package nodomain.freeyourgadget.gadgetbridge.widgets

import android.graphics.Color
import androidx.annotation.ColorInt

/**
 * Shared palette for the dashboard widgets.
 */
object WidgetColors {
    @ColorInt
    val unknown: Int = Color.argb(25, 128, 128, 128)

    @ColorInt
    val notWorn: Int = Color.BLACK

    @ColorInt
    val worn: Int = Color.rgb(128, 128, 128)

    @ColorInt
    val activity: Int = Color.rgb(0x60, 0xbd, 0x6d)

    @ColorInt
    val exercise: Int = Color.rgb(255, 128, 0)

    @ColorInt
    val deepSleep: Int = Color.rgb(0, 84, 163)

    @ColorInt
    val lightSleep: Int = Color.rgb(7, 158, 243)

    @ColorInt
    val remSleep: Int = Color.rgb(228, 39, 199)

    @ColorInt
    val awakeSleep: Int = Color.rgb(0xff, 0x86, 0x6e)

    @ColorInt
    val distance: Int = Color.rgb(0xf2, 0xa9, 0x3b)

    @ColorInt
    val activeTime: Int = Color.rgb(0x8e, 0x63, 0xce)
}
