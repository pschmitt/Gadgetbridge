package nodomain.freeyourgadget.gadgetbridge.devices.huawei

import nodomain.freeyourgadget.gadgetbridge.devices.HuaweiSleepStageSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.SleepSessionProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.HuaweiSleepStatsSample
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage

class HuaweiSleepSessionProvider(
    private val device: GBDevice,
    private val session: DaoSession
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val sleepSessions = ArrayList<SleepSession>()

        val fromMillis: Long = (tsFrom - LOOK_BACK_SECONDS) * 1000L
        val toMillis: Long = tsTo * 1000L

        val sleepStatsSampleProvider = HuaweiSleepStatsSampleProvider(device, session)
        val sleepStatsSamples = sleepStatsSampleProvider.getSleepSamples(fromMillis, toMillis)
        if (sleepStatsSamples.isEmpty()) {
            return emptyList()
        }

        val sleepStagesSampleProvider = HuaweiSleepStageSampleProvider(device, session)

        // Some watches report partial records inside a full night. Process the longest
        // records first, so that a record inside an already accepted one can be dropped.
        val acceptedStatsSamples = ArrayList<HuaweiSleepStatsSample>()
        for (statsSample in sleepStatsSamples.sortedByDescending { it.wakeupTime - it.timestamp }) {
            if (acceptedStatsSamples.any { it.timestamp <= statsSample.timestamp && statsSample.wakeupTime <= it.wakeupTime }) {
                continue
            }
            acceptedStatsSamples.add(statsSample)

            val startMillis = statsSample.timestamp
            val endMillis = statsSample.wakeupTime
            val stageSamples = sleepStagesSampleProvider.getAllSamples(startMillis, endMillis)

            val unsanitizedStages = stageSamples.map {
                SleepStage(
                    sleepStageToActivityKind(it.stage),
                    it.timestamp,
                    -1L
                )
            }

            SleepSessionProvider.sanitizeStages(
                unsanitizedStages,
                startMillis,
                endMillis
            )?.let { sleepSessions.add(it) }
        }

        return sleepSessions
            .filter { (it.startTime / 1000L) in tsFrom..<tsTo }
            .sortedBy { it.startTime }
    }

    companion object {
        private const val LOOK_BACK_SECONDS: Int = 12 * 60 * 60

        @JvmStatic
        fun sleepStageToActivityKind(stage: Int): ActivityKind {
            return when (stage) {
                1 -> ActivityKind.LIGHT_SLEEP
                2 -> ActivityKind.REM_SLEEP
                3 -> ActivityKind.DEEP_SLEEP
                4 -> ActivityKind.AWAKE_SLEEP
                5 -> ActivityKind.LIGHT_SLEEP // Nap
                else -> ActivityKind.UNKNOWN
            }
        }
    }
}
