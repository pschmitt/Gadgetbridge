package nodomain.freeyourgadget.gadgetbridge.devices

import nodomain.freeyourgadget.gadgetbridge.entities.AbstractTimeSample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage

/**
 * Provides whole sleep sessions for a device, for a given time range.
 */
interface SleepSessionProvider {
    /**
     * Returns all sleep sessions that START within [tsFrom, tsTo) (epoch seconds, tsTo exclusive).
     * A session that start inside the range but ends after tsTo should be returned in full.
     */
    fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession>

    /**
     * Returns all sleep sessions that END within (tsFrom, tsTo] (epoch seconds), in full.
     */
    fun getSleepSessionsEndingIn(tsFrom: Int, tsTo: Int): List<SleepSession> {
        return getSleepSessions(tsFrom - MAX_SESSION_SECONDS, tsTo)
            .filter { (it.endTime / 1000L) in (tsFrom + 1L)..tsTo }
    }

    companion object {
        private const val MAX_SESSION_SECONDS: Int = 24 * 60 * 60

        /**
         * Sanitizes a list of sleep stages, into a known sleep session start /end times. Returns null
         * if the session ends at or before its start.
         * <p>
         * Devices sometimes report overlapping/duplicate stage samples (e.g. a stage re-sent
         * with a slightly different start after a later sync). This function will:
         * - Honor the start time of each session
         * - Clip each stage to end no later than next one starts
         * - Drop stages that fully overlap
         * - Merge stages of the same kind into the previous on when they end up touching
         * - Ensure the stages snap to the session bounds, by either cutting them off or padding them with light sleep.
         *
         * @implNote the endTime in the provided stages is ignored, and snapped to the start time of the next stage.
         */
        fun sanitizeStages(
            stages: List<SleepStage>,
            startTime: Long,
            endTime: Long
        ): SleepSession? {
            if (endTime <= startTime) {
                return null
            }

            val sanitized = ArrayList<SleepStage>(stages.size)

            val firstStageTimestamp = stages.firstOrNull()?.startTime
            if (firstStageTimestamp != null && firstStageTimestamp > startTime) {
                // The stage samples start later than the reported bedtime (e.g. fall-asleep time
                // vs bedtime) - fill the gap as light sleep.
                sanitized.add(SleepStage(ActivityKind.LIGHT_SLEEP, startTime, firstStageTimestamp))
            }
            for ((index, sample) in stages.withIndex()) {
                val end = if (index < stages.lastIndex)
                    stages[index + 1].startTime
                else
                    endTime
                val start = maxOf(sample.startTime, sanitized.lastOrNull()?.endTime ?: sample.startTime)
                if (start >= end) {
                    continue
                }

                val kind = sample.kind
                val lastStage = sanitized.lastOrNull()
                if (lastStage != null && lastStage.kind == kind && lastStage.endTime == start) {
                    sanitized[sanitized.size - 1] = SleepStage(kind, lastStage.startTime, end)
                } else {
                    sanitized.add(SleepStage(kind, start, end))
                }
            }
            if (sanitized.isEmpty()) {
                // No stages sent by the watch, or none usable - add a single stage for the entire duration
                sanitized.add(SleepStage(ActivityKind.LIGHT_SLEEP, startTime, endTime))
            }

            return SleepSession(sanitized)
        }

        /**
         * Group arbitrary sleep stages into sleep sessions. Effectively, creates a new session when there's
         * a time gap in-between stages.
         */
        fun groupIntoSessions(allStages: List<SleepStage>): List<SleepSession> {
            val sleepSessions = ArrayList<SleepSession>()
            var stages = ArrayList<SleepStage>()

            for (sample in allStages.sortedBy { it.startTime }) {
                if (sample.kind == ActivityKind.UNKNOWN) {
                    continue
                }

                val end = sample.startTime + sample.duration * 1000L
                val lastEnd = stages.lastOrNull()?.endTime
                if (lastEnd != null && sample.startTime > lastEnd) {
                    // The previous session ended - wrap it up and start a new one
                    if (stages.isNotEmpty()) {
                        sleepSessions.add(SleepSession(stages))
                    }
                    stages = ArrayList()
                }

                val start = maxOf(sample.startTime, stages.lastOrNull()?.endTime ?: sample.startTime)
                if (start >= end) {
                    continue
                }

                val lastStage = stages.lastOrNull()
                if (lastStage != null && lastStage.kind == sample.kind && lastStage.endTime == start) {
                    stages[stages.size - 1] = SleepStage(sample.kind, lastStage.startTime, end)
                } else {
                    stages.add(SleepStage(sample.kind, start, end))
                }
            }

            // Add the last session
            if (stages.isNotEmpty()) {
                sleepSessions.add(SleepSession(stages))
            }

            return sleepSessions
        }
    }
}
