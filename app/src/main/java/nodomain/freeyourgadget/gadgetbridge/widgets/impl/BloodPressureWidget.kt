package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BloodPressureSample
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.RangeSeries
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/**
 * Latest systolic/diastolic reading, colored per the WHO classification.
 */
object BloodPressureWidget : MetricWidget<BloodPressureWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(BloodPressureWidget::class.java)
    private const val HISTORY_DAYS = 14
    private const val AVERAGE_DAYS = 7
    private const val CHART_MIN = 65f
    private const val CHART_MAX = 135f

    // Normal, elevated, stage 1 and stage 2+
    private val COLORS = intArrayOf(
        Color.rgb(76, 175, 80),
        Color.rgb(139, 195, 74),
        Color.rgb(255, 152, 0),
        Color.rgb(244, 67, 54),
    )
    private val LABELS = intArrayOf(
        R.string.normal,
        R.string.widget_status_blood_pressure_elevated,
        R.string.widget_status_blood_pressure_hypertension_stage_1,
        R.string.widget_status_blood_pressure_hypertension_stage_2,
    )
    private val POSITIONS = floatArrayOf(0.25f, 0.45f, 0.65f, 0.88f)
    private val SEGMENTS = floatArrayOf(0.35f, 0.2f, 0.2f, 0.25f)

    override val id = "bloodpressure"
    override val label = R.string.blood_pressure
    override val icon = R.drawable.ic_pressure
    override val chartTab = "bloodpressure"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsBloodPressureMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var latest: BloodPressureSample? = null
        val history = mutableListOf<BloodPressureSample>()
        val from = scope.dayStartMinusDays(HISTORY_DAYS - 1) * 1000L

        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    val provider = dev.deviceCoordinator.getBloodPressureSampleProvider(dev, db.daoSession) ?: continue
                    val sample = provider.getLatestSample(scope.query.timeTo * 1000L)
                    val current = latest
                    if (sample != null && (current == null || sample.timestamp > current.timestamp)) {
                        latest = sample
                    }
                    if (config.needsHistory) {
                        history += provider.getAllSamples(from, scope.query.timeTo * 1000L)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get blood pressure samples", e)
        }

        val reading = latest
        return Data(
            reading?.bpSystolic ?: 0,
            reading?.bpDiastolic ?: 0,
            reading?.timestamp?.div(1000L) ?: 0L,
            if (config.needsHistory) dailyReadings(history, scope.dayStart) else null,
            scope.dayStart,
        )
    }

    override fun previewData(): Data {
        val dayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val systolic = floatArrayOf(121f, 119f, Float.NaN, 124f, 118f, 122f, 117f, 120f, Float.NaN, 123f, 119f, 116f, 121f, 118f)
        val diastolic = floatArrayOf(79f, 77f, Float.NaN, 81f, 76f, 78f, 75f, 77f, Float.NaN, 80f, 78f, 74f, 78f, 76f)
        val daily = DailyReadings(
            systolic,
            diastolic,
            FloatArray(systolic.size) { systolic[it] - 6f },
            FloatArray(systolic.size) { systolic[it] + 5f },
            FloatArray(diastolic.size) { diastolic[it] - 4f },
            FloatArray(diastolic.size) { diastolic[it] + 3f },
        )
        return Data(118, 76, dayStart + 8 * 3600L, daily, dayStart)
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val bars = data.daily?.let { historyBars(context, it) }
        val axis = if (bars != null) historyAxis(data.dayStart) else emptyList()
        if (data.systolic <= 0 || data.diastolic <= 0) {
            val empty = emptyModel(context, GaugeSpec.Progress(Color.GRAY, -1f), MiniChart.ZoneBar(COLORS, SEGMENTS, -1f))
            return when {
                config.isTall && bars != null -> empty.copy(chart = null, largeChart = bars, axis = axis)
                config.isLarge -> empty.copy(largeChart = bars, axis = axis)
                else -> empty
            }
        }
        val category = categoryOf(data.systolic, data.diastolic)
        val readingDay = Instant.ofEpochSecond(data.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
        val selectedDay = Instant.ofEpochSecond(data.dayStart).atZone(ZoneId.systemDefault()).toLocalDate()
        val measuredAt = if (readingDay == selectedDay) {
            Instant.ofEpochSecond(data.timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
        } else {
            TileAxis.dateRange(readingDay, readingDay).first()
        }
        val model = TileModel(
            value = when {
                config.isLargeStyle -> format(context, data.systolic, data.diastolic)
                config.variant.id == WidgetVariant.GAUGE.id || config.isTall -> "${data.systolic}\n${data.diastolic}"
                else -> context.getString(R.string.blood_pressure_avg_format, data.systolic, data.diastolic)
            },
            gauge = GaugeSpec.Progress(COLORS[category], POSITIONS[category]),
            status = StatusLine.Chip(COLORS[category], context.getString(LABELS[category])),
            chart = MiniChart.ZoneBar(COLORS, SEGMENTS, POSITIONS[category]),
            valueDimmed = readingDay.isBefore(selectedDay.minusDays(AVERAGE_DAYS - 1L)),
            valueNote = if (config.variant.id == WidgetVariant.NUMBER.id || config.isWide) measuredAt else null,
        )
        return when {
            config.isLargeStyle -> model.copy(
                miniStats = listOfNotNull(
                    data.daily?.average(AVERAGE_DAYS)?.let { (systolic, diastolic) ->
                        Stat(
                            context.getString(R.string.blood_pressure_avg_format, systolic, diastolic),
                            context.getString(R.string.hrv_status_seven_days_avg),
                        )
                    },
                ),
                largeChart = bars,
                axis = axis,
            )
            config.isTall -> model.copy(chart = if (bars != null) null else model.chart, largeChart = bars, axis = axis)
            config.isLarge -> model.copy(largeChart = bars, axis = axis)
            else -> model
        }
    }

    // WHO classification: color and gauge position based on systolic/diastolic values
    internal fun categoryOf(systolic: Int, diastolic: Int): Int = when {
        systolic < 120 && diastolic < 80 -> 0
        systolic < 130 && diastolic < 80 -> 1
        systolic < 140 && diastolic < 90 -> 2
        else -> 3
    }

    private fun format(context: Context, systolic: Int, diastolic: Int): String =
        context.getString(R.string.blood_pressure_avg_format, systolic, diastolic) + " " +
            context.getString(R.string.unit_millimetre_of_mercury)

    /** Mean, lowest and highest systolic and diastolic per day of the last [HISTORY_DAYS] days, NaN for days without a reading. */
    internal fun dailyReadings(samples: List<BloodPressureSample>, dayStart: Long): DailyReadings {
        val lastDay = WidgetHistory.dayOf(dayStart)
        val byDay = samples.filter { it.bpSystolic > 0 && it.bpDiastolic > 0 }
            .groupBy { HISTORY_DAYS - 1 - ChronoUnit.DAYS.between(WidgetHistory.dayOf(it.timestamp / 1000L), lastDay).toInt() }
        fun perDay(valueOf: (BloodPressureSample) -> Int, reduce: (List<Int>) -> Float) =
            FloatArray(HISTORY_DAYS) { day -> byDay[day]?.map(valueOf)?.let(reduce) ?: Float.NaN }
        val average: (List<Int>) -> Float = { it.average().toFloat() }
        val lowest: (List<Int>) -> Float = { it.min().toFloat() }
        val highest: (List<Int>) -> Float = { it.max().toFloat() }
        return DailyReadings(
            perDay({ it.bpSystolic }, average),
            perDay({ it.bpDiastolic }, average),
            perDay({ it.bpSystolic }, lowest),
            perDay({ it.bpSystolic }, highest),
            perDay({ it.bpDiastolic }, lowest),
            perDay({ it.bpDiastolic }, highest),
        )
    }

    private fun historyAxis(dayStart: Long): List<String> {
        val day = Instant.ofEpochSecond(dayStart).atZone(ZoneId.systemDefault()).toLocalDate()
        return TileAxis.dateRange(day.minusDays(HISTORY_DAYS - 1L), day)
    }

    /** Systolic and diastolic range per day. */
    private fun historyBars(context: Context, daily: DailyReadings): MiniChart.RangeBars {
        val known = (daily.systolicLow + daily.systolicHigh + daily.diastolicLow + daily.diastolicHigh).filter { !it.isNaN() }
        return MiniChart.RangeBars(
            listOf(
                RangeSeries(daily.systolicLow, daily.systolicHigh, ContextCompat.getColor(context, R.color.blood_pressure_systolic_color)),
                RangeSeries(daily.diastolicLow, daily.diastolicHigh, ContextCompat.getColor(context, R.color.blood_pressure_diastolic_color)),
            ),
            minOf(CHART_MIN, known.minOrNull() ?: CHART_MIN),
            maxOf(CHART_MAX, known.maxOrNull() ?: CHART_MAX),
        )
    }

    /**
     * [timestamp] is the latest reading's time in seconds. [daily] holds the last 14 days' readings, only when the
     * style shows history.
     */
    data class Data(
        val systolic: Int,
        val diastolic: Int,
        val timestamp: Long = 0,
        val daily: DailyReadings? = null,
        val dayStart: Long = 0,
    )

    class DailyReadings(
        val systolic: FloatArray,
        val diastolic: FloatArray,
        val systolicLow: FloatArray,
        val systolicHigh: FloatArray,
        val diastolicLow: FloatArray,
        val diastolicHigh: FloatArray,
    ) {
        /** Mean of the last [days] days' averages, or null without a reading in them. */
        fun average(days: Int): Pair<Int, Int>? {
            val from = systolic.size - days
            val s = systolic.drop(from).filter { !it.isNaN() }
            val d = diastolic.drop(from).filter { !it.isNaN() }
            if (s.isEmpty() || d.isEmpty()) return null
            return s.average().roundToInt() to d.average().roundToInt()
        }
    }
}
