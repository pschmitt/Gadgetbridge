package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig

/**
 * Active minutes for the day against the user's active time goal.
 */
object ActiveTimeWidget : MetricWidget<ActiveTimeWidget.Data>() {
    override val id = "activetime"
    override val label = R.string.activity_list_summary_active_time
    override val icon = R.drawable.ic_timer
    override val chartTab = "activity"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsStepCounter(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val total = scope.activeMinutesTotal()
        return Data(
            total,
            scope.activeMinutesGoalFactor(total),
            ActivityUser().activeTimeGoalMinutes.toLong(),
            if (config.isLarge) scope.hourlyActiveMinutes() else null,
            scope.hoursStarted,
            if (config.isLargeStyle) scope.stepsTotal() else 0,
            if (config.isLargeStyle) scope.distanceTotal() else 0f,
        )
    }

    override fun previewData(): Data {
        val hourly = floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 1f, 6f, 14f, 2f, 1f, 3f,
            8f, 2f, 1f, 4f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        )
        val total = hourly.sum().toLong()
        val goal = ActivityUser.defaultUserActiveTimeGoalMinutes.toLong()
        return Data(total, total / goal.toFloat(), goal, hourly, 16, 6400, 4800f)
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel = goalTile(
        context, config, clock(data.total), WidgetColors.activeTime, data.goalFactor, clock(data.goalMinutes),
        data.hourly, data.hoursStarted,
        ratio = if (data.goalMinutes > 0) data.total / data.goalMinutes.toDouble() else 0.0,
        toGo = (data.goalMinutes - data.total).takeIf { it > 0 }?.let { clock(it) },
        extraStats = listOf(
            Stat(data.steps.toString(), context.getString(R.string.steps)),
            Stat(FormatUtils.getFormattedDistanceLabel(data.distanceMeters.toDouble()), context.getString(R.string.distance)),
        ),
        lineGoal = data.goalMinutes.toFloat(),
    )

    /**
     * [hourly] holds active minutes per hour, only for the tall, wide and large styles. [steps] and
     * [distanceMeters] are only loaded for the large style.
     */
    class Data(
        val total: Long,
        val goalFactor: Float,
        val goalMinutes: Long,
        val hourly: FloatArray? = null,
        val hoursStarted: Int = WidgetHistory.HOURS_PER_DAY,
        val steps: Int = 0,
        val distanceMeters: Float = 0f,
    )
}
