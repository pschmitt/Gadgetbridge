/*  Copyright (C) 2026 oddballza

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import android.graphics.PointF
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.WeightSample
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/**
 * Most recent weight measurement taken on or before the selected day. Weight is not
 * measured every day, so the last known value is shown rather than a value for the day
 * itself. The gauge is coloured by WHO BMI class, computed from the profile height.
 */
object WeightWidget : MetricWidget<WeightWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(WeightWidget::class.java)
    private const val HISTORY_DAYS = 30
    private const val SECONDS_PER_DAY = 24 * 60 * 60L
    private const val HISTORY_PADDING_KG = 0.5f

    override val id = "weight"
    override val label = R.string.menuitem_weight
    override val icon = R.drawable.ic_weight
    override val chartTab = "weight"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsWeightMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val latest = latestWeightSample(scope)
        return Data(
            latest?.weightKg?.toDouble() ?: 0.0,
            ActivityUser().heightCm,
            if (config.needsHistory) history(scope) { _, sample -> sample.weightKg.takeIf { it > 0 } } else null,
            scope.dayStart,
        )
    }

    override fun previewData(): Data {
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val weights = listOf(
            29 to 78.4f,
            26 to 78.1f,
            23 to 78.3f,
            20 to 77.8f,
            17 to 77.6f,
            14 to 77.7f,
            11 to 77.3f,
            8 to 77.2f,
            5 to 76.9f,
            2 to 77.0f,
            0 to 76.8f,
        )
        val history = weights.map { (daysAgo, kg) -> TimedValue(dayStart - daysAgo * SECONDS_PER_DAY + 7 * 3600L, kg) }
        return Data(weightKg = history.last().value.toDouble(), heightCm = 178, history = history, dayStart = dayStart)
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        if (data.weightKg <= 0) {
            return emptyModel(context, GaugeSpec.Progress(Color.GRAY, -1f))
        }

        val bmi = Bmi.of(data.weightKg, data.heightCm)
        val color = bmi?.let { Bmi.colorFor(it) } ?: Color.rgb(76, 175, 80)
        val history = data.history
        val change = if (history != null && history.size > 1) history.last().value - history.first().value else null
        val line = history?.takeIf { it.isNotEmpty() }?.let { historyLine(it, data.dayStart, color, HISTORY_PADDING_KG) }
        return TileModel(
            value = formatWeight(context, data.weightKg),
            // no height in the profile, so no BMI to colour by
            gauge = GaugeSpec.Progress(color, bmi?.let { Bmi.gaugeFraction(it) } ?: -1f),
            status = change?.let {
                val sign = if (it < 0) "-" else "+"
                StatusLine.Text(
                    context.getString(R.string.widget_status_weight_change, sign + formatWeight(context, abs(it).toDouble()))
                )
            },
            chart = line,
            largeChart = if (config.isLarge) line else null,
            axis = if (config.isLarge && line != null) historyAxis(data.dayStart) else emptyList(),
        )
    }

    /** [kg] in the preferred unit, with one decimal. */
    internal fun formatWeight(context: Context, kg: Double): String {
        val unit = GBApplication.getPrefs().weightUnit
        val converted = WeightUnit.convertWeight(kg, unit)
        return when (unit) {
            WeightUnit.KILOGRAM -> context.getString(R.string.widget_weight_kilogram_format, converted)
            WeightUnit.POUND -> context.getString(R.string.widget_weight_pound_format, converted)
            WeightUnit.JIN -> context.getString(R.string.widget_weight_jin_format, converted)
            WeightUnit.STONE -> WeightUnit.formatConvertedWeight(context, converted, unit)
        }
    }

    internal fun historyAxis(dayStart: Long): List<String> {
        val day = Instant.ofEpochSecond(dayStart).atZone(ZoneId.systemDefault()).toLocalDate()
        return TileAxis.dateRange(day.minusDays(HISTORY_DAYS - 1L), day)
    }

    internal fun historyLine(history: List<TimedValue>, dayStart: Long, color: Int, padding: Float): MiniChart.Line {
        val start = dayStart - (HISTORY_DAYS - 1) * SECONDS_PER_DAY
        return MiniChart.Line(
            points = history.map { PointF((it.seconds - start).toFloat() / SECONDS_PER_DAY, it.value) },
            xMin = 0f,
            xMax = HISTORY_DAYS.toFloat(),
            yMin = history.minOf { it.value } - padding,
            yMax = history.maxOf { it.value } + padding,
            color = color,
        )
    }

    /** [valueOf] of each measurement of the last 30 days, oldest first, skipping those it returns null for. */
    internal suspend fun history(scope: WidgetDataScope, valueOf: (DaoSession, WeightSample) -> Float?): List<TimedValue> = try {
        val from = scope.dayStartMinusDays(HISTORY_DAYS - 1) * 1000L
        scope.db { db ->
            scope.devices.flatMap { dev ->
                dev.deviceCoordinator.getWeightSampleProvider(dev, db.daoSession)
                    ?.getAllSamples(from, scope.query.timeTo * 1000L)
                    .orEmpty()
                    .mapNotNull { sample -> valueOf(db.daoSession, sample)?.let { TimedValue(sample.timestamp / 1000L, it) } }
            }.sortedBy { it.seconds }
        }
    } catch (e: Exception) {
        LOG.error("Could not get weight history", e)
        emptyList()
    }

    /** The most recent weight measurement of any device, on or before the selected day. */
    internal suspend fun latestWeightSample(scope: WidgetDataScope): WeightSample? = try {
        scope.db { db ->
            var newest: WeightSample? = null
            for (dev in scope.devices) {
                val sample = dev.deviceCoordinator.getWeightSampleProvider(dev, db.daoSession)
                    ?.getLatestSample(scope.query.timeTo * 1000L)
                if (sample != null && (newest == null || sample.timestamp > newest.timestamp)) {
                    newest = sample
                }
            }
            newest
        }
    } catch (e: Exception) {
        LOG.error("Could not get weight samples", e)
        null
    }

    /**
     * [history] holds the last 30 days' measurements, oldest first, only for the number style.
     */
    class Data(val weightKg: Double, val heightCm: Int, val history: List<TimedValue>? = null, val dayStart: Long = 0)
}
