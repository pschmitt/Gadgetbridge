package nodomain.freeyourgadget.gadgetbridge.service.devices.teufel

import android.os.Handler
import android.os.Looper
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState
import nodomain.freeyourgadget.gadgetbridge.service.AbstractHeadphoneBTBRDeviceSupport
import nodomain.freeyourgadget.gadgetbridge.service.btbr.TransactionBuilder
import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedList
import java.util.Queue
import java.util.UUID
import kotlin.experimental.or

class TeufelMyndSupport : AbstractHeadphoneBTBRDeviceSupport(LOG, MAX_MTU) {
    private val packetBuffer: ByteBuffer = ByteBuffer.allocate(MAX_MTU).order(ByteOrder.BIG_ENDIAN)

    private val messageQueue: Queue<TeufelMyndMessage> = LinkedList()
    private var pendingMessage: TeufelMyndMessage? = null
    private var timeoutRetries = 0
    private val timeoutHandler = Handler(Looper.getMainLooper())
    private val versionEvent = GBDeviceEventVersionInfo()
    private val batteryEvent = GBDeviceEventBatteryInfo()

    init {
        addSupportedService(UUID_SERVICE_SERIAL_PORT)
    }

    override fun useAutoConnect(): Boolean {
        return true
    }

    override fun initializeDevice(builder: TransactionBuilder): TransactionBuilder {
        packetBuffer.clear()
        synchronized(messageQueue) {
            timeoutHandler.removeCallbacksAndMessages(null)
            messageQueue.clear()
            pendingMessage = null
            timeoutRetries = 0
        }
        batteryEvent.state = BatteryState.UNKNOWN
        batteryEvent.level = GBDevice.BATTERY_UNKNOWN.toInt()

        builder.setDeviceState(GBDevice.State.INITIALIZING)

        // Send the fw version request directly, but queue everything else, otherwise the device does not respond to all
        val fwGetPacket = TeufelMyndMessage(TeufelMyndCommand.BT_FIRMWARE_GET)
        builder.write(*fwGetPacket.encode())
        synchronized(messageQueue) {
            pendingMessage = fwGetPacket
            timeoutHandler.postDelayed({ onCommandTimeout() }, COMMAND_TIMEOUT_MS)
        }

        queueCommand(TeufelMyndCommand.MCU_FIRMWARE_GET, byteArrayOf(MCU_TARGET))
        queueCommand(TeufelMyndCommand.BATTERY_LEVEL_GET)
        queueCommand(TeufelMyndCommand.POWER_ADAPTER_STATUS_GET)

        for (notification in TeufelMyndNotification.entries) {
            if (notification != TeufelMyndNotification.USB_CONNECTION) {
                queueCommand(TeufelMyndCommand.NOTIFICATION_REGISTER, byteArrayOf(notification.id))
            }
        }

        queueCommand(TeufelMyndCommand.VOLUME_GET)
        queueCommand(TeufelMyndCommand.MUTE_GET)
        queueCommand(TeufelMyndCommand.EQ_GET, byteArrayOf(TeufelMyndCommand.EQ_BASS))
        queueCommand(TeufelMyndCommand.EQ_GET, byteArrayOf(TeufelMyndCommand.EQ_TREBLE))
        queueCommand(TeufelMyndCommand.ECO_MODE_GET)
        queueCommand(TeufelMyndCommand.SOUND_ICONS_GET)
        queueCommand(TeufelMyndCommand.BATTERY_FRIENDLY_CHARGING_GET)
        queueCommand(TeufelMyndCommand.MULTIPOINT_GET)
        queueCommand(TeufelMyndCommand.LED_BRIGHTNESS_GET)
        queueCommand(TeufelMyndCommand.AUTO_OFF_TIMER_GET)
        queueCommand(TeufelMyndCommand.SOURCE_GET, byteArrayOf(SOURCE_CHANNEL))
        queueCommand(TeufelMyndCommand.CONNECTED_SOURCES_GET)
        queueCommand(TeufelMyndCommand.PARTY_LINK_GET)

        return builder
    }

