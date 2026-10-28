package nodomain.freeyourgadget.gadgetbridge.devices.cmfwatchpro.samples

import nodomain.freeyourgadget.gadgetbridge.devices.CmfSleepSessionSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.CmfSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage

class CmfSleepSessionProvider(
    private val device: GBDevice,
    private val session: DaoSession
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val sleepSessions = ArrayList<SleepSession>()

        val fromMillis: Long = (tsFrom - LOOK_BACK_SECONDS) * 1000L
        val toMillis: Long = tsTo * 1000L

        // Get sleep / start events
        val sleepSessionSampleProvider = CmfSleepSessionSampleProvider(device, session)
        val sleepSessionSamples = sleepSessionSampleProvider.getAllSamples(fromMillis, toMillis)
        if (sleepSessionSamples.isEmpty()) {
            return emptyList()
        }

        val sleepStagesSampleProvider = CmfSleepStageSampleProvider(device, session)

        for (sessionSample in sleepSessionSamples) {
            if (sessionSample.wakeupTime == null) {
                // Should never happen?
                continue
            }

            val stageSamples = sleepStagesSampleProvider.getAllSamples(
                sessionSample.timestamp,
                sessionSample.wakeupTime!!
            )

            val unsanitizedStages = stageSamples.map {
                SleepStage(
                    sleepStageToActivityKind(it.stage),
                    it.timestamp,
                    it.timestamp + it.duration * 1000L
                )
            }

            SleepSessionProvider.sanitizeStages(
                unsanitizedStages,
                sessionSample.timestamp,
                sessionSample.wakeupTime!!
            )?.let { sleepSessions.add(it) }
        }

        return sleepSessions
            .filter { (it.startTime / 1000L) in tsFrom..<tsTo }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60

        fun sleepStageToActivityKind(sleepStage: Int): ActivityKind {
            return when (sleepStage) {
                1 -> ActivityKind.DEEP_SLEEP
                2 -> ActivityKind.LIGHT_SLEEP
                3 -> ActivityKind.REM_SLEEP
                else -> ActivityKind.UNKNOWN
            }
        }
    }
}
