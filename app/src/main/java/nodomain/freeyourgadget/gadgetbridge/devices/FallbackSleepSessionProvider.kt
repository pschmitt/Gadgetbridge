package nodomain.freeyourgadget.gadgetbridge.devices

import nodomain.freeyourgadget.gadgetbridge.model.SleepSession

class FallbackSleepSessionProvider(
    private val primary: SleepSessionProvider,
    private val fallback: SleepSessionProvider
) : SleepSessionProvider {
    override fun getSleepSessions(tsFrom: Int, tsTo: Int): List<SleepSession> {
        val primarySessions = primary.getSleepSessions(tsFrom - OVERLAP_MARGIN_SECONDS, tsTo + OVERLAP_MARGIN_SECONDS)

        val fallbackSessions = fallback.getSleepSessions(tsFrom, tsTo).filter { candidate ->
            primarySessions.none { it.startTime < candidate.endTime && candidate.startTime < it.endTime }
        }

        return (primarySessions.filter { (it.startTime / 1000L) in tsFrom..<tsTo } + fallbackSessions)
            .sortedBy { it.startTime }
    }

    companion object {
        private const val OVERLAP_MARGIN_SECONDS: Int = 24 * 60 * 60
    }
}
