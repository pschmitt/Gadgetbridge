package nodomain.freeyourgadget.gadgetbridge.activities.install

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.SimpleItemAnimator
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.wifi.WifiSessionScreen
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityInstallSessionBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.install.InstallManager
import nodomain.freeyourgadget.gadgetbridge.install.InstallOptionsFragment
import nodomain.freeyourgadget.gadgetbridge.install.InstallSession
import nodomain.freeyourgadget.gadgetbridge.install.InstallState
import nodomain.freeyourgadget.gadgetbridge.install.ItemStatus
import nodomain.freeyourgadget.gadgetbridge.install.currentPosition
import nodomain.freeyourgadget.gadgetbridge.install.overallPercent
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableArrayListCompat
import org.slf4j.LoggerFactory

/**
 * Displays one Install Session. If files are not provided in the intent, it displays the current session
 * for the device.
 */
class InstallSessionActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityInstallSessionBinding
    private val itemAdapter = InstallItemAdapter()
    private var lastState: InstallState? = null
    private var optionsCreated = false

    private val viewModel: InstallSessionViewModel by viewModels {
        viewModelFactory {
            initializer {
                InstallSessionViewModel(
                    intent.getDevice()!!,
                    intent.getParcelableArrayListCompat<Uri>(EXTRA_FILES).orEmpty()
                )
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInstallSessionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val device = intent.getDevice()
        if (device == null) {
            LOG.error("No device provided")
            finish()
            return
        }
        WifiSessionScreen(this, device)

        val session = viewModel.session
        if (session == null) {
            LOG.warn("No install session for {}", device)
            finish()
            return
        }

        binding.installItems.layoutManager = LinearLayoutManager(this)
        binding.installItems.adapter = itemAdapter
        // Prevent the rows from blinking every progress update
        (binding.installItems.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        binding.installClose.setOnClickListener { finish() }
        binding.installButton.setOnClickListener { onInstallClicked(session) }
        binding.installConfirmation.setOnCheckedChangeListener { _, _ -> render(session, session.state.value) }
        binding.installShowRunning.setOnClickListener {
            startActivity(Intent(this, InstallSessionActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device))
            finish()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                session.state.collect { render(session, it) }
            }
        }
    }

    private fun onInstallClicked(session: InstallSession) {
        when (session.state.value) {
            is InstallState.Ready -> {
                val fragment = supportFragmentManager.findFragmentById(R.id.installOptions) as? InstallOptionsFragment
                val options = if (fragment != null) fragment.collectOptions() ?: return else null
                InstallManager.install(session, options)
            }

            is InstallState.Finished -> session.stateMachine.restart()
            else -> LOG.warn("Install clicked in unexpected state {}", session.state.value)
        }
    }

    private fun render(session: InstallSession, state: InstallState) {
        val enteredReady = state is InstallState.Ready && lastState !is InstallState.Ready
        lastState = state

        itemAdapter.submitList(
            when (state) {
                InstallState.Preparing -> emptyList()
                is InstallState.WaitingForDevice -> state.parsedFiles.items.map { InstallItemAdapter.Row(it, null) }
                is InstallState.Blocked -> state.items.map { InstallItemAdapter.Row(it, null) }
                is InstallState.Ready -> state.parsedFiles.items.map { InstallItemAdapter.Row(it, null) }
                is InstallState.Installing -> state.items.map { InstallItemAdapter.Row(it.item, it.status) }
                is InstallState.Finished -> state.items.map { InstallItemAdapter.Row(it.item, it.status) }
            }
        )

        binding.installInfo.setTextOrHide(
            when (state) {
                InstallState.Preparing -> getString(R.string.installer_activity_wait_while_determining_status)
                is InstallState.WaitingForDevice -> state.reason
                is InstallState.Blocked -> state.reason
                is InstallState.Ready -> state.parsedFiles.info
                is InstallState.Installing -> null
                is InstallState.Finished -> getString(
                    if (state.items.any { it.status is ItemStatus.Failed }) R.string.installation_failed_ else R.string.installation_successful
                )
            }
        )

        val ready = state as? InstallState.Ready
        binding.installWarnings.setTextOrHide(ready?.warnings?.joinToString("\n\n")?.ifEmpty { null })

        if (ready != null && !optionsCreated) {
            optionsCreated = true
            if (supportFragmentManager.findFragmentById(R.id.installOptions) == null) {
                session.handler?.createOptionsFragment()?.let {
                    supportFragmentManager.beginTransaction().replace(R.id.installOptions, it).commit()
                }
            }
        }
        binding.installOptions.visibility = if (ready != null) View.VISIBLE else View.GONE

        val confirmation = ready?.parsedFiles?.confirmation
        if (enteredReady) {
            binding.installConfirmation.isChecked = false
        }
        binding.installConfirmation.text = confirmation
        binding.installConfirmation.visibility = if (confirmation != null) View.VISIBLE else View.GONE

        val busy = state is InstallState.Preparing || state is InstallState.WaitingForDevice
        binding.installBusy.visibility = if (busy) View.VISIBLE else View.GONE

        val installing = (state as? InstallState.Installing)?.takeIf { it.items.size > 1 }
        binding.installProgress.visibility = if (installing != null) View.VISIBLE else View.GONE
        binding.installProgressText.visibility = if (installing != null) View.VISIBLE else View.GONE
        if (installing != null) {
            val percent = installing.items.overallPercent()
            binding.installProgress.setProgressCompat(percent, true)
            binding.installProgressText.text = getString(
                R.string.install_progress,
                installing.items.currentPosition(),
                installing.items.size,
                percent
            )
        }

        binding.installClose.setText(if (state is InstallState.Installing) R.string.dialog_hide else R.string.dialog_close)

        val retry = state is InstallState.Finished && state.items.any { it.status is ItemStatus.Failed }
        binding.installButton.setText(if (retry) R.string.retry else R.string.appinstaller_install)
        binding.installButton.visibility = if (ready != null || retry) View.VISIBLE else View.GONE
        binding.installButton.isEnabled = ready == null || confirmation == null || binding.installConfirmation.isChecked

        val running = InstallManager.forDevice(session.device)
        val showRunning = state is InstallState.Blocked && running != null && running !== session &&
            running.state.value is InstallState.Installing
        binding.installShowRunning.visibility = if (showRunning) View.VISIBLE else View.GONE
    }

    private fun TextView.setTextOrHide(value: String?) {
        text = value
        visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(InstallSessionActivity::class.java)

        private const val EXTRA_FILES = "files"

        fun createIntent(context: Context, device: GBDevice, files: List<Uri>): Intent =
            Intent(context, InstallSessionActivity::class.java).apply {
                putExtra(GBDevice.EXTRA_DEVICE, device)
                putParcelableArrayListExtra(EXTRA_FILES, ArrayList(files))
                clipData = ClipData.newRawUri(null, files[0]).also { clip ->
                    files.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    }
}

class InstallSessionViewModel(device: GBDevice, files: List<Uri>) : ViewModel() {
    val session: InstallSession? = if (files.isEmpty())
        InstallManager.forDevice(device)
    else
        InstallManager.start(device, files)

    override fun onCleared() {
        session?.let { InstallManager.close(it) }
    }
}
