package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.SleepScoreSample
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * Latest sleep score for the day.
 */
object SleepScoreWidget : MetricWidget<SleepScoreWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(SleepScoreWidget::class.java)
    private const val NIGHTS = 7
    private const val HISTORY_MIN = 40f
    private const val HISTORY_MAX = 100f

    override val id = "sleepscore"
    override val label = R.string.sleep_score
    override val icon = R.drawable.ic_widget_sleep_score
    override val chartTab = "sleep"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsSleepScore(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data = Data(
        latestScore(scope),
        if (config.needsHistory) nights(scope) else null,
        if (config.isLargeStyle) scope.sleepStageMinutes() else null,
        scope.day,
        if (config.isLargeStyle) scope.sleepMinutesTotal() else 0L,
        SleepWidget.StageSupport.of(scope.devices),
    )

    override fun previewData(): Data = Data(
        value = 84,
        nights = floatArrayOf(72f, 81f, 68f, 77f, 88f, 79f, 84f),
        stages = longArrayOf(248, 94, 110, 18),
        asleepMinutes = 452,
    )

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val history = data.nights?.let {
            MiniChart.Bars(it, intArrayOf(WidgetColors.lightSleep), HISTORY_MIN, HISTORY_MAX, highlightLast = true)
        }
        if (data.value <= 0) {
            // no device reported a sleep score for the day
            return emptyModel(context, GaugeSpec.Progress(Color.GRAY, -1f), history)
        }
        val stages = data.stages?.takeIf { it.sum() > 0 }
        if (config.isLargeStyle) {
            return TileModel(
                value = data.value.toString(),
                gauge = GaugeSpec.Progress(WidgetColors.lightSleep, data.value / 100f),
                status = StatusLine.Chip(WidgetColors.lightSleep, context.getString(bandOf(data.value))),
                miniStats = if (data.asleepMinutes > 0) {
                    listOf(Stat(clock(data.asleepMinutes), context.getString(R.string.widget_stat_asleep)))
                } else {
                    emptyList()
                },
                chart = stages?.let { SleepWidget.stageComposition(it, data.support) },
                legend = stages?.let { SleepWidget.stageLegend(context, it, data.support) }.orEmpty(),
                largeChart = history,
                axis = if (history != null) TileAxis.weekdays(data.day, NIGHTS) else emptyList(),
            )
        }
        return TileModel(
            value = data.value.toString(),
            gauge = GaugeSpec.Progress(WidgetColors.lightSleep, data.value / 100f),
            status = StatusLine.Chip(WidgetColors.lightSleep, context.getString(bandOf(data.value))),
            chart = history,
            largeChart = if (config.isWide || config.isTall) history else null,
            axis = if ((config.isWide || config.isTall) && history != null) TileAxis.weekdays(data.day, NIGHTS) else emptyList(),
        )
    }

    private fun bandOf(score: Int): Int = when {
        score >= 90 -> R.string.sleep_score_excellent
        score >= 80 -> R.string.sleep_score_good
        score >= 60 -> R.string.sleep_score_fair
        else -> R.string.sleep_score_poor
    }

    /**
     * The latest sleep score of the selected day, or -1.
     */
    internal suspend fun latestScore(scope: WidgetDataScope): Int {
        try {
            var latest: SleepScoreSample? = null
            for (dev in scope.devices.filter { it.deviceCoordinator.supportsSleepScore(it) }) {
                val sample = scope.db { db ->
                    dev.deviceCoordinator.getSleepScoreProvider(dev, db.daoSession)?.getLatestSample(
                        scope.query.timeTo * 1000L
                    )
                }?.takeIf { it.timestamp >= scope.dayStart * 1000L }
                if (sample != null && (latest == null || sample.timestamp > latest.timestamp)) {
                    latest = sample
                }
            }
            if (latest != null) {
                return latest.sleepScore
            }
        } catch (e: Exception) {
            LOG.error("Could not get sleep score", e)
        }
        return -1
    }

    private suspend fun nights(scope: WidgetDataScope): FloatArray {
        val from = scope.dayStartMinusDays(NIGHTS - 1) * 1000L
        val samples = try {
            scope.db { db ->
                scope.devices.flatMap { dev ->
                    dev.deviceCoordinator.getSleepScoreProvider(dev, db.daoSession)
                        ?.getAllSamples(from, scope.query.timeTo * 1000L)
                        .orEmpty()
                        .filter { it.sleepScore > 0 }
                        .map { TimedValue(it.timestamp / 1000L, it.sleepScore.toFloat()) }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get sleep score history", e)
            emptyList()
        }
        return WidgetHistory.dailyLatest(samples, scope.day, NIGHTS, scope.zone)
    }

    /**
     * [nights] holds the seven nights' scores ending on [day], oldest first, for every style but the gauge.
     * [stages] are the night's light, deep, REM and awake minutes, only for the tall and large styles, and
     * [asleepMinutes] the time asleep, only for the large style.
     */
    class Data(
        val value: Int,
        val nights: FloatArray? = null,
        val stages: LongArray? = null,
        val day: LocalDate = LocalDate.now(),
        val asleepMinutes: Long = 0,
        val support: SleepWidget.StageSupport = SleepWidget.StageSupport.ALL,
    )
}
