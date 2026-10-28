/*  Copyright (C) 2026 Manuel Marcatili

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
package nodomain.freeyourgadget.gadgetbridge.util.sleep

import java.time.Instant
import kotlin.math.sqrt

/** A stage the estimator assigns to a minute. UNKNOWN is a not-worn gap inside the night. */
enum class EstimatedStage { AWAKE, LIGHT, DEEP, REM, UNKNOWN }

/** `[start, end)` spent at [stage]. */
data class EstimatedStageSpan(
    val start: Instant,
    val end: Instant,
    val stage: EstimatedStage,
)

/** One estimated night. [onset] is sleep onset, not bed time. */
data class EstimatedSleepSession(
    val onset: Instant,
    val end: Instant,
    /** True until 45 minutes of not sleeping have been seen after [end]. */
    val inProgress: Boolean,
    val stages: List<EstimatedStageSpan>,
    val sleepMinutes: Int,
    val awakeMinutes: Int,
    val unknownMinutes: Int,
    /** The deep and REM score thresholds after the sanity loop. 0.6 when it never fired. */
    val deepThreshold: Float,
    val remThreshold: Float,
)

/**
 * Every constant of the estimator. The defaults were fitted on three nights
 * of one wearer's Venu SQ; a test can tighten or loosen them.
 */
data class SleepEstimatorConfig(
    /** Activity assigned to a minute the watch itself called awake. */
    val awakeActivity: Float = 10f,
    /** Weighted activity at or above which a raw minute counts as awake. */
    val sleepThreshold: Float = 8f,
    /** A run of not sleeping this long splits candidate blocks and ends a night. */
    val maxBreakMinutes: Int = 45,
    /** Sleep onset is the first sleep run this long; the end is the last one. */
    val onsetRunMinutes: Int = 10,
    val endRunMinutes: Int = 10,
    val minWindowMinutes: Int = 180,
    val minSleepMinutes: Int = 150,
    val heartRateSmoothHalf: Int = 2,
    val baselineHalf: Int = 45,
    val deviationHalf: Int = 3,
    val movementHalf: Int = 2,
    /** A wake run this long resets the post-wake light-sleep rule. */
    val wakeRunMinutes: Int = 3,
    val remLatencyMinutes: Int = 60,
    val postWakeLightMinutes: Int = 10,
    val deviationScale: Float = 2f,
    val variabilityWeight: Float = 0.5f,
    val deepMovementPenalty: Float = 0.6f,
    val remMovementPenalty: Float = 0.4f,
    val deepPriorStart: Float = 0.5f,
    val deepPriorSlope: Float = -1.0f,
    val remPriorStart: Float = -0.6f,
    val remPriorSlope: Float = 1.2f,
    val deepThreshold: Float = 0.6f,
    val remThreshold: Float = 0.6f,
    val minSegmentMinutes: Int = 5,
    val bridgeMinutes: Int = 2,
    val deepShareMin: Float = 0.10f,
    val deepShareMax: Float = 0.25f,
    val remShareMin: Float = 0.15f,
    val remShareMax: Float = 0.30f,
    val adaptStep: Float = 0.1f,
    val adaptMaxIterations: Int = 8,
)

/**
 * The part of a stream one import may replace: every session inside
 * `[zoneStart, zoneEnd)`, a stretch that begins and ends in a long break, so
 * no sleep crosses its edges. [sessions] may be empty while the zone is not:
 * the zone is then known to hold no sleep any more.
 */
data class SettledEstimate(
    val zoneStart: Instant,
    val zoneEnd: Instant,
    val sessions: List<EstimatedSleepSession>,
)

/**
 * Estimates sleep from per-minute movement and heart rate, for watches that
 * leave staging to the vendor's servers. Sleep/wake follows actigraphy
 * practice (Cole-Kripke weights, Webster rescoring); stages follow heart
 * rate against its own slow baseline, its variability, and time-of-night
 * priors. Pure and deterministic: the same minutes always give the same
 * sessions.
 *
 * This is a heuristic without ground truth. Read the stages as "when heart
 * rate was low and steady versus high and variable", not as a sleep lab.
 */
