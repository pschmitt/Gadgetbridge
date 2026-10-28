package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

// Guards the stateful sleep-session identity contract that fixes issue #6297: sleep session
// detection is stateless and can re-segment a night's start earlier as samples arrive. planSleep-
// Sessions must pin a clientRecordId on first sight and reuse it for any later overlapping
// detection, so the single HC record grows in place instead of orphaning the night. Only new-or-
// grown sessions are planned (written to HC); an unchanged re-detection keeps its row but is
// skipped. These tests exercise the pure decision core directly — no HealthConnectClient or DB mock
// needed.
class SleepSessionIdentityTest {

    // Deterministic id minter mirroring SleepSyncer's hour-bucketed scheme (without device parts).
    private val mintId: (Instant) -> String = { start ->
        "id-${start.epochSecond / 3600 * 3600}"
    }

    private fun det(start: String, end: String) = DetectedSleepSession(Instant.parse(start), Instant.parse(end))

    private fun plan(existingRows: List<SleepSessionRow>, vararg detected: DetectedSleepSession) =
        SleepSyncer.planSleepSessions(existingRows, detected.toList(), mintId)

    @Test
    fun freshSession_mintsId_plansRecord() {
        val result = plan(emptyList(), det("2026-06-15T07:30:00Z", "2026-06-15T07:30:01Z"))

        assertEquals(1, result.planned.size)
        assertEquals(1, result.rows.size)
        val row = result.rows[0]
        assertEquals(mintId(Instant.parse("2026-06-15T07:30:00Z")), row.clientRecordId)
        assertEquals(row.clientRecordId, result.planned[0].clientRecordId)
    }

    // ★ Regression guard for #6297: the night re-segments earlier across passes, must keep its id.
    @Test
    fun backwardGrowth_reusesFrozenId_growsRecord() {
        // Pass A: a single-sample fragment is all the segmenter can see yet.
        val passA = plan(emptyList(), det("2026-06-15T01:02:00Z", "2026-06-15T01:02:01Z"))
        val frozenId = passA.rows[0].clientRecordId

        // Pass B (seconds later): the fragment has grown into the real night starting 23:08.
        val passB = plan(passA.rows, det("2026-06-14T23:08:00Z", "2026-06-15T07:25:00Z"))

        assertEquals("one record, not a new orphan", 1, passB.rows.size)
        assertEquals("id frozen from first sight", frozenId, passB.rows[0].clientRecordId)
        assertEquals(Instant.parse("2026-06-14T23:08:00Z"), passB.rows[0].startTime)
        assertEquals(Instant.parse("2026-06-15T07:25:00Z"), passB.rows[0].endTime)
        assertEquals("grown session is re-planned", 1, passB.planned.size)
        assertEquals(frozenId, passB.planned[0].clientRecordId)
    }

    @Test
    fun freshSession_advancesCursorToEnd() {
        val end = "2026-06-15T01:00:00Z"
        val result = plan(emptyList(), det("2026-06-14T23:08:00Z", end))
        // Cursor derivation lives in sync(); here we assert the planned span the cursor is taken from.
        assertEquals(Instant.parse(end), result.planned.maxOf { it.end })
    }

    @Test
    fun fragmentOnStartEdge_matchesGrownNight_noOrphan() {
        // First detection is a single-sample fragment sitting exactly on the night's eventual start
        // edge (23:08Z). When the full night 23:08Z->07:25Z is detected next, it must reuse the
        // frozen id, not spawn a second record. (Inclusive overlap; strict would re-orphan here.)
        val frag = plan(emptyList(), det("2026-06-14T23:08:00Z", "2026-06-14T23:08:01Z"))
        val night = plan(frag.rows, det("2026-06-14T23:08:00Z", "2026-06-15T07:25:00Z"))

        assertEquals("one record, not an orphan", 1, night.rows.size)
        assertEquals(frag.rows[0].clientRecordId, night.rows[0].clientRecordId)
        assertEquals(Instant.parse("2026-06-15T07:25:00Z"), night.rows[0].endTime)
    }

    @Test
    fun gapSeparatedSessions_stayDistinct() {
        // The segmenter only splits across a wake gap > 1h, so distinct sessions never touch.
        // A nap and a later main sleep with a clear gap must remain two records.
        val first = plan(emptyList(), det("2026-06-14T13:00:00Z", "2026-06-14T14:00:00Z"))
        val second = plan(first.rows, det("2026-06-14T23:08:00Z", "2026-06-15T07:25:00Z"))

        assertEquals("gap-separated sessions must not merge", 2, second.rows.size)
        assertEquals(2, second.rows.map { it.clientRecordId }.toSet().size)
    }

