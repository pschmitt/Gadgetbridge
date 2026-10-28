package nodomain.freeyourgadget.gadgetbridge.devices.huami

import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage

class HuamiSleepSessionProvider(
    private val device: GBDevice,
    private val session: DaoSession
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val sleepSessions = ArrayList<SleepSession>()

        val fromMillis: Long = (tsFrom - LOOK_BACK_SECONDS) * 1000L
        // The record timestamp is its report time, which may be on the following day.
        val toMillis: Long = (tsTo.toLong() + LOOK_AHEAD_SECONDS) * 1000L

        val sleepSessionSampleProvider = HuamiSleepSessionSampleProvider(device, session)
        val sleepSessionSamples = sleepSessionSampleProvider.getAllSamples(fromMillis, toMillis)

        for (sessionSample in sleepSessionSamples) {
            val rawSession = HuamiSleepSessionSampleProvider.SleepSession(sessionSample.data)
            if (rawSession.stages.isEmpty()) {
                continue
            }

            val stages = ArrayList<SleepStage>(rawSession.stages.size)
            for (stage in rawSession.stages) {
                val start = (rawSession.timestampMidnight - 24 * 3600 + stage.start * 60) * 1000L
                // Huami includes both endpoint minutes in each native stage.
                val end = (rawSession.timestampMidnight - 24 * 3600 + (stage.end + 1) * 60) * 1000L
                stages.add(SleepStage(stage.asActivityKind(), start, end))
            }

            val candidate = SleepSession(stages)
            // Reports are ordered by report time. Revised sleep bounds can shift, so identify
            // revisions by overlap before filtering the requested window, not by exact start.
            sleepSessions.removeAll {
                it.startTime < candidate.endTime && candidate.startTime < it.endTime
            }
            sleepSessions.add(candidate)
        }

        return sleepSessions
            .filter { (it.startTime / 1000L) in tsFrom..<tsTo }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60
        private const val LOOK_AHEAD_SECONDS: Int = 48 * 60 * 60
    }
}