object SleepStageEstimator {

    /**
     * How far a minute's sleep/wake label can depend on its neighbours:
     * Cole-Kripke reads four minutes back and two forward, and the three
     * Webster passes chain on top of that, to 148 back and 89 forward.
     */
    internal const val LABEL_REACH_MINUTES = 150

    /** The session in [minutes] with the most sleep, the earliest on a tie, or null when none qualifies. */
    @JvmStatic
    @JvmOverloads
    fun estimate(
        minutes: List<SleepMinute>,
        config: SleepEstimatorConfig = SleepEstimatorConfig(),
    ): EstimatedSleepSession? = estimateAll(minutes, config).maxByOrNull { it.sleepMinutes }

    /** Every session in [minutes], in time order: each stretch between long breaks that holds real sleep. */
    fun estimateAll(
        minutes: List<SleepMinute>,
        config: SleepEstimatorConfig = SleepEstimatorConfig(),
    ): List<EstimatedSleepSession> {
        val grid = MinuteGrid.of(minutes) ?: return emptyList()
        val labels = scoreSleepWake(grid, config)
        return findBlocks(labels, config).map { sessionOf(grid, labels, it, config) }
    }

    /**
     * The sessions of [minutes] that a whole-stream estimate would also find, with the zone
     * they settle. [minutes] is a stretch of a longer stored stream; [gapBefore] and [gapAfter]
     * say how many minute slots are missing between it and the nearest stored minute on each
     * side, null when there is none within a break's length. A missing stretch of
     * [SleepEstimatorConfig.maxBreakMinutes] or more is a break whatever lies beyond it, so the
     * stretch is padded with that much gap and its edge settles. Where the stream continues without a gap, the labels near the edge could
     * still change with the minutes beyond it, so [LABEL_REACH_MINUTES] at that edge are left
     * unsettled. Null when no break lies in the settled part.
     */
    fun estimateSettled(
        minutes: List<SleepMinute>,
        gapBefore: Int?,
        gapAfter: Int?,
        config: SleepEstimatorConfig = SleepEstimatorConfig(),
    ): SettledEstimate? {
        if (minutes.isEmpty()) return null
        val breakMinutes = config.maxBreakMinutes
        val padBefore = minOf(gapBefore ?: breakMinutes, breakMinutes)
        val padAfter = minOf(gapAfter ?: breakMinutes, breakMinutes)
        // The grid fills what lies between its first and last minute as gap, so one sentinel pads a side.
        val padded = minutes.toMutableList()
        if (padBefore > 0) padded += SleepMinute(minutes.minOf { it.time }.minusSeconds(60L * padBefore), SleepMinuteKind.UNMEASURABLE)
        if (padAfter > 0) padded += SleepMinute(minutes.maxOf { it.time }.plusSeconds(60L * padAfter), SleepMinuteKind.UNMEASURABLE)
        val grid = MinuteGrid.of(padded) ?: return null
        val labels = scoreSleepWake(grid, config)

        val settledFrom = if (gapBefore == 0) LABEL_REACH_MINUTES else 0
        val settledTo = if (gapAfter == 0) grid.size - LABEL_REACH_MINUTES else grid.size
        val breaks = findBreaks(labels, config)
        val settledBreaks = breaks.filter { brk ->
            // At least a whole break's worth of it lies in the settled part.
            maxOf(brk.start, settledFrom) + breakMinutes <= minOf(brk.end, settledTo)
        }
        if (settledBreaks.isEmpty()) return null
        val firstBreak = settledBreaks.first()
        val lastBreak = settledBreaks.last()
        val sessions = findBlocks(labels, breaks, config)
            .filter { it.onset >= firstBreak.end && it.end <= lastBreak.start }
            .map { sessionOf(grid, labels, it, config) }
        return SettledEstimate(
            zoneStart = grid.timeAt(maxOf(firstBreak.start, settledFrom)),
            zoneEnd = grid.timeAt(minOf(lastBreak.end, settledTo)),
            sessions = sessions,
        )
    }

