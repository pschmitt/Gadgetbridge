package nodomain.freeyourgadget.gadgetbridge.activities.vendorimport

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityVendorDataImportDeviceBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemVendorImportTargetDeviceBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.DeviceIconUtils

@AndroidEntryPoint
class VendorDataImportDeviceActivity : AbstractGBActivity() {
    private val viewModel: VendorDataImportDeviceViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityVendorDataImportDeviceBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val container = binding.vendorImportTargetDevices
        for (item in viewModel.devices) {
            val row = ItemVendorImportTargetDeviceBinding.inflate(layoutInflater, container, false)
            row.vendorImportTargetIcon.setImageDrawable(
                DeviceIconUtils.tile(this, item.icon, item.initialized)
            )
            row.vendorImportTargetName.text = item.name
            row.vendorImportTargetAddress.text = item.address
            row.root.setOnClickListener {
                startActivity(
                    Intent(this, VendorDataImportActivity::class.java)
                        .putExtra(GBDevice.EXTRA_DEVICE, item.device)
                )
            }
            container.addView(row.root)
        }
    }
}
