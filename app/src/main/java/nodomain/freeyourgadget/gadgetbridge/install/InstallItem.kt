package nodomain.freeyourgadget.gadgetbridge.install

import android.graphics.Bitmap
import androidx.annotation.DrawableRes

/**
 * One unit that is transferable to the device. Install Handlers extend it with the data that the
 * device support needs to send the item.
 */
open class InstallItem(
    val displayName: String,
    @DrawableRes val icon: Int,
    val sizeBytes: Long?,
    val details: String? = null,
    val picture: Bitmap? = null,
)