    private fun sessionOf(grid: MinuteGrid, labels: CharArray, block: SleepBlock, config: SleepEstimatorConfig): EstimatedSleepSession {
        val staged = classifyStages(grid, labels, block.onset, block.end, config)
        val stages = staged.stages
        val spans = mutableListOf<EstimatedStageSpan>()
        var runStart = 0
        for (index in 1..stages.size) {
            if (index == stages.size || stages[index] != stages[runStart]) {
                spans.add(
                    EstimatedStageSpan(
                        start = grid.timeAt(block.onset + runStart),
                        end = grid.timeAt(block.onset + index),
                        stage = stages[runStart],
                    ),
                )
                runStart = index
            }
        }
        return EstimatedSleepSession(
            onset = grid.timeAt(block.onset),
            end = grid.timeAt(block.end),
            inProgress = grid.size - block.end < config.maxBreakMinutes,
            stages = spans,
            sleepMinutes = stages.count { it == EstimatedStage.LIGHT || it == EstimatedStage.DEEP || it == EstimatedStage.REM },
            awakeMinutes = stages.count { it == EstimatedStage.AWAKE },
            unknownMinutes = stages.count { it == EstimatedStage.UNKNOWN },
            deepThreshold = staged.deepThreshold,
            remThreshold = staged.remThreshold,
        )
    }

    /** Sleep (S), wake (W) or gap (G) per minute, after Webster rescoring. */
    internal fun scoreSleepWake(grid: MinuteGrid, config: SleepEstimatorConfig): CharArray {
        val activity = grid.activity(config)
        val labels = CharArray(grid.size)
        for (index in 0 until grid.size) {
            labels[index] = when (grid.kinds[index]) {
                SleepMinuteKind.UNMEASURABLE -> SleepLabels.GAP
                SleepMinuteKind.AWAKE -> SleepLabels.WAKE
                SleepMinuteKind.RAW -> {
                    var weighted = 0f
                    for (offset in COLE_KRIPKE_WEIGHTS.indices) {
                        val at = index + offset - COLE_KRIPKE_CENTER
                        if (at in 0 until grid.size) weighted += COLE_KRIPKE_WEIGHTS[offset] * activity[at]
                    }
                    if (weighted < config.sleepThreshold) SleepLabels.SLEEP else SleepLabels.WAKE
                }
            }
        }
        WebsterRescoring.apply(labels)
        return labels
    }

    /** The breaks: runs of not sleeping (wake or gap mixed) at least maxBreak long, as `[start, end)`. */
    internal fun findBreaks(labels: CharArray, config: SleepEstimatorConfig): List<LabelSpan> {
        val breaks = mutableListOf<LabelSpan>()
        var index = 0
        while (index < labels.size) {
            if (labels[index] == SleepLabels.SLEEP) {
                index++
                continue
            }
            val start = index
            while (index < labels.size && labels[index] != SleepLabels.SLEEP) index++
            if (index - start >= config.maxBreakMinutes) breaks.add(LabelSpan(start, index))
        }
        return breaks
    }

    /** Candidate sleep blocks: spans between long breaks that hold a real night. */
    internal fun findBlocks(labels: CharArray, config: SleepEstimatorConfig): List<SleepBlock> =
        findBlocks(labels, findBreaks(labels, config), config)

    private fun findBlocks(labels: CharArray, breaks: List<LabelSpan>, config: SleepEstimatorConfig): List<SleepBlock> {
        val blocks = mutableListOf<SleepBlock>()
        var spanStart = 0
        for (brk in breaks) {
            blockOf(labels, spanStart, brk.start, config)?.let(blocks::add)
            spanStart = brk.end
        }
        blockOf(labels, spanStart, labels.size, config)?.let(blocks::add)
        return blocks
    }

    private fun blockOf(labels: CharArray, from: Int, to: Int, config: SleepEstimatorConfig): SleepBlock? {
        if (to <= from) return null
        val runs = SleepLabels.runsOf(labels, from, to).filter { it.label == SleepLabels.SLEEP }
        val onsetRun = runs.firstOrNull { it.length >= config.onsetRunMinutes } ?: return null
        val endRun = runs.lastOrNull { it.length >= config.endRunMinutes } ?: return null
        val onset = onsetRun.start
        val end = endRun.start + endRun.length
        if (end - onset < config.minWindowMinutes) return null
        var sleepCount = 0
        for (index in onset until end) if (labels[index] == SleepLabels.SLEEP) sleepCount++
        if (sleepCount < config.minSleepMinutes) return null
        return SleepBlock(onset = onset, end = end, sleepCount = sleepCount)
    }

