package nodomain.freeyourgadget.gadgetbridge.activities.charts

import android.content.Context
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.load.LoadChartData
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample

/**
 * Stat tiles shared by the training readiness and training status screens.
 */
object TrainingStatTiles {
    @JvmStatic
    fun indicator(context: Context, value: String, label: String, indicatorText: String, @ColorRes colorRes: Int?): StatTileData {
        val color = if (colorRes != null) ContextCompat.getColor(context, colorRes) else GBApplication.getSecondaryTextColor(context)
        return StatTileData(value, label, null, indicatorText, null, color)
    }

    @JvmStatic
    fun hrv(context: Context, status: HrvSummarySample.Status, weeklyAverageMs: Int?): StatTileData {
        val value = weeklyAverageMs?.let { context.getString(R.string.hrv_status_unit, it) }
            ?: context.getString(R.string.stats_empty_value)
        val label = context.getString(R.string.pref_header_hrv_status)
        return when (status) {
            HrvSummarySample.Status.BALANCED ->
                indicator(context, value, label, context.getString(R.string.hrv_status_balanced), R.color.hrv_status_balanced)
            HrvSummarySample.Status.UNBALANCED ->
                indicator(context, value, label, context.getString(R.string.hrv_status_unbalanced), R.color.hrv_status_unbalanced)
            HrvSummarySample.Status.LOW ->
                indicator(context, value, label, context.getString(R.string.hrv_status_low), R.color.hrv_status_low)
            HrvSummarySample.Status.POOR ->
                indicator(context, value, label, context.getString(R.string.hrv_status_poor), R.color.hrv_status_poor)
            else -> StatTileData(value, label)
        }
    }

    @JvmStatic
    fun acuteLoad(context: Context, label: String, acuteLoad: Int?, chronicLoad: Int?): StatTileData {
        if (acuteLoad == null) {
            return StatTileData(context.getString(R.string.stats_empty_value), label)
        }
        val value = acuteLoad.toString()
        if (chronicLoad == null || chronicLoad <= 0) {
            return indicator(context, value, label, context.getString(R.string.none), null)
        }
        val ratio = acuteLoad.toFloat() / chronicLoad
        return when {
            ratio < LoadChartData.OPTIMAL_LOAD_RATIO_LOWER ->
                indicator(context, value, label, context.getString(R.string.low), R.color.training_load_low)
            ratio < LoadChartData.OPTIMAL_LOAD_RATIO_UPPER ->
                indicator(context, value, label, context.getString(R.string.optimal), R.color.training_load_optimal)
            ratio < 2f ->
                indicator(context, value, label, context.getString(R.string.high), R.color.training_load_high)
            else ->
                indicator(context, value, label, context.getString(R.string.very_high), R.color.training_load_high)
        }
    }
}
