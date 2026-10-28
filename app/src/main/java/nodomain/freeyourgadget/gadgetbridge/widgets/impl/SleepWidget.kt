package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.HypnogramSegment
import nodomain.freeyourgadget.gadgetbridge.widgets.LegendRow
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * Sleep duration for the day against the user's sleep goal.
 */
object SleepWidget : MetricWidget<SleepWidget.Data>() {
    private const val LIGHT = 0
    private const val DEEP = 1
    private const val REM = 2
    private const val AWAKE = 3

    private val STAGE_COLORS = intArrayOf(WidgetColors.lightSleep, WidgetColors.deepSleep, WidgetColors.remSleep, WidgetColors.awakeSleep)
    private val STAGE_NAMES = intArrayOf(
        R.string.sleep_colored_stats_light,
        R.string.sleep_colored_stats_deep,
        R.string.sleep_colored_stats_rem,
        R.string.abstract_chart_fragment_kind_awake_sleep,
    )

    // Hypnogram lanes, top to bottom
    private const val LANE_AWAKE = 0
    private const val LANE_REM = 1
    private const val LANE_LIGHT = 2
    private const val LANE_DEEP = 3
    private val LANE_COLORS = intArrayOf(WidgetColors.awakeSleep, WidgetColors.remSleep, WidgetColors.lightSleep, WidgetColors.deepSleep)