    override fun dispose() {
        synchronized(ConnectionMonitor) {
            timeoutHandler.removeCallbacksAndMessages(null)
            super.dispose()
        }
    }

    override fun onSocketRead(data: ByteArray) {
        packetBuffer.put(data)
        packetBuffer.flip()

        while (packetBuffer.remaining() >= TeufelMyndMessage.HEADER_SIZE) {
            val start = packetBuffer.position()
            val message = TeufelMyndMessage.tryParse(packetBuffer)
            if (message == null) {
                if (packetBuffer.position() == start) {
                    // Incomplete packet, wait for more data
                    break
                }
                continue
            }

            if (handleMessage(message)) {
                sendNextCommand()
            }
        }

        packetBuffer.compact()
    }

    /**
     * Returns true if the message is the response to the pending command.
     */
    private fun handleMessage(message: TeufelMyndMessage): Boolean {
        val command = message.originalCommand

        if (!message.isAck) {
            if (command == TeufelMyndCommand.NOTIFICATION_EVENT) {
                try {
                    handleNotification(message.payload)
                } catch (e: Exception) {
                    LOG.error("Failed to handle notification", e)
                }
                sendAck(TeufelMyndCommand.NOTIFICATION_EVENT.commandId)
            } else {
                LOG.warn("Unexpected non-ACK command 0x{}", String.format("%04x", message.commandId))
            }
            return false
        }

        if (command == null) {
            LOG.warn("ACK for unknown command 0x{}", String.format("%04x", message.commandId))
            return false
        }

        val pending = synchronized(messageQueue) { pendingMessage }
        val isPendingResponse = pending != null && pending.originalCommand == command

        if (message.payload.isEmpty()) {
            LOG.warn("Empty ACK for {}", command)
        } else if (message.payload[0] != TeufelMyndMessage.STATUS_SUCCESS) {
            LOG.warn("Command {} failed, status=0x{}", command, String.format("%02x", message.payload[0]))
        } else {
            val request = if (isPendingResponse) pending.payload else byteArrayOf()
            try {
                handleAckResponse(command, request, message.payload.copyOfRange(1, message.payload.size))
            } catch (e: Exception) {
                LOG.error("Failed to handle ACK for {}", command, e)
            }
        }

        return isPendingResponse
    }

    private fun handleAckResponse(command: TeufelMyndCommand, request: ByteArray, payload: ByteArray) {
        LOG.debug("Handling ACK for {} payload={}", command, payload.toHexString())

        when (command) {
            TeufelMyndCommand.BT_FIRMWARE_GET -> {
                versionEvent.fwVersion = String(payload, Charsets.US_ASCII).trimEnd('\u0000')
                LOG.info("Bluetooth firmware: {}", versionEvent.fwVersion)
                evaluateGBDeviceEvent(versionEvent)
            }

            TeufelMyndCommand.MCU_FIRMWARE_GET -> {
                if (payload.size < 3) {
                    LOG.warn("Unexpected MCU firmware payload size {}", payload.size)
                    return
                }
                versionEvent.fwVersion2 = payload.take(3).joinToString(".") {
                    (it.toInt() and 0xff).toString()
                }
                LOG.info("MCU firmware: {}", versionEvent.fwVersion2)
                evaluateGBDeviceEvent(versionEvent)
            }

            TeufelMyndCommand.BATTERY_LEVEL_GET -> {
                // The spec defines 2 bytes, but the official app accepts a single byte on either byte as well
                val level = if (payload.size >= 2 && payload[1].toInt() != 0) {
                    payload[1].toInt() and 0xff
                } else {
                    payload[0].toInt() and 0xff
                }
                LOG.info("Battery level: {}%", level)
                batteryEvent.level = level
                evaluateGBDeviceEvent(batteryEvent)
            }

            TeufelMyndCommand.POWER_ADAPTER_STATUS_GET -> {
                handlePowerAdapter(payload[0])
            }

            TeufelMyndCommand.VOLUME_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_VOLUME,
                    payload[0].toInt() and 0xff
                )
            }

