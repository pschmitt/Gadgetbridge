package nodomain.freeyourgadget.gadgetbridge.activities.debug

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.companion.AssociationInfo
import android.companion.CompanionDeviceManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.CompanionDeviceUtil
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.O)
class CompanionDebugFragment : AbstractDebugFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.debug_preferences_companion, rootKey)

        reloadDevices()
    }

    private fun reloadDevices() {
        val companionDevicesHeader: PreferenceCategory = findPreference(PREF_HEADER_COMPANION_DEVICES)!!
        val otherDevicesHeader: PreferenceCategory = findPreference(PREF_HEADER_OTHER_DEVICES)!!
        removeDynamicPrefs(companionDevicesHeader)
        removeDynamicPrefs(otherDevicesHeader)

        val associations = CompanionDeviceUtil.getCompanionAssociations(GBApplication.getContext()).toSet()

        val companionDevices = associations
            .map {
                val deviceByAddress = GBApplication.app().deviceManager.getDeviceByAddress(it)
                Device(
                    it,
                    deviceByAddress?.aliasOrName ?: getString(R.string.unknown),
                    deviceByAddress?.deviceCoordinator?.defaultIconResource ?: R.drawable.ic_device_unknown,
                    deviceByAddress?.isConnected == true
                )
            }
            .sortedBy { it.name }

        val otherDevices = GBApplication.app().deviceManager.devices
            .filter { !associations.contains(it.address.uppercase(Locale.ROOT)) }
            .map {
                Device(
                    it.address,
                    it.aliasOrName,
                    it.deviceCoordinator.defaultIconResource,
                    it.isConnected
                )
            }

        if (!companionDevices.isEmpty()) {
            for (device in companionDevices) {
                addDynamicDevicePref(companionDevicesHeader, device.name, device.address, device.icon, device.connected) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setCancelable(true)
                        .setIcon(device.icon)
                        .setTitle("Unpair companion device")
                        .setMessage("Unpair ${device.name} (${device.address}) from companion device?")
                        .setNeutralButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.ok) { _, _ -> unpairCompanion(device) }
                        .show()
                }
            }
        } else {
            addDynamicPref(companionDevicesHeader, "", "No companion devices")
        }

        if (!otherDevices.isEmpty()) {
            otherDevicesHeader.isVisible = true
            for (device in otherDevices) {
                addDynamicDevicePref(otherDevicesHeader, device.name, device.address, device.icon, device.connected) {
                    MaterialAlertDialogBuilder(requireContext())
                        .setCancelable(true)
                        .setIcon(device.icon)
                        .setTitle("Pair as companion")
                        .setMessage("Pair ${device.name} (${device.address}) as companion device?")
                        .setNeutralButton(R.string.cancel) { _, _ -> }
                        .setPositiveButton(R.string.ok) { _, _ -> pairAsCompanion(device) }
                        .show()
                }
            }
        } else {
            otherDevicesHeader.isVisible = false
        }
    }

    private data class Device(val address: String, val name: String, val icon: Int, val connected: Boolean)

    // FIXME replace deprecated code
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == SELECT_DEVICE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            val deviceToPair = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val macAddress = data?.getParcelableCompat<AssociationInfo>(CompanionDeviceManager.EXTRA_ASSOCIATION)
                    ?.deviceMacAddress?.toString()
                val bluetoothAdapter =
                    (requireContext().getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
                macAddress?.let { bluetoothAdapter?.getRemoteDevice(it) }
            } else {
                @Suppress("DEPRECATION")
                data?.getParcelableCompat<BluetoothDevice>(CompanionDeviceManager.EXTRA_DEVICE)
            }

            if (deviceToPair != null) {
                if (deviceToPair.bondState != BluetoothDevice.BOND_BONDED) {
                    GB.toast("Creating bond...", Toast.LENGTH_SHORT, GB.INFO)
                    deviceToPair.createBond()
                } else {
                    GB.toast("Bonding complete", Toast.LENGTH_LONG, GB.INFO)
                    reloadDevices()
                }
            } else {
                GB.toast("No device to pair", Toast.LENGTH_LONG, GB.ERROR)
            }
        }
    }

    private fun unpairCompanion(device: Device) {
        CompanionDeviceUtil.Disassociate(requireContext(), device.address)
        reloadDevices()
    }

    private fun pairAsCompanion(device: Device) {
        if (CompanionDeviceUtil.isCompanionAssociated(requireContext(), device.address)) {
            GB.toast(device.name + " already paired as companion", Toast.LENGTH_LONG, GB.INFO)
            return
        }

        CompanionDeviceUtil.associateCompanion(requireActivity(), device.address, false, SELECT_DEVICE_REQUEST_CODE, {
            GB.toast("Companion pairing success", Toast.LENGTH_SHORT, GB.INFO)
            reloadDevices()
        }, null)
    }

    companion object {
        const val SELECT_DEVICE_REQUEST_CODE: Int = 1424

        private const val PREF_HEADER_COMPANION_DEVICES = "pref_header_companion_devices"
        private const val PREF_HEADER_OTHER_DEVICES = "pref_header_other_devices"
    }
}
