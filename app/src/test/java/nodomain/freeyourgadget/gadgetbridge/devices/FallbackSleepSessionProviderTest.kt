package nodomain.freeyourgadget.gadgetbridge.devices

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Test

class FallbackSleepSessionProviderTest {
    @Test
    fun fallbackSessionsFillTheTimeWithoutPrimarySessions() {
        val night = session(10_000, 30_000)
        val nap = session(2_000, 4_000)
        val provider = FallbackSleepSessionProvider(
            FixedSleepSessionProvider(night),
            FixedSleepSessionProvider(session(9_000, 31_000), nap)
        )

        assertEquals(listOf(nap, night), provider.getSleepSessions(0, 100_000))
    }

    @Test
    fun allFallbackSessionsWhenThereAreNoPrimarySessions() {
        val nap = session(2_000, 4_000)
        val provider = FallbackSleepSessionProvider(FixedSleepSessionProvider(), FixedSleepSessionProvider(nap))

        assertEquals(listOf(nap), provider.getSleepSessions(0, 100_000))
    }

    @Test
    fun primarySessionBeforeTheRangeHidesOverlappingFallbackSession() {
        val provider = FallbackSleepSessionProvider(
            FixedSleepSessionProvider(session(10_000, 30_000)),
            FixedSleepSessionProvider(session(20_000, 25_000))
        )

        assertEquals(emptyList<SleepSession>(), provider.getSleepSessions(15_000, 40_000))
    }

    private fun session(startSeconds: Long, endSeconds: Long) =
        SleepSession(listOf(SleepStage(ActivityKind.LIGHT_SLEEP, startSeconds * 1000L, endSeconds * 1000L)))

    private class FixedSleepSessionProvider(vararg sessions: SleepSession) : SleepSessionProvider {
        private val sessions = sessions.toList()

        override fun getSleepSessions(tsFrom: Int, tsTo: Int) =
            sessions.filter { (it.startTime / 1000L) in tsFrom..<tsTo }
    }
}
