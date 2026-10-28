package nodomain.freeyourgadget.gadgetbridge.activities.charts

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.fragment.app.Fragment
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

interface DeviceChartsProvider {
    fun getSupportedCharts(device: GBDevice): List<String>

    fun getEnabledCharts(device: GBDevice): List<String>

    fun getChartLabel(context: Context, device: GBDevice, chartName: String): String

    @DrawableRes
    fun getChartIcon(chartName: String): Int

    fun getChartFragment(device: GBDevice, chartName: String, allowSwipe: Boolean): Fragment

    /**
     * Returns a short human-readable text with the value of the chart for the day of [timestamp], in seconds.
     * Returns null if the chart has no single value, or if there is no data.
     */
    fun getChartValue(
        context: Context,
        db: DBHandler,
        device: GBDevice,
        chartName: String,
        timestamp: Int
    ): String?

    fun getDailySleepStats(
        context: Context,
        db: DBHandler,
        device: GBDevice,
        tsStart: Int,
        tsEnd: Int
    ): Map<String, ActivitySummarySimpleEntry>
}