    override val id = "sleep"
    override val label = R.string.menuitem_sleep
    override val icon = R.drawable.ic_activity_sleep
    override val chartTab = "sleep"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsSleepMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val total = scope.sleepMinutesTotal()
        if (!config.needsHistory) {
            return Data(total, scope.sleepGoalFactor(total))
        }
        return Data(
            total,
            scope.sleepGoalFactor(total),
            scope.sleepStageMinutes(),
            if (scope.devices.any { it.deviceCoordinator.supportsSleepScore(it) }) SleepScoreWidget.latestScore(scope) else -1,
            if (config.isFullWidth || config.isTall) scope.sleepStretches() else null,
            StageSupport.of(scope.devices),
        )
    }

    override fun previewData(): Data {
        val stages = listOf(
            LANE_LIGHT to 20,
            LANE_DEEP to 45,
            LANE_LIGHT to 25,
            LANE_REM to 15,
            LANE_AWAKE to 4,
            LANE_LIGHT to 30,
            LANE_DEEP to 35,
            LANE_LIGHT to 30,
            LANE_REM to 25,
            LANE_LIGHT to 35,
            LANE_DEEP to 14,
            LANE_LIGHT to 30,
            LANE_AWAKE to 6,
            LANE_REM to 30,
            LANE_LIGHT to 40,
            LANE_REM to 40,
            LANE_AWAKE to 8,
            LANE_LIGHT to 38,
        )
        var start = LocalDate.now().minusDays(1).atTime(23, 10).atZone(ZoneId.systemDefault()).toEpochSecond()
        val night = stages.map { (lane, minutes) ->
            HypnogramSegment(start, start + minutes * 60L, lane).also { start = it.end }
        }
        return Data(
            total = 452,
            goalFactor = 452f / ActivityUser.defaultUserSleepDurationGoal,
            stages = longArrayOf(248, 94, 110, 18),
            score = 84,
            night = night,
        )
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        if (data.total <= 0) {
            return emptyModel(context, chart = MiniChart.Progress(WidgetColors.lightSleep, -1f))
        }
        val stages = data.stages
        val hasStages = stages != null && stages[DEEP] + stages[REM] + stages[AWAKE] > 0
        val support = data.support
        val chart = if (stages != null && hasStages) {
            stageComposition(stages, support)
        } else {
            MiniChart.Progress(WidgetColors.lightSleep, data.goalFactor)
        }
        val night = data.night?.takeIf { it.isNotEmpty() }
        val times = night?.let { TileAxis.timeRange(it.first().start, it.last().end, ZoneId.systemDefault()) }
        if (config.isLargeStyle) {
            return TileModel(
                value = String.format(Locale.ROOT, "%d:%02d", data.total / 60, data.total % 60),
                gauge = GaugeSpec.Progress(WidgetColors.lightSleep, data.goalFactor),
                status = if (data.score > 0) {
                    StatusLine.Chip(WidgetColors.lightSleep, context.getString(R.string.sleep_score_value, data.score))
                } else {
                    null
                },
                valueSuffix = times?.joinToString(" - "),
                legend = if (stages != null && hasStages) stageLegend(context, stages, support) else emptyList(),
                largeChart = night?.let { hypnogram(it, hasStages, support) },
            )
        }
        return TileModel(
            value = String.format(Locale.ROOT, "%d:%02d", data.total / 60, data.total % 60),
            gauge = GaugeSpec.Progress(WidgetColors.lightSleep, data.goalFactor),
            status = if (data.score > 0) {
                StatusLine.Chip(WidgetColors.lightSleep, context.getString(R.string.sleep_score_value, data.score))
            } else {
                null
            },
            // the wide and tall tiles show the night as a hypnogram instead
            chart = if (config.isWide || config.isTall) null else chart,
            largeChart = night?.let { hypnogram(it, hasStages, support) },
            axis = times.orEmpty(),
        )
    }

    /**
     * Deep, light, REM and awake shares of [stages] (light, deep, REM, awake minutes).
     */
    internal fun stageComposition(stages: LongArray, support: StageSupport = StageSupport.ALL): MiniChart.Composition {
        val shown = shownStages(support)
        return MiniChart.Composition(
            IntArray(shown.size) { STAGE_COLORS[shown[it]] },
            FloatArray(shown.size) { stages[shown[it]].toFloat() },
        )
    }

    internal fun stageLegend(context: Context, stages: LongArray, support: StageSupport = StageSupport.ALL): List<LegendRow> =
        shownStages(support).map { stage ->
            LegendRow(STAGE_COLORS[stage], context.getString(STAGE_NAMES[stage]), hoursMinutes(context, stages[stage]))
        }

    private fun shownStages(support: StageSupport): List<Int> = listOfNotNull(
        DEEP,
        LIGHT,
        REM.takeIf { support.rem },
        AWAKE.takeIf { support.awake },
    )

    /**
     * The night on the awake, REM, light and deep lanes, or as one bar without stage data.
     */
    private fun hypnogram(night: List<HypnogramSegment>, hasStages: Boolean, support: StageSupport): MiniChart.Hypnogram {
        val start = night.first().start
        val end = night.last().end
        if (!hasStages) {
            return MiniChart.Hypnogram(
                night.filter { it.lane == LANE_LIGHT }.map { it.copy(lane = 0) },
                start,
                end,
                intArrayOf(WidgetColors.lightSleep),
            )
        }
        val lanes = listOfNotNull(
            LANE_AWAKE.takeIf { support.awake },
            LANE_REM.takeIf { support.rem },
            LANE_LIGHT,
            LANE_DEEP,
        )
        return MiniChart.Hypnogram(
            night.filter { it.lane in lanes }.map { it.copy(lane = lanes.indexOf(it.lane)) },
            start,
            end,
            IntArray(lanes.size) { LANE_COLORS[lanes[it]] },
        )
    }

    /**
     * Whether any of the devices records REM and awake phases.
     */
    class StageSupport(val rem: Boolean, val awake: Boolean) {
        companion object {
            val ALL = StageSupport(rem = true, awake = true)

            fun of(devices: List<GBDevice>) = StageSupport(
                rem = devices.any { it.deviceCoordinator.supportsRemSleep(it) },
                awake = devices.any { it.deviceCoordinator.supportsAwakeSleep(it) },
            )
        }
    }

    /**
     * [stages] are light, deep, REM and awake minutes and [score] the night's sleep score or -1, loaded for every
     * style but the gauge. [night] holds the night's stage stretches, only for the wide and large styles.
     */
    class Data(
        val total: Long,
        val goalFactor: Float,
        val stages: LongArray? = null,
        val score: Int = -1,
        val night: List<HypnogramSegment>? = null,
        val support: StageSupport = StageSupport.ALL,
    )
}
