package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.Context
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R

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
    fun deepSleep(context: Context): Int = chartColor(context, R.attr.chart_deep_sleep, R.color.chart_deep_sleep_light)

    @ColorInt
    fun lightSleep(context: Context): Int = chartColor(context, R.attr.chart_light_sleep, R.color.chart_light_sleep_light)

    @ColorInt
    fun remSleep(context: Context): Int = chartColor(context, R.attr.chart_rem_sleep, R.color.chart_rem_sleep_light)

    @ColorInt
    fun awakeSleep(context: Context): Int = chartColor(context, R.attr.chart_awake_sleep, R.color.chart_awake_sleep_light)

    @ColorInt
    val distance: Int = Color.rgb(0xf2, 0xa9, 0x3b)

    @ColorInt
    val activeTime: Int = Color.rgb(0x8e, 0x63, 0xce)

    private fun chartColor(context: Context, @AttrRes attr: Int, @ColorRes fallback: Int): Int =
        MaterialColors.getColor(context, attr, ContextCompat.getColor(context, fallback))
}
