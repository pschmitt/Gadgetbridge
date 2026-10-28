package nodomain.freeyourgadget.gadgetbridge.activities.vendorimport

import android.content.Intent
import android.os.Bundle
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityVendorDataImportDeviceBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemVendorImportTargetDeviceBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.DeviceIconUtils

class VendorDataImportDeviceActivity : AbstractGBActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityVendorDataImportDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val container = binding.vendorImportTargetDevices
        for (device in getSupportedDevices().sortedBy { it.aliasOrName }) {
            val row = ItemVendorImportTargetDeviceBinding.inflate(layoutInflater, container, false)
            row.vendorImportTargetIcon.setImageDrawable(
                DeviceIconUtils.tile(this, device.deviceCoordinator.defaultIconResource, device.isInitialized)
            )
            row.vendorImportTargetName.text = device.aliasOrName
            row.vendorImportTargetAddress.text = device.address
            row.root.setOnClickListener {
                startActivity(
                    Intent(this, VendorDataImportActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
                )
            }
            container.addView(row.root)
        }
    }

    companion object {
        @JvmStatic
        fun getSupportedDevices(): List<GBDevice> =
            GBApplication.app().deviceManager.devices
                .filter { it.deviceCoordinator.getVendorDataImporter(it) != null }
    }
}
