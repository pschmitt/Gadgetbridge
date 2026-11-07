/*  Copyright (C) 2026 Thomas Kuehne

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
import android.graphics.PointF
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import org.slf4j.LoggerFactory
import kotlin.math.ceil
import kotlin.math.floor

/**
 * Daily total descent (elevation loss) in meters.
 */
object DescentWidget : MetricWidget<DescentWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(DescentWidget::class.java)
    private const val WEEKS = 12

    override val id = "descent"
    override val label = R.string.activityTotalDescent
    override val icon = R.drawable.ic_south_east
    override val chartTab = "genericmetrics"

    override fun chartMode(variant: WidgetVariant): String = MetricSample.Metric.DAILY_TOTAL_DESCENT.name

    override fun isSupportedBy(device: GBDevice): Boolean =
        GenericMetricSampleProvider.supportsMetrics(device, MetricSample.Metric.DAILY_TOTAL_DESCENT)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var value = -1f
        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    val sample = GenericMetricSampleProvider.getLatestMetricSample(
                        db, dev, MetricSample.Metric.DAILY_TOTAL_DESCENT,
                        scope.query.timeFrom * 1000L, scope.query.timeTo * 1000L
                    )
                    if (sample != null) {
                        value = sample.metricScore.toFloat()
                        break
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get descent for today", e)
        }

        return Data(
            value,
            if (config.needsHistory) weeks(scope) else null,
            scope.day,
        )
    }

    override fun previewData(): Data = Data(
        value = 180f,
        weeks = floatArrayOf(150f, 170f, 160f, 190f, 200f, 185f, 210f, 175f, 180f, 195f, 185f, 180f),
    )

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        if (data.value < 0f) {
            return emptyModel(context)
        }
        val line = data.weeks?.let { weekLine(context, it) }
        return TileModel(
            value = FormatUtils.getFormattedDistanceLabel(data.value.toDouble()),
            gauge = GaugeSpec.EMPTY,
            chart = line,
            largeChart = if (config.isLarge) line else null,
            axis = if (config.isLarge && line != null) TileAxis.months(data.day, WEEKS) else emptyList(),
        )
    }

    private suspend fun weeks(scope: WidgetDataScope): FloatArray {
        val from = scope.dayStartMinusDays(WEEKS * 7 - 1) * 1000L
        val to = scope.query.timeTo * 1000L
        val samples = try {
            scope.db { db ->
                scope.devices.flatMap { dev ->
                    GenericMetricSampleProvider.getMetricSamples(
                        db, dev, MetricSample.Metric.DAILY_TOTAL_DESCENT, from, to
                    ).map { TimedValue(it.timestamp / 1000L, it.metricScore.toFloat()) }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get descent history", e)
            emptyList()
        }
        return WidgetHistory.weeklyLatest(samples, scope.day, WEEKS, scope.zone)
    }

    private fun weekLine(context: Context, weeks: FloatArray): MiniChart.Line {
        val known = weeks.filter { !it.isNaN() }
        val yMin = floor((known.minOrNull() ?: 0f)).coerceAtLeast(0f)
        val yMax = ceil((known.maxOrNull() ?: 0f) + 1)
        return MiniChart.Line(
            points = weeks.withIndex().filter { !it.value.isNaN() }.map { PointF(it.index.toFloat(), it.value) },
            xMin = 0f,
            xMax = (weeks.size - 1).toFloat(),
            yMin = yMin,
            yMax = yMax,
            color = GBApplication.getTextColor(context),
        )
    }

    /**
     * [weeks] holds the latest descent values of the twelve weeks ending on [day], oldest first.
     */
    class Data(
        val value: Float,
        val weeks: FloatArray? = null,
        val day: java.time.LocalDate = java.time.LocalDate.now(),
    )
}
