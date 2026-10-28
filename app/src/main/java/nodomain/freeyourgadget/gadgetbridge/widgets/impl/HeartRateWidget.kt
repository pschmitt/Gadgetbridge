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
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZonesUtils
import nodomain.freeyourgadget.gadgetbridge.util.TimeWeightedAverageAccumulator
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Most recent heart rate measurement of the day, taken from the activity samples so that
 * it works on any device that records heart rate, not only the ones that report daily
 * resting or manual measurements.
 */
object HeartRateWidget : MetricWidget<HeartRateWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(HeartRateWidget::class.java)

    /**
     * Zone floors as a percentage of the maximum heart rate, the same split the heart rate
     * zone settings use for the "maximum heart rate" method: warm-up, fat burn, aerobic,
     * anaerobic and extreme. Anything below the first floor is resting.
     */
    private val ZONE_PERCENTAGES = intArrayOf(50, 60, 70, 80, 90)

    private const val GAUGE_MIN = 40
    private const val GAUGE_MAX = 200
    private const val BUCKET_SECONDS = 15 * 60L
    private const val BUCKETS = 24 * 4
    private const val STALE_SECONDS = 15 * 60L
    private const val CHART_MIN = 40f
    private const val CHART_MIN_TOP = 160f
    private const val CHART_HEADROOM = 10f
    private const val LINE_WIDTH_DP = 1.5f

    private val COLOR = Color.rgb(0xEF, 0x44, 0x44)
    private val COLOR_RESTING_LINE = Color.rgb(0x5A, 0x2A, 0x2A)
    private val COLOR_NEUTRAL = Color.rgb(0x8F, 0x8F, 0x8F)

    // Rest, then zones 1 to 5
    private val ZONE_COLORS = intArrayOf(
        COLOR_NEUTRAL,
        Color.rgb(0x4F, 0xB3, 0xF0),
        Color.rgb(0x5B, 0xB5, 0x34),
        Color.rgb(0xF5, 0xA6, 0x23),
        Color.rgb(0xFF, 0x5A, 0x1F),
        Color.rgb(0xD6, 0x3A, 0x3A),
    )

    override val id = "heartrate"
    override val label = R.string.menuitem_hr
    override val icon = R.drawable.ic_heartrate
    override val chartTab = "heartrate"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsHeartRateMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var latestHeartRate = 0
        var latestTimestamp = 0
        val day = mutableListOf<TimedValue>()
        var maxGapMinutes = 0

        try {
            val utils = HeartRateUtils.getInstance()
            for (dev in scope.devices) {
                maxGapMinutes = maxOf(maxGapMinutes, dev.deviceCoordinator.getMaxHeartRateMeasurementsGapMinutes(dev))
                for (sample in scope.allSamples(dev)) {
                    if (!utils.isValidHeartRateValue(sample.heartRate)) {
                        continue
                    }
                    if (sample.timestamp > latestTimestamp) {
                        latestTimestamp = sample.timestamp
                        latestHeartRate = sample.heartRate
                    }
                    if (config.needsHistory) {
                        day += TimedValue(sample.timestamp.toLong(), sample.heartRate.toFloat())
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get heart rate samples", e)
        }

        if (!config.needsHistory) {
            return Data(latestHeartRate, maxHeartRate())
        }

        day.sortBy { it.seconds }
        val accumulator = TimeWeightedAverageAccumulator(60L * maxGapMinutes.coerceAtLeast(1), 60)
        for (sample in day) {
            accumulator.add(sample.seconds, sample.value.toDouble())
        }
        val hasStats = accumulator.count > 0
        return Data(
            heartRate = latestHeartRate,
            maxHeartRate = maxHeartRate(),
            line = WidgetHistory.bucketAverages(day, scope.dayStart, BUCKET_SECONDS, BUCKETS),
            resting = deviceResting(scope) ?: 0,
            minimum = if (hasStats) accumulator.min.roundToInt() else 0,
            maximum = if (hasStats) accumulator.max.roundToInt() else 0,
            average = if (hasStats) accumulator.average.roundToInt() else 0,
            latestSeconds = latestTimestamp.toLong(),
            isToday = scope.isToday,
        )
    }

    override fun previewData(): Data {
        val hourly = intArrayOf(58, 55, 53, 52, 52, 54, 61, 78, 86, 74, 71, 76, 104, 83, 75, 72)
        val line = FloatArray(BUCKETS) { bucket ->
            hourly.getOrNull(bucket / 4)?.let { it + 3 * sin(bucket * 1.7f) } ?: Float.NaN
        }
        return Data(
            heartRate = 72,
            maxHeartRate = 190,
            line = line,
            resting = 56,
            minimum = 49,
            maximum = 118,
            average = 68,
        )
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val floors = zoneFloors(data.maxHeartRate.coerceAtLeast(1))
        val segments = gaugeSegments(floors)
        if (data.heartRate <= 0) {
            return emptyModel(context, GaugeSpec.Zones(ZONE_COLORS, segments))
        }

        val now = System.currentTimeMillis() / 1000L
        val stale = data.isToday && data.latestSeconds > 0 && now - data.latestSeconds > STALE_SECONDS
        val measuredAt = Instant.ofEpochSecond(data.latestSeconds).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        val zone = zoneFor(data.heartRate, data.maxHeartRate.coerceAtLeast(1))
        val gauge = GaugeSpec.Zones(
            ZONE_COLORS,
            segments,
            gaugeFraction(data.heartRate),
            markerColor = if (stale) COLOR_NEUTRAL else ZONE_COLORS[zone],
        )
        val value = "${data.heartRate} ${context.getString(R.string.bpm)}"
        val restingChip = data.resting.takeIf { it > 0 }?.let {
            StatusLine.Chip(COLOR, context.getString(R.string.widget_status_resting, it))
        }
        val model = TileModel(
            value = value,
            gauge = gauge,
            valueNote = if (stale && (config.variant.id == WidgetVariant.NUMBER.id || config.isWide)) {
                context.getString(R.string.widget_status_at_time, measuredAt)
            } else {
                null
            },
            contentDescription = describe(context, config, data, stale, measuredAt),
        )
        return when {
            config.isLargeStyle -> model.copy(
                miniStats = listOfNotNull(
                    data.resting.takeIf { it > 0 }?.let { Stat(withUnit(context, it), context.getString(R.string.hr_resting)) },
                    Stat(withUnit(context, data.minimum), context.getString(R.string.hr_minimum)),
                    Stat(withUnit(context, data.maximum), context.getString(R.string.hr_maximum)),
                ),
                largeChart = data.line?.let { dayLine(it, data, withResting = true) },
                axis = TileAxis.HOURS,
            )
            config.isTall || config.isWide -> model.copy(
                status = restingChip,
                largeChart = data.line?.let { dayLine(it, data, withResting = true) },
                axis = TileAxis.HOURS,
            )
            else -> model.copy(
                status = restingChip,
                chart = data.line?.let { dayLine(it, data, withResting = false) },
            )
        }
    }

    /**
     * [bpm] with its unit, or the empty value when there is none.
     */
    private fun withUnit(context: Context, bpm: Int): String =
        if (bpm > 0) "$bpm ${context.getString(R.string.bpm)}" else context.getString(R.string.stats_empty_value)

    /**
     * The day's 15-minute averages. A single empty bucket is bridged, longer gaps break the line.
     */
    private fun dayLine(line: FloatArray, data: Data, withResting: Boolean): MiniChart.Line {
        val points = line.indices.mapNotNull { bucket ->
            val value = line[bucket]
            val bridged = value.isNaN() &&
                line.getOrNull(bucket - 1)?.isNaN() == false &&
                line.getOrNull(bucket + 1)?.isNaN() == false
            if (bridged) null else PointF(bucket.toFloat(), value)
        }
        val known = line.filter { !it.isNaN() }
        return MiniChart.Line(
            points = points,
            xMin = 0f,
            xMax = BUCKETS.toFloat(),
            yMin = CHART_MIN,
            yMax = maxOf(CHART_MIN_TOP, (known.maxOrNull() ?: 0f) + CHART_HEADROOM),
            color = COLOR,
            fill = true,
            reference = data.resting.takeIf { withResting && it > 0 }?.toFloat(),
            referenceColor = COLOR_RESTING_LINE,
            lineWidthDp = LINE_WIDTH_DP,
        )
    }

    private fun describe(context: Context, config: WidgetConfig, data: Data, stale: Boolean, measuredAt: String): String {
        val parts = mutableListOf(
            context.getString(R.string.widget_hr_description, config.title(context, label), data.heartRate),
        )
        if (data.resting > 0) {
            parts += context.getString(R.string.widget_hr_description_resting, data.resting)
        }
        if (config.isTall && data.minimum > 0) {
            parts += context.getString(R.string.widget_hr_description_min_max, data.minimum, data.maximum)
        }
        if (stale) {
            parts += context.getString(R.string.widget_hr_description_stale, measuredAt)
        }
        return parts.joinToString(", ")
    }

    /**
     * The device's resting heart rate for the day, if it reports one.
     */
    private suspend fun deviceResting(scope: WidgetDataScope): Int? = try {
        scope.db { db ->
            scope.devices.filter { it.deviceCoordinator.supportsHeartRateRestingMeasurement(it) }
                .flatMap { dev ->
                    dev.deviceCoordinator.getHeartRateRestingSampleProvider(dev, db.daoSession)
                        ?.getAllSamples(scope.dayStart * 1000L, scope.query.timeTo * 1000L)
                        .orEmpty()
                }
                .filter { it.heartRate > 0 }
                .maxByOrNull { it.timestamp }
                ?.heartRate
        }
    } catch (e: Exception) {
        LOG.error("Could not get resting heart rate", e)
        null
    }

    /** Where [bpm] sits on the gauge's 40 to 200 scale. */
    fun gaugeFraction(bpm: Int): Float = ((bpm - GAUGE_MIN).toFloat() / (GAUGE_MAX - GAUGE_MIN)).coerceIn(0f, 1f)

    /** Rest from 40 to the first floor, then the five zones, the last one up to 200. */
    fun gaugeSegments(floors: IntArray): FloatArray {
        val bounds = intArrayOf(GAUGE_MIN) + floors + GAUGE_MAX
        return FloatArray(bounds.size - 1) { gaugeFraction(bounds[it + 1]) - gaugeFraction(bounds[it]) }
    }

    /** The user's maximum heart rate, estimated as 220 minus age like the heart rate zone settings do. */
    private fun maxHeartRate(): Int = HeartRateZonesUtils.MAXIMUM_HEART_RATE - ActivityUser().age

    /** The lowest heart rate of each of the five zones, in ascending order. */
    fun zoneFloors(maxHeartRate: Int): IntArray =
        IntArray(ZONE_PERCENTAGES.size) { Math.round(maxHeartRate * ZONE_PERCENTAGES[it] / 100f) }

    /** 0 when [heartRate] is below the first zone (resting), otherwise the zone number 1..5. */
    fun zoneFor(heartRate: Int, maxHeartRate: Int): Int =
        zoneFloors(maxHeartRate).count { heartRate >= it }

    /**
     * Every field after [maxHeartRate] is only loaded for styles other than the gauge. [line] holds 15-minute
     * averages over the day; [resting] comes from the device, or is 0 when it reports none.
     */
    class Data(
        val heartRate: Int,
        val maxHeartRate: Int,
        val line: FloatArray? = null,
        val resting: Int = 0,
        val minimum: Int = 0,
        val maximum: Int = 0,
        val average: Int = 0,
        val latestSeconds: Long = 0,
        val isToday: Boolean = false,
    )
}
