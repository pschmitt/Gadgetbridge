package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit
import nodomain.freeyourgadget.gadgetbridge.widgets.Band
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

object HydrationWidget : MetricWidget<HydrationWidget.Data>() {
    private const val DAYS = 7
    private const val SCALE_ABOVE = 1.1f
    private const val BAND_ALPHA = 36
    private const val MISSED_DARKEN = 0.35f

    override val id = "hydration"
    override val label = R.string.pref_header_hydration
    override val icon = R.drawable.ic_drink
    override val chartTab = "hydration"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsHydration(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val devices = scope.devices.filter { it.deviceCoordinator.supportsHydration(it) }
        val date = LocalDate.ofInstant(Instant.ofEpochSecond(scope.query.timeTo.toLong()), ZoneId.systemDefault())
        val day = HydrationSampleProvider.toDay(date)
        val totalMl = scope.db { db ->
            devices.sumOf { device ->
                device.deviceCoordinator.getHydrationSampleProvider(device, db.daoSession)?.getDayTotal(day) ?: 0.0
            }
        }
        val days = if (config.isLarge) {
            val totals = scope.db { db ->
                devices.map { device ->
                    device.deviceCoordinator.getHydrationSampleProvider(device, db.daoSession)
                        ?.getDayTotals(HydrationSampleProvider.toDay(date.minusDays(DAYS - 1L)), day).orEmpty()
                }
            }
            FloatArray(DAYS) { i ->
                val key = HydrationSampleProvider.toDay(date.minusDays(DAYS - 1L - i))
                totals.mapNotNull { it[key] }.takeIf { it.isNotEmpty() }?.sum()?.toFloat() ?: Float.NaN
            }
        } else {
            null
        }
        val unit = devices.firstOrNull()?.let { HydrationUnit.forDevice(it) } ?: HydrationUnit.MILLILITER
        return Data(totalMl, ActivityUser().hydrationGoalMl, unit, days, date)
    }

    override fun previewData() = Data(
        1450.0,
        2000,
        HydrationUnit.MILLILITER,
        floatArrayOf(1800f, 2250f, 1600f, 2000f, 2400f, 1900f, 1450f),
    )

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val color = ContextCompat.getColor(context, R.color.hydration_color)
        val factor = (data.totalMl / data.goalMl).toFloat().coerceIn(0f, 1f)
        val toGo = (data.goalMl - data.totalMl).takeIf { it > 0 }?.let { format(context, it, data.unit) }
        val tile = goalTile(
            context,
            config,
            format(context, data.totalMl, data.unit),
            color,
            factor,
            format(context, data.goalMl.toDouble(), data.unit),
            hourly = null,
            hoursStarted = 0,
            ratio = data.totalMl / data.goalMl,
            toGo = toGo,
        )
        val days = data.days ?: return tile
        val goal = data.goalMl.toFloat()
        val max = maxOf(goal, days.filter { !it.isNaN() }.maxOrNull() ?: 0f) * SCALE_ABOVE
        val missed = ColorUtils.blendARGB(color, Color.BLACK, MISSED_DARKEN)
        val bars = MiniChart.Bars(
            days,
            IntArray(DAYS) { if (days[it] >= goal) color else missed },
            0f,
            max,
            band = Band(goal, max, ColorUtils.setAlphaComponent(color, BAND_ALPHA)),
        )
        return tile.copy(largeChart = bars, axis = TileAxis.weekdays(data.day, DAYS))
    }

    private fun format(context: Context, ml: Double, unit: HydrationUnit): String =
        if (unit == HydrationUnit.MILLILITER && ml >= 1000) {
            val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
            numberFormat.maximumFractionDigits = 1
            numberFormat.format(ml / 1000) + " " + context.getString(R.string.unit_liter)
        } else {
            unit.format(context, ml)
        }

    /**
     * [days] holds the total in ml of each of the [DAYS] days ending on [day], only for the tall and full-width styles.
     */
    data class Data(
        val totalMl: Double,
        val goalMl: Int,
        val unit: HydrationUnit,
        val days: FloatArray? = null,
        val day: LocalDate = LocalDate.now(),
    )
}
