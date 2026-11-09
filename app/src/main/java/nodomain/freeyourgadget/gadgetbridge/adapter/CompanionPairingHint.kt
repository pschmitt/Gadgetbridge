package nodomain.freeyourgadget.gadgetbridge.adapter

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.CompanionDeviceUtil
import org.slf4j.LoggerFactory

object CompanionPairingHint {
    private val LOG = LoggerFactory.getLogger(CompanionPairingHint::class.java)

    private const val PREF_DISMISSED = "companion_pairing_hint_dismissed"
    private const val REQUEST_CODE = 1425

    @JvmStatic
    fun shouldShow(context: Context, device: GBDevice): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return false
        }
        if (!device.isInitialized) {
            return false
        }
        if (device.deviceCoordinator.connectionType == DeviceCoordinator.ConnectionType.USB) {
            return false
        }
        if (!BluetoothAdapter.checkBluetoothAddress(device.address)) {
            return false
        }
        if (GBApplication.getDeviceSpecificSharedPrefs(device.address).getBoolean(PREF_DISMISSED, false)) {
            return false
        }
        if (CompanionDeviceUtil.getCompanionDeviceManager(context) == null) {
            return false
        }
        return !CompanionDeviceUtil.isCompanionAssociated(context, device.address)
    }

    @JvmStatic
    fun dismiss(device: GBDevice) {
        LOG.debug("Dismissing companion pairing hint for {}", device.address)
        GBApplication.getDeviceSpecificSharedPrefs(device.address).edit {
            putBoolean(PREF_DISMISSED, true)
        }
    }

    @JvmStatic
    @RequiresApi(Build.VERSION_CODES.O)
    fun pair(activity: Activity, device: GBDevice) {
        // #7279 - The device is connected, so it may not advertise - set useLe=false
        CompanionDeviceUtil.associateCompanion(activity, device.address, false, REQUEST_CODE, null, null)
    }

    @JvmStatic
    fun onChooserClosed(context: Context, address: String) {
        if (CompanionDeviceUtil.isCompanionAssociated(context, address)) {
            LOG.debug("{} is now associated as companion", address)
            CompanionDeviceUtil.StartObserving(context, address)
        }
    }
}
