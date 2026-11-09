package nodomain.freeyourgadget.gadgetbridge.model

import android.content.Context
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Garmin training status values stored in [MetricSample.Metric.GARMIN_TRAINING_STATUS].
 */
enum class TrainingStatus(
    val value: Int,
    @StringRes val labelResId: Int,
    @ColorRes val colorResId: Int,
    @DrawableRes val iconResId: Int,
) {
    PAUSED(-1, R.string.training_status_paused, R.color.training_status_paused, R.drawable.ic_pause_circle),
    NO_STATUS(0, R.string.training_status_no_status, R.color.training_status_no_status, R.drawable.ic_help_outline), // unconfirmed - based on predictions
    DETRAINING(1, R.string.training_status_detraining, R.color.training_status_detraining, R.drawable.ic_trending_down),
    UNPRODUCTIVE(2, R.string.training_status_unproductive, R.color.training_status_unproductive, R.drawable.ic_trending_flat), // unconfirmed - based on predictions
    OVERREACHING(3, R.string.training_status_overreaching, R.color.training_status_overreaching, R.drawable.ic_warning), // unconfirmed - based on predictions
    MAINTAINING(4, R.string.training_status_maintaining, R.color.training_status_maintaining, R.drawable.ic_balance),
    RECOVERY(5, R.string.training_status_recovery, R.color.training_status_recovery, R.drawable.ic_battery_charging_full),
    PEAKING(6, R.string.training_status_peaking, R.color.training_status_peaking, R.drawable.ic_emoji_events), // unconfirmed - based on predictions
    PRODUCTIVE(7, R.string.training_status_productive, R.color.training_status_productive, R.drawable.ic_trending_up),
    STRAINED(8, R.string.training_status_strained, R.color.training_status_strained, R.drawable.ic_priority_high);

    companion object {
        @JvmField
        val PAUSED_VALUE = PAUSED.value

        @JvmStatic
        fun of(value: Int): TrainingStatus? = entries.firstOrNull { it.value == value }

        @JvmStatic
        fun label(context: Context, value: Int): String =
            of(value)?.let { context.getString(it.labelResId) }
                ?: context.getString(R.string.training_status_unknown, value)

        @JvmStatic
        fun color(context: Context, value: Int): Int =
            ContextCompat.getColor(context, of(value)?.colorResId ?: R.color.training_status_unknown)

        @JvmStatic
        @DrawableRes
        fun icon(value: Int): Int = of(value)?.iconResId ?: R.drawable.ic_help_outline
    }
}
