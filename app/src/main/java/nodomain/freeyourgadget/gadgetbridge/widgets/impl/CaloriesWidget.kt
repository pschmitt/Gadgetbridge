package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.CaloriesDailyFragment
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import java.text.NumberFormat

/**
 * Total calories for the day, split into active and resting.
 */
object CaloriesWidget : MetricWidget<CaloriesWidget.Data>() {
    val SEGMENTED = WidgetVariant("segmented", R.string.menuitem_calories_segmented, 3, WidgetVariant.GAUGE_HEIGHT_DP)

    override val id = "calories"
    override val label = R.string.calories
    override val icon = R.drawable.ic_calories
    override val chartTab = "calories"
    override val variants = listOf(
        SEGMENTED,
        WidgetVariant.NUMBER,
        WidgetVariant.TALL,
        WidgetVariant.WIDE,
        WidgetVariant.METRIC_LARGE,
    )

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsActiveCalories(device)

    override fun chartMode(variant: WidgetVariant): String =
        CaloriesDailyFragment.GaugeViewMode.TOTAL_CALORIES_SEGMENT.toString()

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data = Data(
        scope.activeCaloriesTotal(),
        scope.restingCaloriesTotal(),
        if (config.isLarge) hourlyKcal(scope) else null,
        scope.hoursStarted,
    )

    override fun previewData(): Data {
        val hourly = floatArrayOf(
            0f, 0f, 0f, 0f, 0f, 0f, 4f, 28f, 62f, 18f, 14f, 20f,
            34f, 16f, 12f, 32f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f,
        )
        return Data(hourly.sum().toInt(), 1130, hourly, 16)
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val number = NumberFormat.getInstance()
        val totalCalories = data.activeCalories + data.restingCalories
        val colors = intArrayOf(
            ContextCompat.getColor(context, R.color.calories_resting_color),
            ContextCompat.getColor(context, R.color.calories_color),
        )
        val values = floatArrayOf(data.restingCalories.coerceAtLeast(0).toFloat(), data.activeCalories.coerceAtLeast(0).toFloat())
        val gauge = if (totalCalories != 0) {
            GaugeSpec.Zones(colors, FloatArray(2) { values[it] / totalCalories }, gapBetweenSegments = false)
        } else {
            GaugeSpec.Zones(intArrayOf(Color.argb(25, 128, 128, 128)), floatArrayOf(1f), gapBetweenSegments = false)
        }
        val kcal = { value: Int -> context.getString(R.string.widget_value_kcal, number.format(value)) }
        val hourly = data.hourlyActive
        return TileModel(
            value = number.format(totalCalories),
            gauge = gauge,
            status = if (config.isLargeStyle) {
                null
            } else {
                StatusLine.Text(context.getString(R.string.widget_status_number_active_calories, number.format(data.activeCalories)))
            },
            chart = if (config.isLargeStyle || config.isTall) null else MiniChart.Composition(colors, values),
            miniStats = if (config.isLargeStyle) {
                listOf(
                    Stat(kcal(data.restingCalories), context.getString(R.string.hr_resting)),
                    Stat(kcal(data.activeCalories), context.getString(R.string.active_calories_short)),
                )
            } else {
                emptyList()
            },
            largeChart = hourly?.let { hourlyBars(it, values[0], data.hoursStarted, colors) },
            axis = if (hourly != null) TileAxis.HOURS else emptyList(),
        )
    }

    /**
     * Resting calories spread evenly over the started hours under the [active] calories of each hour, so the bars
     * add up to the day's resting and active totals.
     */
    internal fun hourlyBars(active: FloatArray, resting: Float, hoursStarted: Int, colors: IntArray): MiniChart.StackedBars {
        val restingPerHour = resting / hoursStarted.coerceAtLeast(1)
        val restingBars = FloatArray(active.size) { hour -> if (hour < hoursStarted) restingPerHour else 0f }
        val max = active.indices.maxOfOrNull { hour -> restingBars[hour] + active[hour] }?.coerceAtLeast(1f) ?: 1f
        return MiniChart.StackedBars(restingBars, active, colors[0], colors[1], max, hoursStarted)
    }

    /**
     * Active kilocalories per hour.
     */
    internal suspend fun hourlyKcal(scope: WidgetDataScope): FloatArray =
        scope.hourlySums(scope.devices.filter { it.deviceCoordinator.supportsActiveCalories(it) }) { it.activeCalories / 1000f }

    /**
     * [hourlyActive] holds active kilocalories per hour, only for the tall, wide and large variants.
     */
    class Data(
        val activeCalories: Int,
        val restingCalories: Int,
        val hourlyActive: FloatArray? = null,
        val hoursStarted: Int = WidgetHistory.HOURS_PER_DAY,
    )
}