    /** Stages for the window `[onset, end)`, with the thresholds the sanity loop settled on. */
    internal fun classifyStages(
        grid: MinuteGrid,
        labels: CharArray,
        onset: Int,
        end: Int,
        config: SleepEstimatorConfig,
    ): StagedWindow {
        val length = end - onset
        val features = StageFeatures.of(grid, labels, onset, end, config)
        var deepThreshold = config.deepThreshold
        var remThreshold = config.remThreshold
        var stages = decide(features, labels, onset, length, deepThreshold, remThreshold, config)
        for (iteration in 0 until config.adaptMaxIterations) {
            val sleeping = stages.count { it == EstimatedStage.LIGHT || it == EstimatedStage.DEEP || it == EstimatedStage.REM }
            if (sleeping == 0) break
            val deepShare = stages.count { it == EstimatedStage.DEEP }.toFloat() / sleeping
            val remShare = stages.count { it == EstimatedStage.REM }.toFloat() / sleeping
            var moved = false
            if (deepShare < config.deepShareMin) {
                deepThreshold -= config.adaptStep
                moved = true
            } else if (deepShare > config.deepShareMax) {
                deepThreshold += config.adaptStep
                moved = true
            }
            if (remShare < config.remShareMin) {
                remThreshold -= config.adaptStep
                moved = true
            } else if (remShare > config.remShareMax) {
                remThreshold += config.adaptStep
                moved = true
            }
            if (!moved) break
            stages = decide(features, labels, onset, length, deepThreshold, remThreshold, config)
        }
        return StagedWindow(stages, deepThreshold, remThreshold)
    }

    private fun decide(
        features: StageFeatures,
        labels: CharArray,
        onset: Int,
        length: Int,
        deepThreshold: Float,
        remThreshold: Float,
        config: SleepEstimatorConfig,
    ): Array<EstimatedStage> {
        val stages = Array(length) { EstimatedStage.LIGHT }
        val variabilityScale = maxOf(features.variabilityMedian, 1f)
        for (index in 0 until length) {
            when (labels[onset + index]) {
                SleepLabels.GAP -> {
                    stages[index] = EstimatedStage.UNKNOWN
                    continue
                }
                SleepLabels.WAKE -> {
                    stages[index] = EstimatedStage.AWAKE
                    continue
                }
            }
            val position = index.toFloat() / length
            val deviation = features.deviation[index] / config.deviationScale
            val variability = (features.variability[index] - features.variabilityMedian) / variabilityScale
            val moving = features.movement[index] > 0f
            var deep = -deviation - config.variabilityWeight * variability +
                (config.deepPriorStart + config.deepPriorSlope * position)
            if (moving) deep -= config.deepMovementPenalty
            var rem = deviation + config.variabilityWeight * variability +
                (config.remPriorStart + config.remPriorSlope * position)
            if (moving) rem -= config.remMovementPenalty
            if (index < config.remLatencyMinutes) rem = Float.NEGATIVE_INFINITY
            if (features.sinceWake[index] < config.postWakeLightMinutes) deep = Float.NEGATIVE_INFINITY
            stages[index] = when {
                deep > deepThreshold && deep >= rem -> EstimatedStage.DEEP
                rem > remThreshold -> EstimatedStage.REM
                else -> EstimatedStage.LIGHT
            }
        }
        smooth(stages, config)
        return stages
    }

