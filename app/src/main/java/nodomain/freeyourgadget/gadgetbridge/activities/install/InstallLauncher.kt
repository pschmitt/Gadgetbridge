package nodomain.freeyourgadget.gadgetbridge.activities.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceService.EXTRA_OPTIONS

object InstallLauncher {
    /**
     * Returns the intent that opens the device's install screen for the files, or null if the
     * device cannot install them. Only an InstallHandlerV2 accepts more than one file.
     */
    @JvmStatic
    fun createIntent(context: Context, device: GBDevice, files: List<Uri>, options: Bundle): Intent? {
        if (files.isEmpty()) {
            return null
        }
        val coordinator = device.deviceCoordinator
        if (coordinator.findInstallHandlerV2(files, context) != null) {
            return InstallSessionActivity.createIntent(context, device, files)
        }
        if (files.size != 1) {
            return null
        }
        @Suppress("DEPRECATION")
        val handler = coordinator.findInstallHandler(files[0], options, context) ?: return null
        return Intent(context, handler.installActivity).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(files[0], null)
            putExtra(GBDevice.EXTRA_DEVICE, device)
            if (!options.isEmpty) {
                putExtra(EXTRA_OPTIONS, options)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