    @Test
    fun multiSessionNight_napAndMainSleep_distinctIds() {
        val result = plan(
            emptyList(),
            det("2026-06-14T13:00:00Z", "2026-06-14T13:30:00Z"), // afternoon nap
            det("2026-06-14T23:00:00Z", "2026-06-15T07:00:00Z")  // main sleep
        )
        assertEquals(2, result.rows.size)
        assertEquals(2, result.rows.map { it.clientRecordId }.toSet().size)
        assertEquals(2, result.planned.size)
    }

    // ★ Skip-if-unchanged: the 24h look-back re-scans an already-synced night every run; an unchanged
    // re-detection must keep its row but not be re-written to HC.
    @Test
    fun unchangedSession_keepsRow_notReplanned() {
        val first = plan(emptyList(), det("2026-06-14T23:00:00Z", "2026-06-15T07:00:00Z"))
        val second = plan(first.rows, det("2026-06-14T23:00:00Z", "2026-06-15T07:00:00Z"))

        assertEquals(1, second.rows.size)
        assertEquals(first.rows[0].clientRecordId, second.rows[0].clientRecordId)
        assertTrue("unchanged span must not be re-planned", second.planned.isEmpty())
    }

    @Test
    fun reDetectedWithinLookback_noNewId() {
        val first = plan(emptyList(), det("2026-06-14T22:00:00Z", "2026-06-15T00:00:00Z"))
        val again = plan(first.rows, det("2026-06-14T22:00:00Z", "2026-06-15T00:00:00Z"))

        assertEquals(1, again.rows.size)
        assertEquals(first.rows[0].clientRecordId, again.rows[0].clientRecordId)
    }

    @Test
    fun prune_dropsRowsBeforeHorizon() {
        val old = SleepSessionRow("old", Instant.parse("2026-06-10T00:00:00Z"), Instant.parse("2026-06-10T06:00:00Z"))
        val recent = SleepSessionRow("recent", Instant.parse("2026-06-14T23:00:00Z"), Instant.parse("2026-06-15T06:00:00Z"))
        val pruneBefore = Instant.parse("2026-06-14T00:00:00Z")

        val keptIds = SleepSyncer.pruneSleepRows(listOf(old, recent), pruneBefore)
            .map { it.clientRecordId }.toSet()

        assertFalse("old row pruned", keptIds.contains("old"))
        assertTrue("recent row kept", keptIds.contains("recent"))
    }

    // ── Registry round-trip: sub-second detection spans defeat the skip-unchanged guard ─────────

    // The registry persists spans at epochSecond precision (loadSleepRows/persistSleepRows).
    // buildCandidate derives the detected span from the provider's epoch-millis session bounds, so
    // a provider emitting sub-second boundaries makes the re-detected span differ from the stored
    // row after reload -> changed=true -> the night is rewritten to HC every sync. This guard
    // pins the round-trip property the providers must hold (issue #6453, Fix A).
    private fun persistLoad(instant: Instant) = Instant.ofEpochSecond(instant.epochSecond)

    private fun sessionSpan(session: SleepSession) =
        DetectedSleepSession(
            Instant.ofEpochMilli(session.startTime),
            Instant.ofEpochMilli(session.endTime)
        )

    @Test
    fun wholeSecondSessionSpan_survivesRegistryRoundTrip_notReplanned() {
        val session = SleepSession(listOf(
            SleepStage(ActivityKind.LIGHT_SLEEP, 1_700_000_000_000L, 1_700_003_600_000L),
            SleepStage(ActivityKind.DEEP_SLEEP, 1_700_003_600_000L, 1_700_007_200_000L)
        ))
        val stored = SleepSessionRow(
            "frozen-id",
            persistLoad(Instant.ofEpochMilli(session.startTime)),
            persistLoad(Instant.ofEpochMilli(session.endTime))
        )

        val result = plan(listOf(stored), sessionSpan(session))

        assertTrue("unchanged whole-second re-detection must not be rewritten to HC", result.planned.isEmpty())
    }

    @Test
    fun subSecondSessionSpan_registryRoundTrip_replansEverySync() {
        // A provider whose bounds carry a sub-second component (e.g. last stage end at +500ms)
        // truncates on reload; the re-detected span no longer equals the stored row, so the
        // skip-unchanged guard is defeated and the night is re-planned every sync.
        val session = SleepSession(listOf(
            SleepStage(ActivityKind.LIGHT_SLEEP, 1_700_000_000_000L, 1_700_003_600_500L)
        ))
        val stored = SleepSessionRow(
            "frozen-id",
            persistLoad(Instant.ofEpochMilli(session.startTime)),
            persistLoad(Instant.ofEpochMilli(session.endTime))
        )

        val result = plan(listOf(stored), sessionSpan(session))

        assertEquals("sub-second end truncates on reload and defeats the skip-unchanged guard", 1, result.planned.size)
    }
}