    /** Short deep/REM runs become light; a light sliver between two runs of one stage joins them. */
    private fun smooth(stages: Array<EstimatedStage>, config: SleepEstimatorConfig) {
        for (run in stageRuns(stages)) {
            if ((run.stage == EstimatedStage.DEEP || run.stage == EstimatedStage.REM) &&
                run.length < config.minSegmentMinutes
            ) {
                for (index in run.start until run.start + run.length) stages[index] = EstimatedStage.LIGHT
            }
        }
        val runs = stageRuns(stages)
        for ((position, run) in runs.withIndex()) {
            if (run.stage != EstimatedStage.LIGHT || run.length > config.bridgeMinutes) continue
            val before = runs.getOrNull(position - 1) ?: continue
            val after = runs.getOrNull(position + 1) ?: continue
            if (before.stage == after.stage && (before.stage == EstimatedStage.DEEP || before.stage == EstimatedStage.REM)) {
                for (index in run.start until run.start + run.length) stages[index] = before.stage
            }
        }
    }

    private class StageRun(val stage: EstimatedStage, val start: Int, val length: Int)

    private fun stageRuns(stages: Array<EstimatedStage>): List<StageRun> {
        val runs = mutableListOf<StageRun>()
        var start = 0
        for (index in 1..stages.size) {
            if (index == stages.size || stages[index] != stages[start]) {
                runs.add(StageRun(stages[start], start, index - start))
                start = index
            }
        }
        return runs
    }

    /** Cole-Kripke one-minute weights for t-4..t+2, scaled so the centre is 1. */
    private val COLE_KRIPKE_WEIGHTS = floatArrayOf(0.29f, 0.42f, 0.23f, 0.31f, 1.00f, 0.36f, 0.25f)
    private const val COLE_KRIPKE_CENTER = 4
}

/** A candidate night: window indices into the grid and how many minutes scored as sleep. */
internal class SleepBlock(val onset: Int, val end: Int, val sleepCount: Int)

/** Grid indices `[start, end)`. */
internal class LabelSpan(val start: Int, val end: Int)

internal class StagedWindow(
    val stages: Array<EstimatedStage>,
    val deepThreshold: Float,
    val remThreshold: Float,
)

/**
 * The input on a one-minute grid: sorted, duplicates resolved (last wins),
 * missing minutes filled as not worn so a partial file behaves like a gap.
 */
internal class MinuteGrid private constructor(
    private val startMinute: Long,
    val kinds: Array<SleepMinuteKind>,
    val movement: FloatArray,
    /** Beats per minute, or NaN when the minute carried none. */
    val heartRate: FloatArray,
) {
    val size: Int get() = kinds.size

    fun timeAt(index: Int): Instant = Instant.ofEpochSecond((startMinute + index) * SECONDS_PER_MINUTE)

    /** Movement for scoring: the watch's own awake verdict counts as strong movement. */
    fun activity(config: SleepEstimatorConfig): FloatArray = FloatArray(size) { index ->
        when (kinds[index]) {
            SleepMinuteKind.RAW -> movement[index]
            SleepMinuteKind.AWAKE -> config.awakeActivity
            SleepMinuteKind.UNMEASURABLE -> 0f
        }
    }

    companion object {
        private const val SECONDS_PER_MINUTE = 60L

        fun of(minutes: List<SleepMinute>): MinuteGrid? {
            if (minutes.isEmpty()) return null
            val byMinute = LinkedHashMap<Long, SleepMinute>()
            for (minute in minutes.sortedBy { it.time }) {
                byMinute[minute.time.epochSecond / SECONDS_PER_MINUTE] = minute
            }
            val first = byMinute.keys.min()
            val last = byMinute.keys.max()
            val size = (last - first + 1).toInt()
            val kinds = Array(size) { SleepMinuteKind.UNMEASURABLE }
            val movement = FloatArray(size)
            val heartRate = FloatArray(size) { Float.NaN }
            for ((slot, minute) in byMinute) {
                val index = (slot - first).toInt()
                kinds[index] = minute.kind
                if (minute.kind == SleepMinuteKind.RAW) {
                    movement[index] = maxOf(minute.movement, 0f).takeIf { it.isFinite() } ?: 0f
                    val bpm = minute.heartRate
                    if (bpm != null && bpm.isFinite() && bpm > 0f) heartRate[index] = bpm
                }
            }
            return MinuteGrid(first, kinds, movement, heartRate)
        }
    }
}

