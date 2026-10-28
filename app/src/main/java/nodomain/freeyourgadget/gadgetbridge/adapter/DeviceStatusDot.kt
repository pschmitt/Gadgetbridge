package nodomain.freeyourgadget.gadgetbridge.adapter

import android.content.res.ColorStateList
import android.view.View
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

/**
 * Tints a connection status dot: red while busy, green when initialized, yellow while connecting, grey otherwise.
 */
object DeviceStatusDot {
    @JvmStatic
    fun apply(dot: View, device: GBDevice) {
        val color = when {
            device.isBusy -> ContextCompat.getColor(dot.context, R.color.device_status_busy)
            device.isInitialized -> ContextCompat.getColor(dot.context, R.color.device_status_connected)
            device.state.equalsOrHigherThan(GBDevice.State.CONNECTING) ->
                ContextCompat.getColor(dot.context, R.color.device_status_connecting)
            else -> MaterialColors.getColor(dot, R.attr.textColorSecondary)
        }
        dot.backgroundTintList = ColorStateList.valueOf(color)
    }

    @JvmStatic
    fun apply(dot: View, connected: Boolean) {
        dot.backgroundTintList = ColorStateList.valueOf(
            if (connected) {
                ContextCompat.getColor(dot.context, R.color.device_status_connected)
            } else {
                MaterialColors.getColor(dot, R.attr.textColorSecondary)
            }
        )
    }
}
