package nodomain.freeyourgadget.gadgetbridge.util

import android.content.Context
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.ContextThemeWrapper
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import nodomain.freeyourgadget.gadgetbridge.R

object DeviceIconUtils {
    /**
     * The device icon on a tile. The icon is gray when the device is not connected.
     */
    fun tile(context: Context, @DrawableRes iconRes: Int, connected: Boolean): Drawable {
        val iconContext = ContextThemeWrapper(
            context,
            if (connected) R.style.ThemeOverlay_App_DeviceCardIcon_Connected else R.style.ThemeOverlay_App_DeviceCardIcon
        )
        val tile = AppCompatResources.getDrawable(iconContext, R.drawable.device_card_icon_bg)!!
        val icon = AppCompatResources.getDrawable(iconContext, iconRes)!!
        if (!connected) {
            icon.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
                setSaturation(0f)
            })
        }

        val tileSize = context.resources.getDimensionPixelSize(R.dimen.device_icon_tile_size)
        val iconInset = context.resources.getDimensionPixelSize(R.dimen.device_icon_tile_inset)
        return LayerDrawable(arrayOf(tile, icon)).apply {
            setLayerSize(0, tileSize, tileSize)
            setLayerInset(1, iconInset, iconInset, 0, 0)
            setLayerSize(1, tileSize - iconInset, tileSize - iconInset)
        }
    }
}
