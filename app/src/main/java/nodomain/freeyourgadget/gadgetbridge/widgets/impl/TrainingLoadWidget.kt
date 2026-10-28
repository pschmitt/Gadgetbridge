package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.PointF
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.load.LoadChartData
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import nodomain.freeyourgadget.gadgetbridge.model.TrainingLoadStatus
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.RangeBand
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Today's acute training load against the optimal range from the chronic load.
 */
object TrainingLoadWidget : MetricWidget<TrainingLoadWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(TrainingLoadWidget::class.java)

    private const val DAYS = 28
    private const val WEEKS = 4
    private const val MIN_SCALE = 600f
    private const val CHART_HEADROOM = 1.1f
    private const val HIGH_RATIO = 2f
    private const val THIRD = 1f / 3
    private const val BAND_ALPHA = 56
    private const val SPARKLINE_WIDTH_DP = 1.5f

    override val id = "trainingload"
    override val label = R.string.pref_header_training_load
    override val icon = R.drawable.ic_widget_training_load
    override val chartTab = "load"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsTrainingLoad(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val from = scope.dayStartMinusDays(DAYS - 1) * 1000L
        val to = scope.query.timeTo * 1000L
        val acute = mutableListOf<TimedValue>()
        val chronic = mutableListOf<TimedValue>()
        var reported: TrainingLoadStatus? = null
        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    acute += samples(db, dev, MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE, from, to)
                    if (dev.deviceCoordinator.supportsTrainingLoadChronic(dev)) {
                        chronic += samples(db, dev, MetricSample.Metric.GENERIC_TRAINING_LOAD_CHRONIC, from, to)
                    }
                    if (reported == null) {
                        reported = dev.deviceCoordinator.getTrainingLoadStatus(dev, db.daoSession, to)
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get training load samples", e)
        }
        val acuteDays = WidgetHistory.dailyLatest(acute, scope.day, DAYS, scope.zone)
        val chronicDays = WidgetHistory.dailyLatest(chronic, scope.day, DAYS, scope.zone)
        val todayAcute = acuteDays.last().takeUnless { it.isNaN() }?.roundToInt() ?: 0
        val todayChronic = chronicDays.last().takeUnless { it.isNaN() }?.roundToInt() ?: 0
        return Data(
            acute = todayAcute,
            chronic = todayChronic,
            status = if (todayAcute > 0) reported ?: statusFor(todayAcute, todayChronic) else null,
            reported = reported,
            acuteDays = if (config.needsHistory) acuteDays else null,
            chronicDays = if (config.needsHistory) chronicDays else null,
            day = scope.day,
        )
    }

    override fun previewData(): Data {
        val acuteDays = floatArrayOf(
            340f, 352f, 371f, 365f, 348f, 362f, 390f, 402f, 385f, 370f, 356f, 381f, 410f, 428f,
            415f, 398f, 380f, 362f, 389f, 405f, 431f, 446f, 437f, 418f, 401f, 395f, 412f, 420f,
        )
        val chronicDays = FloatArray(DAYS) { 350f + it * 1.2f }
        val acute = acuteDays.last().roundToInt()
        val chronic = chronicDays.last().roundToInt()
        return Data(
            acute = acute,
            chronic = chronic,
            status = statusFor(acute, chronic),
            acuteDays = acuteDays,
            chronicDays = chronicDays,
        )
    }

    /**
     * The day's acute or chronic load samples, from the metric samples like the Load chart when experimental
     * metrics are on.
     */
    @Suppress("DEPRECATION")
    private fun samples(db: DBHandler, dev: GBDevice, metric: MetricSample.Metric, from: Long, to: Long): List<TimedValue> {
        if (GBApplication.getPrefs().experimentalMetrics() && GenericMetricSampleProvider.supportsMetrics(dev, metric)) {
            return GenericMetricSampleProvider.getMetricSamples(db, dev, metric, from, to)
                .map { TimedValue(it.timestamp / 1000L, it.metricScore.toFloat()) }
        }
        val coordinator = dev.deviceCoordinator
        return if (metric == MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE) {
            coordinator.getTrainingAcuteLoadSampleProvider(dev, db.daoSession)?.getAllSamples(from, to).orEmpty()
                .map { TimedValue(it.timestamp / 1000L, it.value.toFloat()) }
        } else {
            coordinator.getTrainingChronicLoadSampleProvider(dev, db.daoSession)?.getAllSamples(from, to).orEmpty()
                .map { TimedValue(it.timestamp / 1000L, it.value.toFloat()) }
        }
    }

    /**
     * The status from the acute to chronic ratio, as the Load chart works it out, or null without a chronic load.
     */
    internal fun statusFor(acute: Int, chronic: Int): TrainingLoadStatus? {
        if (chronic <= 0) return null
        val ratio = acute.toFloat() / chronic
        return when {
            ratio < LoadChartData.OPTIMAL_LOAD_RATIO_LOWER -> TrainingLoadStatus.LOW
            ratio < LoadChartData.OPTIMAL_LOAD_RATIO_UPPER -> TrainingLoadStatus.OPTIMAL
            ratio < HIGH_RATIO -> TrainingLoadStatus.HIGH
            else -> TrainingLoadStatus.VERY_HIGH
        }
    }

    internal fun optimalLow(chronic: Int): Int = (chronic * LoadChartData.OPTIMAL_LOAD_RATIO_LOWER).roundToInt()

    internal fun optimalHigh(chronic: Int): Int = (chronic * LoadChartData.OPTIMAL_LOAD_RATIO_UPPER).roundToInt()

    /**
     * Where the marker sits on the three equal low, optimal and high thirds, as on the Load chart: in the middle of
     * the device's own status, or by the acute to chronic ratio. Negative without either.
     */
    internal fun gaugeMarker(acute: Int, chronic: Int, reported: TrainingLoadStatus?): Float {
        if (reported != null) {
            return when (reported) {
                TrainingLoadStatus.LOW -> 0.166f
                TrainingLoadStatus.OPTIMAL -> 0.5f
                TrainingLoadStatus.HIGH -> 0.833f
                TrainingLoadStatus.VERY_HIGH -> 1f
            }
        }
        if (acute <= 0 || chronic <= 0) return -1f
        val ratio = acute.toDouble() / chronic
        val lower = LoadChartData.OPTIMAL_LOAD_RATIO_LOWER.toDouble()
        val upper = LoadChartData.OPTIMAL_LOAD_RATIO_UPPER.toDouble()
        return when {
            ratio < lower -> GaugeDrawer.normalize(ratio, 0.0, lower, 0.0, 0.333)
            ratio < upper -> GaugeDrawer.normalize(ratio, lower, upper, 0.334, 0.666)
            ratio < HIGH_RATIO -> GaugeDrawer.normalize(ratio, upper, HIGH_RATIO.toDouble(), 0.667, 1.0)
            else -> 1.0
        }.toFloat()
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val colors = Colors(context)
        val gauge = gauge(data, colors)
        val chart = data.acuteDays?.let { fullChart(it, data.chronicDays ?: FloatArray(it.size) { Float.NaN }, colors, config.isLargeStyle) }
        val axis = if (chart != null) TileAxis.weekStarts(data.day, WEEKS) else emptyList()
        if (data.acute <= 0) {
            val empty = emptyModel(context, gauge)
            return when {
                config.isLargeStyle || config.isWide || config.isTall -> empty.copy(largeChart = chart, axis = axis)
                else -> empty
            }
        }
        val range = if (data.chronic > 0) "${optimalLow(data.chronic)}–${optimalHigh(data.chronic)}" else context.getString(R.string.stats_empty_value)
        val model = TileModel(
            value = data.acute.toString(),
            gauge = gauge,
            status = data.status?.let { StatusLine.Chip(colors.of(it), context.getString(nameOf(it))) },
            contentDescription = describe(context, config, data),
        )
        return when {
            config.isLargeStyle -> model.copy(
                miniStats = listOfNotNull(
                    data.chronic.takeIf { it > 0 }?.let { Stat(it.toString(), context.getString(R.string.widget_stat_chronic)) },
                    Stat(range, context.getString(R.string.optimal)),
                ),
                largeChart = chart,
                axis = axis,
            )
            config.isTall || config.isWide -> model.copy(largeChart = chart, axis = axis)
            else -> model.copy(chart = data.acuteDays?.let { sparkline(it, colors.acute) })
        }
    }

    private fun gauge(data: Data, colors: Colors): GaugeSpec = GaugeSpec.Zones(
        intArrayOf(colors.low, colors.optimal, colors.high),
        floatArrayOf(THIRD, THIRD, THIRD),
        gaugeMarker(data.acute, data.chronic, data.reported),
        markerColor = data.status?.let { colors.of(it) },
    )

    /**
     * The optimal range, the chronic line and the acute line over the 28 days. Missing days break the acute line;
     * the chronic line and the range carry on.
     */
    internal fun fullChart(acute: FloatArray, chronic: FloatArray, colors: Colors, dots: Boolean): MiniChart.Line {
        val known = chronic.indices.filter { !chronic[it].isNaN() }
        val highest = (acute.toList() + known.map { chronic[it] * LoadChartData.OPTIMAL_LOAD_RATIO_UPPER })
            .filter { !it.isNaN() }.maxOrNull() ?: 0f
        return MiniChart.Line(
            points = acute.indices.map { PointF(it.toFloat(), acute[it]) },
            xMin = 0f,
            xMax = (acute.size - 1).toFloat(),
            yMin = 0f,
            yMax = maxOf(MIN_SCALE, highest * CHART_HEADROOM),
            color = colors.acute,
            secondary = known.map { PointF(it.toFloat(), chronic[it]) },
            secondaryColor = colors.chronic,
            rangeBand = RangeBand(
                FloatArray(known.size) { known[it].toFloat() },
                FloatArray(known.size) { chronic[known[it]] * LoadChartData.OPTIMAL_LOAD_RATIO_LOWER },
                FloatArray(known.size) { chronic[known[it]] * LoadChartData.OPTIMAL_LOAD_RATIO_UPPER },
                ColorUtils.setAlphaComponent(colors.optimal, BAND_ALPHA),
            ),
            dots = dots,
        )
    }

    private fun sparkline(acute: FloatArray, color: Int): MiniChart.Line {
        val highest = acute.filter { !it.isNaN() }.maxOrNull() ?: 0f
        return MiniChart.Line(
            points = acute.indices.map { PointF(it.toFloat(), acute[it]) },
            xMin = 0f,
            xMax = (acute.size - 1).toFloat(),
            yMin = 0f,
            yMax = maxOf(1f, highest * CHART_HEADROOM),
            color = color,
            lineWidthDp = SPARKLINE_WIDTH_DP,
        )
    }

    private fun nameOf(status: TrainingLoadStatus): Int = when (status) {
        TrainingLoadStatus.LOW -> R.string.low
        TrainingLoadStatus.OPTIMAL -> R.string.optimal
        TrainingLoadStatus.HIGH -> R.string.high
        TrainingLoadStatus.VERY_HIGH -> R.string.very_high
    }

    private fun describe(context: Context, config: WidgetConfig, data: Data): String {
        val parts = mutableListOf(config.title(context, label), data.acute.toString())
        data.status?.let { parts += context.getString(nameOf(it)) }
        if (data.chronic > 0) {
            parts += context.getString(R.string.widget_load_description_chronic, data.chronic)
            parts += context.getString(R.string.widget_load_description_range, optimalLow(data.chronic), optimalHigh(data.chronic))
        }
        return parts.joinToString(", ")
    }

    internal class Colors(context: Context) {
        val acute = GBApplication.getTextColor(context)
        val chronic = ContextCompat.getColor(context, R.color.training_chronic_load)
        val optimal = ContextCompat.getColor(context, R.color.training_load_optimal)
        val low = ContextCompat.getColor(context, R.color.training_load_low)
        val high = ContextCompat.getColor(context, R.color.training_load_high)

        fun of(status: TrainingLoadStatus): Int = when (status) {
            TrainingLoadStatus.LOW -> low
            TrainingLoadStatus.OPTIMAL -> optimal
            TrainingLoadStatus.HIGH, TrainingLoadStatus.VERY_HIGH -> high
        }
    }

    /**
     * Today's [acute] and [chronic] load, 0 when missing, and the [status] the device reports ([reported]) or the
     * ratio gives.
     * [acuteDays] and [chronicDays] hold the 28 days ending on [day], NaN for days without a sample, for every style
     * but the gauge.
     */
    class Data(
        val acute: Int = 0,
        val chronic: Int = 0,
        val status: TrainingLoadStatus? = null,
        val reported: TrainingLoadStatus? = null,
        val acuteDays: FloatArray? = null,
        val chronicDays: FloatArray? = null,
        val day: LocalDate = LocalDate.now(),
    )
}