/** Per-minute stage features over one window. Indices are window-local. */
private class StageFeatures(
    val deviation: FloatArray,
    val variability: FloatArray,
    val variabilityMedian: Float,
    val movement: FloatArray,
    val sinceWake: IntArray,
) {
    companion object {
        private const val MIN_VARIABILITY_SAMPLES = 4

        fun of(
            grid: MinuteGrid,
            labels: CharArray,
            onset: Int,
            end: Int,
            config: SleepEstimatorConfig,
        ): StageFeatures {
            val length = end - onset
            val heartRate = FloatArray(length) { grid.heartRate[onset + it] }
            val sleeping = BooleanArray(length) { labels[onset + it] == SleepLabels.SLEEP }

            val smoothed = FloatArray(length) { index ->
                medianOf(heartRate, index - config.heartRateSmoothHalf, index + config.heartRateSmoothHalf)
            }
            // The baseline follows the night: heart rate keeps falling for an hour after onset.
            val deviation = FloatArray(length) { index ->
                val base = medianOf(smoothed, index - config.baselineHalf, index + config.baselineHalf, sleeping)
                if (smoothed[index].isNaN() || base.isNaN()) 0f else smoothed[index] - base
            }

            val rawVariability = FloatArray(length) { index ->
                standardDeviationOf(heartRate, index - config.deviationHalf, index + config.deviationHalf)
            }
            val sleepingVariability = FloatArray(length)
            var count = 0
            for (index in 0 until length) {
                if (sleeping[index] && !rawVariability[index].isNaN()) sleepingVariability[count++] = rawVariability[index]
            }
            val variabilityMedian = if (count == 0) 1f else medianOf(sleepingVariability, 0, count - 1)
            val variability = FloatArray(length) { index ->
                if (rawVariability[index].isNaN()) variabilityMedian else rawVariability[index]
            }

            val activity = grid.activity(config)
            val movement = FloatArray(length) { index ->
                var sum = 0f
                for (at in index - config.movementHalf..index + config.movementHalf) {
                    if (at in 0 until length) sum += activity[onset + at]
                }
                sum
            }

            // Minutes since the last wake or gap run of at least wakeRunMinutes ended.
            val sinceWake = IntArray(length) { Int.MAX_VALUE }
            var runLength = 0
            var lastWakeEnd = -1
            for (index in 0 until length) {
                if (sleeping[index]) {
                    if (runLength >= config.wakeRunMinutes) lastWakeEnd = index
                    runLength = 0
                    if (lastWakeEnd >= 0) sinceWake[index] = index - lastWakeEnd
                } else {
                    runLength++
                }
            }

            return StageFeatures(deviation, variability, variabilityMedian, movement, sinceWake)
        }

        /** Median of the finite values in `[from, to]`, optionally only where [mask] is true. NaN if none. */
        private fun medianOf(values: FloatArray, from: Int, to: Int, mask: BooleanArray? = null): Float {
            val low = maxOf(from, 0)
            val high = minOf(to, values.size - 1)
            if (high < low) return Float.NaN
            val picked = FloatArray(high - low + 1)
            var count = 0
            for (index in low..high) {
                if (values[index].isNaN()) continue
                if (mask != null && !mask[index]) continue
                picked[count++] = values[index]
            }
            if (count == 0) return Float.NaN
            picked.sort(0, count)
            return if (count % 2 == 1) picked[count / 2] else (picked[count / 2 - 1] + picked[count / 2]) / 2f
        }

        /** Population standard deviation over `[from, to]`, or NaN with fewer than four samples. */
        private fun standardDeviationOf(values: FloatArray, from: Int, to: Int): Float {
            val low = maxOf(from, 0)
            val high = minOf(to, values.size - 1)
            var count = 0
            var sum = 0.0
            for (index in low..high) {
                if (values[index].isNaN()) continue
                count++
                sum += values[index]
            }
            if (count < MIN_VARIABILITY_SAMPLES) return Float.NaN
            val mean = sum / count
            var squares = 0.0
            for (index in low..high) {
                if (values[index].isNaN()) continue
                val delta = values[index] - mean
                squares += delta * delta
            }
            return sqrt(squares / count).toFloat()
        }
    }
}
