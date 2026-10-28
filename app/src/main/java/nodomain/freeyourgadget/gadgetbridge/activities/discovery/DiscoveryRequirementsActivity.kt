package nodomain.freeyourgadget.gadgetbridge.activities.discovery

import android.bluetooth.BluetoothAdapter
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.discovery.DiscoveryRequirements.Requirement
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityDiscoveryRequirementsBinding
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import org.slf4j.LoggerFactory

private const val STATE_PERMISSIONS_PERMANENTLY_DENIED = "permissions_permanently_denied"

/**
 * Shows the first discovery requirement that is not met, and opens discovery when all are met.
 */
class DiscoveryRequirementsActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityDiscoveryRequirementsBinding
    private var permissionsPermanentlyDenied = false

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        LOG.debug("Permission request result: {}", result)
        permissionsPermanentlyDenied = DiscoveryRequirements.missingPermissions(this).any {
            !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
        }
        refresh()
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        LOG.debug("Bluetooth enable request result: {}", result.resultCode)
        refresh()
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            LOG.debug("Received {}", intent.action)
            refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDiscoveryRequirementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        permissionsPermanentlyDenied =
            savedInstanceState?.getBoolean(STATE_PERMISSIONS_PERMANENTLY_DENIED) ?: false

        val filter = IntentFilter().apply {
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
            addAction(LocationManager.PROVIDERS_CHANGED_ACTION)
        }
        ContextCompat.registerReceiver(this, stateReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_PERMISSIONS_PERMANENTLY_DENIED, permissionsPermanentlyDenied)
    }

    override fun onDestroy() {
        AndroidUtils.safeUnregisterBroadcastReceiver(this, stateReceiver)
        super.onDestroy()
    }

    private fun refresh() {
        if (isFinishing) {
            return
        }

        when (DiscoveryRequirements.firstUnmet(this)) {
            null -> {
                LOG.info("All discovery requirements are met, opening discovery")
                startActivity(Intent(this, DiscoveryActivityV2::class.java))
                finish()
            }

            Requirement.BLUETOOTH_ADAPTER_EXISTS -> show(
                icon = R.drawable.ic_bluetooth_disabled,
                title = R.string.bluetooth_is_not_supported_,
                text = null,
                primary = R.string.dialog_close to { finish() },
            )

            Requirement.PERMISSIONS_GRANTED -> show(
                icon = R.drawable.ic_shield,
                title = R.string.first_start_permissions_title,
                text = R.string.discovery_requirements_permissions_text,
                primary = if (permissionsPermanentlyDenied) {
                    R.string.contact_picker_open_settings to {
                        openSettings(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:$packageName".toUri())
                        )
                    }
                } else {
                    R.string.first_start_permissions_request_button to {
                        permissionsLauncher.launch(DiscoveryRequirements.permissions.toTypedArray())
                    }
                },
            )

            Requirement.LOCATION_ENABLED -> show(
                icon = R.drawable.ic_gps_location,
                title = R.string.require_location_provider,
                text = R.string.error_location_enabled_mandatory,
                primary = R.string.contact_picker_open_settings to {
                    openSettings(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                },
            )

            Requirement.BLUETOOTH_ENABLED -> show(
                icon = R.drawable.ic_bluetooth_disabled,
                title = R.string.bluetooth_is_disabled_,
                text = R.string.discovery_enable_bluetooth,
                primary = R.string.discovery_requirements_enable_bluetooth to { requestEnableBluetooth() },
                secondary = R.string.discovery_requirements_bluetooth_settings to {
                    openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                },
            )
        }
    }

    private fun show(
        @DrawableRes icon: Int,
        @StringRes title: Int,
        @StringRes text: Int?,
        primary: Pair<Int, () -> Unit>,
        secondary: Pair<Int, () -> Unit>? = null,
    ) {
        binding.discoveryRequirementsIcon.setImageResource(icon)
        binding.discoveryRequirementsTitle.setText(title)
        binding.discoveryRequirementsText.isVisible = text != null
        text?.let { binding.discoveryRequirementsText.setText(it) }
        binding.discoveryRequirementsPrimary.setText(primary.first)
        binding.discoveryRequirementsPrimary.setOnClickListener { primary.second() }
        binding.discoveryRequirementsSecondary.isVisible = secondary != null
        secondary?.let { (label, action) ->
            binding.discoveryRequirementsSecondary.setText(label)
            binding.discoveryRequirementsSecondary.setOnClickListener { action() }
        }
    }

    private fun requestEnableBluetooth() {
        try {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        } catch (e: ActivityNotFoundException) {
            LOG.error("Failed to request Bluetooth enable", e)
            openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        } catch (e: SecurityException) {
            LOG.error("Failed to request Bluetooth enable", e)
            openSettings(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    private fun openSettings(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            LOG.error("Failed to open {}", intent.action, e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DiscoveryRequirementsActivity::class.java)
    }
}
