package nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.samples

import nodomain.freeyourgadget.gadgetbridge.devices.ColmiSleepSessionSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.ColmiSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider
import nodomain.freeyourgadget.gadgetbridge.devices.yawell.ring.YawellRingConstants
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage

class ColmiSleepSessionProvider(
    private val device: GBDevice,
    private val session: DaoSession
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val sleepSessions = ArrayList<SleepSession>()

        val fromMillis: Long = (tsFrom - LOOK_BACK_SECONDS) * 1000L
        val toMillis: Long = tsTo * 1000L

        val sleepSessionSampleProvider = ColmiSleepSessionSampleProvider(device, session)
        val sleepSessionSamples = sleepSessionSampleProvider.getAllSamples(fromMillis, toMillis)
        if (sleepSessionSamples.isEmpty()) {
            return emptyList()
        }

        val sleepStagesSampleProvider = ColmiSleepStageSampleProvider(device, session)

        for (sessionSample in sleepSessionSamples) {
            val wakeupTime = sessionSample.wakeupTime ?: continue
            val stageSamples = sleepStagesSampleProvider.getAllSamples(sessionSample.timestamp, wakeupTime)

            // Stage durations are stored in minutes
            val unsanitizedStages = stageSamples.map {
                SleepStage(
                    sleepStageToActivityKind(it.stage),
                    it.timestamp,
                    it.timestamp + it.duration * 60 * 1000L
                )
            }

            SleepSessionProvider.sanitizeStages(
                unsanitizedStages,
                sessionSample.timestamp,
                wakeupTime
            )?.let { sleepSessions.add(it) }
        }

        return sleepSessions
            .filter { (it.startTime / 1000L) in tsFrom..<tsTo }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60

        fun sleepStageToActivityKind(sleepStage: Int): ActivityKind {
            return when (sleepStage.toByte()) {
                YawellRingConstants.SLEEP_TYPE_LIGHT -> ActivityKind.LIGHT_SLEEP
                YawellRingConstants.SLEEP_TYPE_DEEP -> ActivityKind.DEEP_SLEEP
                YawellRingConstants.SLEEP_TYPE_REM -> ActivityKind.REM_SLEEP
                YawellRingConstants.SLEEP_TYPE_AWAKE -> ActivityKind.AWAKE_SLEEP
                else -> ActivityKind.UNKNOWN
            }
        }
    }
}
