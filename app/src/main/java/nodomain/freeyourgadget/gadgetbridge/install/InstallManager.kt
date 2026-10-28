package nodomain.freeyourgadget.gadgetbridge.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.install.InstallSessionActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import org.slf4j.LoggerFactory
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages [InstallSession]s, at most one for each device.
 */
object InstallManager {
    private val LOG = LoggerFactory.getLogger(InstallManager::class.java)

    private val sessions = ConcurrentHashMap<String, InstallSession>()
    private val nextId = AtomicLong()
    private val scope = MainScope()
    private var receiverRegistered = false

    private val deviceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getDevice() ?: return
            val session = sessions[key(device)] ?: return
            onDeviceChanged(session, device)
        }
    }

    /**
     * Starts a new session for a list of files. When the device already has a session in progress,
     * the returned session is blocked and is not kept.
     */
    fun start(device: GBDevice, files: List<Uri>): InstallSession {
        registerReceiver()
        val context = GBApplication.getContext()

        val existing = sessions[key(device)]
        if (existing != null && existing.state.value is InstallState.Installing) {
            LOG.warn("Session {} is already in progress on {}, refusing a new one", existing.id, device)
            return InstallSession(
                nextId.incrementAndGet(),
                device,
                InstallStateMachine(
                    InstallState.Blocked(
                        context.getString(R.string.install_another_in_progress),
                        emptyList()
                    )
                )
            )
        }
        existing?.let { close(it) }

        val session = InstallSession(nextId.incrementAndGet(), device, InstallStateMachine())
        LOG.info("Starting session {} on {} for {} files", session.id, device, files.size)
        sessions[key(device)] = session
        session.stateJob = scope.launch {
            session.state
                .map { notificationContent(context, it) }
                .distinctUntilChanged()
                .collect { updateNotification(context, session.device, it) }
        }
        scope.launch { prepare(session, files) }

        val current = currentDevice(device)
        if (!current.isConnected && !current.isConnecting) {
            GBApplication.deviceService(current).connect()
        }
        return session
    }

    fun get(sessionId: Long): InstallSession? = sessions.values.firstOrNull { it.id == sessionId }

    fun forDevice(device: GBDevice): InstallSession? = sessions[key(device)]

    fun install(session: InstallSession, options: Any?) {
        session.options = options
        if (!session.stateMachine.start()) {
            return
        }
        LOG.info("Installing session {} on {}", session.id, session.device)
        GBApplication.deviceService(session.device).onInstall(session.id)
    }

    /**
     * Removes the session and its notification. A session in progress is kept.
     */
    fun close(session: InstallSession) {
        if (session.state.value is InstallState.Installing) {
            return
        }
        if (sessions.remove(key(session.device), session)) {
            LOG.info("Closing session {}", session.id)
            session.stateJob?.cancel()
            GB.removeNotification(session.device.address, GB.NOTIFICATION_ID_INSTALL, GBApplication.getContext())
        }
    }

    private suspend fun prepare(session: InstallSession, files: List<Uri>) {
        val context = GBApplication.getContext()
        val parsedFiles = withContext(Dispatchers.IO) {
            try {
                val handler = session.device.deviceCoordinator.findInstallHandlerV2(files, context)
                if (handler == null) {
                    ParsedFiles.Unsupported(context.getString(R.string.fwinstaller_file_not_compatible_to_device))
                } else {
                    session.handler = handler
                    handler.prepare(files)
                }
            } catch (e: Exception) {
                LOG.error("Failed to prepare session {}", session.id, e)
                ParsedFiles.Unsupported(context.getString(R.string.installation_failed_))
            }
        }
        LOG.debug("Session {} parsed files: {}", session.id, parsedFiles)

        when (parsedFiles) {
            is ParsedFiles.Supported -> {
                val handler = session.handler ?: return
                session.stateMachine.supported(parsedFiles, check(handler, parsedFiles, currentDevice(session.device)))
            }

            is ParsedFiles.Unsupported -> session.stateMachine.unsupported(parsedFiles.reason)
        }
    }

    private fun onDeviceChanged(session: InstallSession, device: GBDevice) {
        val handler = session.handler ?: return
        val parsedFiles = session.stateMachine.parsedFiles ?: return
        if (session.state.value is InstallState.Installing && !device.isInitialized) {
            session.stateMachine.deviceLost(GBApplication.getContext().getString(R.string.device_not_connected))
        }
        session.stateMachine.deviceChecked(check(handler, parsedFiles, device))
    }

    private fun check(handler: InstallHandlerV2, parsedFiles: ParsedFiles.Supported, device: GBDevice): DeviceCheck {
        val context = GBApplication.getContext()
        return when {
            !device.isInitialized -> DeviceCheck.NotReady(
                context.getString(R.string.fwappinstaller_connection_state, device.getStateString(context))
            )

            device.isBusy -> DeviceCheck.NotReady(device.busyTask.orEmpty())
            else -> DeviceCheck.Checked(handler.checkCompatibility(parsedFiles, device))
        }
    }

    private fun currentDevice(device: GBDevice): GBDevice =
        GBApplication.app().deviceManager.getDeviceByAddress(device.address) ?: device

    private fun key(device: GBDevice): String = device.address.uppercase(Locale.ROOT)

    @Synchronized
    private fun registerReceiver() {
        if (receiverRegistered) {
            return
        }
        LocalBroadcastManager.getInstance(GBApplication.getContext()).registerReceiver(
            deviceReceiver,
            IntentFilter(GBDevice.ACTION_DEVICE_CHANGED)
        )
        receiverRegistered = true
    }

    private data class NotificationContent(
        val ongoing: Boolean,
        val text: String,
        val position: Int,
        val count: Int,
        val percent: Int,
    )

    private fun notificationContent(context: Context, state: InstallState): NotificationContent? = when (state) {
        is InstallState.Installing -> {
            val position = state.items.currentPosition()
            NotificationContent(
                ongoing = true,
                text = state.items[position - 1].item.displayName,
                position = position,
                count = state.items.size,
                percent = state.items.overallPercent(),
            )
        }

        is InstallState.Finished -> {
            val failed = state.items.any { it.status is ItemStatus.Failed }
            NotificationContent(
                ongoing = false,
                text = context.getString(if (failed) R.string.installation_failed_ else R.string.installation_successful),
                position = state.items.size,
                count = state.items.size,
                percent = 100,
            )
        }

        else -> null
    }

    private fun updateNotification(context: Context, device: GBDevice, content: NotificationContent?) {
        if (content == null) {
            GB.removeNotification(device.address, GB.NOTIFICATION_ID_INSTALL, context)
            return
        }

        val intent = Intent(context, InstallSessionActivity::class.java)
            .putExtra(GBDevice.EXTRA_DEVICE, device)
        val pendingIntent = PendingIntent.getActivity(
            context,
            device.address.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(context, GB.NOTIFICATION_CHANNEL_ID_TRANSFER)
            .setContentTitle(device.aliasOrName)
            .setContentText(content.text)
            .setSubText(context.getString(R.string.blood_pressure_avg_format, content.position, content.count))
            .setContentIntent(pendingIntent)
            .setOngoing(content.ongoing)
            .setOnlyAlertOnce(true)
        if (content.ongoing) {
            builder.setProgress(100, content.percent, false)
            builder.setSmallIcon(android.R.drawable.stat_sys_upload)
        } else {
            builder.setSmallIcon(android.R.drawable.stat_sys_upload_done)
        }
        GB.notify(device.address, GB.NOTIFICATION_ID_INSTALL, builder.build(), context)
    }
}
