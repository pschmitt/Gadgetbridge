package nodomain.freeyourgadget.gadgetbridge.activities.vendorimport

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import javax.inject.Inject

@HiltViewModel
class VendorDataImportDeviceViewModel @Inject constructor(
    deviceManager: DeviceManager,
) : ViewModel() {
    data class DeviceItem(
        val device: GBDevice,
        val name: String,
        val address: String,
        @DrawableRes val icon: Int,
        val initialized: Boolean,
    )

    val devices: List<DeviceItem> = deviceManager.getSupportedDevices()
        .sortedBy { it.aliasOrName }
        .map { device ->
            DeviceItem(
                device = device,
                name = device.aliasOrName,
                address = device.address,
                icon = device.deviceCoordinator.defaultIconResource,
                initialized = device.isInitialized,
            )
        }
}