            TeufelMyndCommand.MUTE_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MUTE,
                    payload[0].toInt() != 0
                )
            }

            TeufelMyndCommand.EQ_GET -> {
                // The response does not include the band - use the one from the request
                val key = when (request.firstOrNull()) {
                    TeufelMyndCommand.EQ_BASS -> DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_BASS
                    TeufelMyndCommand.EQ_TREBLE -> DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_TREBLE
                    else -> {
                        LOG.warn("Unknown EQ band in request {}", request.toHexString())
                        return
                    }
                }
                updatePreference(key, payload[0].toInt() / EQ_GAIN_MULTIPLIER)
            }

            TeufelMyndCommand.ECO_MODE_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_ECO_MODE,
                    payload[0].toInt() != 0
                )
            }

            TeufelMyndCommand.SOUND_ICONS_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_SOUND_ICONS,
                    payload[0].toInt() != 0
                )
            }

            TeufelMyndCommand.BATTERY_FRIENDLY_CHARGING_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_BATTERY_FRIENDLY_CHARGING,
                    payload[0].toInt() != 0
                )
            }

            TeufelMyndCommand.MULTIPOINT_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MULTIPOINT,
                    payload[0].toInt() != 0
                )
            }

            TeufelMyndCommand.LED_BRIGHTNESS_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_LED_BRIGHTNESS,
                    payload[0].toInt() and 0xff
                )
            }

            TeufelMyndCommand.AUTO_OFF_TIMER_GET -> {
                val seconds = ((payload[0].toInt() and 0xff) shl 8) or (payload[1].toInt() and 0xff)
                val autoOff = TeufelMyndAutoOff.fromSeconds(seconds)
                if (autoOff == null) {
                    LOG.warn("Unknown auto-off value {}s", seconds)
                    return
                }
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_AUTO_OFF,
                    autoOff.name.lowercase()
                )
            }

            TeufelMyndCommand.SOURCE_GET -> {
                val source = TeufelMyndSource.fromCode(payload[0])
                if (source == null) {
                    LOG.warn("Unknown source 0x{}", String.format("%02x", payload[0]))
                    return
                }
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE,
                    source.name.lowercase()
                )
            }

            TeufelMyndCommand.CONNECTED_SOURCES_GET -> {
                LOG.info("Connected sources: 0x{}", payload.toHexString())
            }

            TeufelMyndCommand.PARTY_LINK_GET -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_PARTY_LINK,
                    payload[0].toInt() != 0
                )
            }

            else -> {}
        }
    }

    private fun handleNotification(payload: ByteArray) {
        if (payload.isEmpty()) {
            LOG.warn("Empty notification")
            return
        }

        val notificationId = payload[0]
        val data = payload.copyOfRange(1, payload.size)
        val notification = TeufelMyndNotification.fromId(notificationId)

        LOG.debug(
            "Notification: {} data={}",
            notification ?: String.format("0x%02x", notificationId),
            data.toHexString()
        )

        if (notification == null) {
            LOG.warn("Unknown notification ID 0x{}", String.format("%02x", notificationId))
            return
        }

        if (data.isEmpty() && notification != TeufelMyndNotification.BATTERY_LOW) {
            LOG.warn("Missing data for notification {}", notification)
            return
        }

        when (notification) {
            TeufelMyndNotification.BATTERY_LEVEL -> {
                batteryEvent.level = data[0].toInt() and 0xff
                LOG.info("Battery level notification: {}%", batteryEvent.level)
                evaluateGBDeviceEvent(batteryEvent)
            }

            TeufelMyndNotification.BATTERY_LOW -> {
                LOG.info("Battery low notification")
                batteryEvent.state = BatteryState.BATTERY_LOW
                evaluateGBDeviceEvent(batteryEvent)
            }

            TeufelMyndNotification.POWER_ADAPTER -> {
                handlePowerAdapter(data[0])
            }

            TeufelMyndNotification.VOLUME_CHANGE -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_VOLUME,
                    data[0].toInt() and 0xff
                )
            }

            TeufelMyndNotification.MUTE_STATUS -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MUTE,
                    data[0].toInt() != 0
                )
            }

            TeufelMyndNotification.ECO_MODE -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_ECO_MODE,
                    data[0].toInt() != 0
                )
            }

            TeufelMyndNotification.SOUND_ICONS -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_SOUND_ICONS,
                    data[0].toInt() != 0
                )
            }

            TeufelMyndNotification.PARTY_LINK -> {
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_PARTY_LINK,
                    data[0].toInt() != 0
                )
            }

            TeufelMyndNotification.BATTERY_FRIENDLY_CHARGING ->
                updatePreference(
                    DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_BATTERY_FRIENDLY_CHARGING,
                    data[0].toInt() != 0
                )

            TeufelMyndNotification.CONNECTED_SOURCES -> {
                LOG.info("Connected sources: 0x{}", data.toHexString())
            }

            TeufelMyndNotification.USB_CONNECTION -> {
                LOG.info("USB connection: 0x{}", data.toHexString())
            }
        }
    }

    private fun handlePowerAdapter(value: Byte) {
        val connected = value.toInt() != 0
        LOG.info("Power adapter connected: {}", connected)
        batteryEvent.state = if (connected) BatteryState.BATTERY_CHARGING else BatteryState.BATTERY_NORMAL
        evaluateGBDeviceEvent(batteryEvent)
    }

    private fun updatePreference(key: String, value: Any) {
        LOG.debug("Updating preference {} = {}", key, value)
        evaluateGBDeviceEvent(GBDeviceEventUpdatePreferences(key, value))
    }

    override fun onSendConfiguration(config: String) {
        val prefs = devicePrefs

        when (config) {
            DeviceSettingsPreferenceConst.PREF_VOLUME -> {
                val volume = prefs.getInt(config, 50).coerceIn(0, 100)
                LOG.debug("Setting volume to {}", volume)
                queueCommand(TeufelMyndCommand.VOLUME_SET, byteArrayOf(volume.toByte()))
            }

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MUTE -> {
                val muted = prefs.getBoolean(config, false)
                LOG.debug("Setting mute to {}", muted)
                queueCommand(TeufelMyndCommand.MUTE_SET, byteArrayOf(if (muted) 0x01 else 0x00))
            }

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_BASS -> setEq(
                TeufelMyndCommand.EQ_BASS,
                config
            )

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_EQ_TREBLE -> setEq(
                TeufelMyndCommand.EQ_TREBLE,
                config
            )

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_ECO_MODE -> setToggle(
                config,
                TeufelMyndCommand.ECO_MODE_ENABLE,
                TeufelMyndCommand.ECO_MODE_DISABLE
            )

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_SOUND_ICONS -> setToggle(
                config,
                TeufelMyndCommand.SOUND_ICONS_ENABLE,
                TeufelMyndCommand.SOUND_ICONS_DISABLE
            )

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_BATTERY_FRIENDLY_CHARGING -> setToggle(
                config,
                TeufelMyndCommand.BATTERY_FRIENDLY_CHARGING_ENABLE,
                TeufelMyndCommand.BATTERY_FRIENDLY_CHARGING_DISABLE
            )

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_MULTIPOINT -> setToggle(
                config,
                TeufelMyndCommand.MULTIPOINT_ENABLE,
                TeufelMyndCommand.MULTIPOINT_DISABLE
            )

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_PARTY_LINK -> setToggle(
                config,
                TeufelMyndCommand.PARTY_LINK_START,
                TeufelMyndCommand.PARTY_LINK_STOP
            )

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_LED_BRIGHTNESS -> {
                val brightness = prefs.getInt(config, 100).coerceIn(0, 100)
                LOG.debug("Setting brightness to {}", brightness)
                queueCommand(TeufelMyndCommand.LED_BRIGHTNESS_SET, byteArrayOf(brightness.toByte()))
            }

            DeviceSettingsPreferenceConst.PREF_TEUFEL_MYND_AUTO_OFF -> {
                val value = prefs.getString(config, TeufelMyndAutoOff.MINUTES_15.name.lowercase())
                val autoOff = TeufelMyndAutoOff.fromPreference(value) ?: run {
                    LOG.warn("Unknown auto-off {}", value)
                    return
                }
                LOG.debug("Setting auto-off to {}", autoOff)
                queueCommand(
                    TeufelMyndCommand.AUTO_OFF_TIMER_SET,
                    byteArrayOf((autoOff.seconds shr 8).toByte(), autoOff.seconds.toByte())
                )
            }

            DeviceSettingsPreferenceConst.PREF_MEDIA_SOURCE -> {
                val value = prefs.getString(config, TeufelMyndSource.BLUETOOTH.name.lowercase())
                val source = TeufelMyndSource.fromPreference(value) ?: run {
                    LOG.warn("Unknown source {}", value)
                    return
                }
                LOG.debug("Setting media source to {}", value)
                queueCommand(TeufelMyndCommand.SOURCE_SET, byteArrayOf(SOURCE_CHANNEL, source.code))
            }

            else -> super.onSendConfiguration(config)
        }
    }

    private fun setEq(band: Byte, key: String) {
        val gain = devicePrefs.getInt(key, 0).coerceIn(EQ_GAIN_MIN, EQ_GAIN_MAX)
        queueCommand(TeufelMyndCommand.EQ_SET, byteArrayOf(band, (gain * EQ_GAIN_MULTIPLIER).toByte()))
    }

    private fun setToggle(key: String, enable: TeufelMyndCommand, disable: TeufelMyndCommand) {
        val value = devicePrefs.getBoolean(key, false)
        LOG.debug("Setting {} to {}", key, value)
        queueCommand(if (value) enable else disable)
    }

    private fun queueCommand(command: TeufelMyndCommand, payload: ByteArray = byteArrayOf()) {
        synchronized(messageQueue) {
            messageQueue.add(TeufelMyndMessage(command, payload))

            if (pendingMessage == null) {
                sendNextCommand()
            }
        }
    }

    private fun onCommandTimeout() {
        synchronized(messageQueue) {
            val pending = pendingMessage
            if (pending != null && timeoutRetries++ < MAX_RETRIES) {
                LOG.warn("Timed out waiting for response to {}, retry {}", pending.originalCommand, timeoutRetries)
                sendMessage(pending)
                return
            }
            LOG.warn("Timed out waiting for response to {}, giving up", pending?.originalCommand)
            sendNextCommand()
        }
    }

    private fun sendNextCommand() {
        synchronized(messageQueue) {
            timeoutHandler.removeCallbacksAndMessages(null)
            timeoutRetries = 0

            pendingMessage = messageQueue.poll()
            pendingMessage?.let { msg ->
                sendMessage(msg)
                return
            }
        }
        LOG.debug("No more commands in queue")

        if (gbDevice.state == GBDevice.State.INITIALIZING) {
            gbDevice.setUpdateState(GBDevice.State.INITIALIZED, context)
        }
    }

    private fun sendMessage(message: TeufelMyndMessage) {
        LOG.debug("Sending command: {} payload={}", message.originalCommand, message.payload.toHexString())

        val builder = createTransactionBuilder(message.originalCommand?.name ?: message.commandId.toString())
        builder.write(*message.encode())
        builder.queue()

        timeoutHandler.postDelayed({ onCommandTimeout() }, COMMAND_TIMEOUT_MS)
    }

    private fun sendAck(commandId: Short) {
        val builder = createTransactionBuilder("ack $commandId")
        builder.write(
            *TeufelMyndMessage(
                commandId or TeufelMyndCommand.ACK_MASK.toShort(),
                byteArrayOf(TeufelMyndMessage.STATUS_SUCCESS)
            ).encode()
        )
        builder.queue()
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(TeufelMyndSupport::class.java)
        private const val MAX_MTU: Int = 2048
        private const val COMMAND_TIMEOUT_MS = 2000L
        private const val MAX_RETRIES = 3

        private const val MCU_TARGET: Byte = 0x01
        private const val SOURCE_CHANNEL: Byte = 0x00

        const val EQ_GAIN_MIN = -6
        const val EQ_GAIN_MAX = 6
        private const val EQ_GAIN_MULTIPLIER = 10

        private val UUID_SERVICE_SERIAL_PORT: UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
    }
}
