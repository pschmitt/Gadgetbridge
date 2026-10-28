package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.CaloriesDailyFragment
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import java.text.NumberFormat

/**
 * Active calories for the day against the user's goal.
 */
object ActiveCaloriesWidget : MetricWidget<ActiveCaloriesWidget.Data>() {
    override val id = "activecalories"
    override val label = R.string.active_calories
    override val icon = R.drawable.ic_calories
    override val chartTab = "calories"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsActiveCalories(device)

    override fun chartMode(variant: WidgetVariant): String =
        CaloriesDailyFragment.GaugeViewMode.ACTIVE_CALORIES_GOAL.toString()

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val active = scope.activeCaloriesTotal()
        return Data(
            active,
            scope.activeCaloriesGoalFactor(active),
            ActivityUser().caloriesBurntGoal,
            if (config.isLarge) CaloriesWidget.hourlyKcal(scope) else null,
            scope.hoursStarted,
            if (config.isLargeStyle) scope.restingCaloriesTotal() else 0,
        )
    }

    override fun previewData(): Data {
        val calories = CaloriesWidget.previewData()
        val goal = ActivityUser.defaultUserCaloriesBurntGoal
        return Data(
            calories.activeCalories,
            calories.activeCalories / goal.toFloat(),
            goal,
            calories.hourlyActive,
            calories.hoursStarted,
            calories.restingCalories,
        )
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val number = NumberFormat.getInstance()
        val kcal = { value: Int -> context.getString(R.string.widget_value_kcal, number.format(value)) }
        return goalTile(
            context, config, number.format(data.active), ContextCompat.getColor(context, R.color.calories_color), data.goalFactor,
            number.format(data.goal), data.hourly, data.hoursStarted,
            ratio = if (data.goal > 0) data.active / data.goal.toDouble() else 0.0,
            toGo = (data.goal - data.active).takeIf { it > 0 }?.let { number.format(it) },
            extraStats = listOfNotNull(
                data.resting.takeIf { it > 0 }?.let { Stat(kcal(it), context.getString(R.string.hr_resting)) },
            ),
            lineGoal = data.goal.toFloat(),
        )
    }

    /**
     * [hourly] holds active kilocalories per hour, only for the tall, wide and large styles. [resting] is only loaded
     * for the large style.
     */
    class Data(
        val active: Int,
        val goalFactor: Float,
        val goal: Int,
        val hourly: FloatArray? = null,
        val hoursStarted: Int = WidgetHistory.HOURS_PER_DAY,
        val resting: Int = 0,
    )
}
