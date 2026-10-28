package nodomain.freeyourgadget.gadgetbridge.devices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.BaseMockSample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage;

public class SleepSessionSegmenterTest {
    @Test
    public void emptySamples_noSessions() {
        assertEquals(0, SleepSessionSegmenter.segment(Collections.emptyList()).size());
    }

    @Test
    public void singleSleepType_oneStage_withFixedTrailingPad() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(300, ActivityKind.LIGHT_SLEEP),
                sleep(600, ActivityKind.LIGHT_SLEEP)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(1, sessions.size());

        SleepSession session = sessions.get(0);
        assertEquals(1, session.getStages().size());
        // Trailing pad is a fixed 60s, not derived from the 300s sampling interval used here.
        assertEquals(0L, session.getStartTime());
        assertEquals(660_000L, session.getEndTime());
        assertEquals(660, session.getLightSleepDuration());
    }

    @Test
    public void mixedSleepTypes_threeStages() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(300, ActivityKind.DEEP_SLEEP),
                sleep(600, ActivityKind.REM_SLEEP),
                sleep(900, ActivityKind.LIGHT_SLEEP)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(1, sessions.size());

        SleepSession session = sessions.get(0);
        // The first sample's own kind (LIGHT) never becomes a stage; only the delta *into* a kind
        // change is attributed, so the session opens directly on DEEP.
        assertStages(session,
                stage(ActivityKind.DEEP_SLEEP, 0, 300),
                stage(ActivityKind.REM_SLEEP, 300, 600),
                stage(ActivityKind.LIGHT_SLEEP, 600, 960)
        );
        assertEquals(300, session.getDeepSleepDuration());
        assertEquals(300, session.getRemSleepDuration());
        assertEquals(360, session.getLightSleepDuration());
    }

    @Test
    public void sessionTooShort_noSessions() {
        List<ActivitySample> tooShort = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(240, ActivityKind.LIGHT_SLEEP)
        );
        assertEquals(0, SleepSessionSegmenter.segment(tooShort).size());

        // One second longer clears the (span + trailing pad) > MIN_SESSION_LENGTH_SECONDS threshold.
        List<ActivitySample> justLongEnough = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(241, ActivityKind.LIGHT_SLEEP)
        );
        assertEquals(1, SleepSessionSegmenter.segment(justLongEnough).size());
    }

    @Test
    public void gapBridgedByResumingSleep_foldsIntoAwakeStage() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(60, ActivityKind.DEEP_SLEEP),
                idle(120),
                idle(180),
                sleep(240, ActivityKind.LIGHT_SLEEP),
                sleep(300, ActivityKind.DEEP_SLEEP)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(1, sessions.size());

        SleepSession session = sessions.get(0);
        assertStages(session,
                stage(ActivityKind.DEEP_SLEEP, 0, 60),
                stage(ActivityKind.AWAKE_SLEEP, 60, 180),
                stage(ActivityKind.LIGHT_SLEEP, 180, 240),
                stage(ActivityKind.DEEP_SLEEP, 240, 360)
        );
        assertEquals(180, session.getDeepSleepDuration());
        assertEquals(120, session.getAwakeSleepDuration());
        assertEquals(60, session.getLightSleepDuration());
    }

    @Test
    public void gapClosedByActivity_sessionExcludesTheActiveSample() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.DEEP_SLEEP),
                sleep(600, ActivityKind.DEEP_SLEEP),
                idle(1200),
                activity(1800, 100)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(1, sessions.size());

        SleepSession session = sessions.get(0);
        assertStages(session, stage(ActivityKind.DEEP_SLEEP, 0, 660));
        assertEquals(660, session.getDeepSleepDuration());
    }

    @Test
    public void gapClosedByTimeout_noStepsNeeded() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.DEEP_SLEEP),
                sleep(1800, ActivityKind.DEEP_SLEEP),
                // 3660s after the last sleep sample - past the 1h wake-phase limit on its own.
                idle(5460)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(1, sessions.size());

        SleepSession session = sessions.get(0);
        assertStages(session, stage(ActivityKind.DEEP_SLEEP, 0, 1860));
    }

    @Test
    public void deviceReportedAwakeSleep_isAttributedLikeAnySleepStage() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.DEEP_SLEEP),
                sleep(600, ActivityKind.DEEP_SLEEP),
                sleep(1200, ActivityKind.AWAKE_SLEEP),
                sleep(1800, ActivityKind.AWAKE_SLEEP),
                sleep(2400, ActivityKind.LIGHT_SLEEP)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(1, sessions.size());

        SleepSession session = sessions.get(0);
        assertStages(session,
                stage(ActivityKind.DEEP_SLEEP, 0, 600),
                stage(ActivityKind.AWAKE_SLEEP, 600, 1800),
                stage(ActivityKind.LIGHT_SLEEP, 1800, 2460)
        );
        assertEquals(600, session.getDeepSleepDuration());
        assertEquals(1200, session.getAwakeSleepDuration());
    }

    @Test
    public void sleepAny_countsTowardTotalButNotLightDeepRem() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(600, ActivityKind.SLEEP_ANY),
                sleep(1200, ActivityKind.LIGHT_SLEEP)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(1, sessions.size());

        SleepSession session = sessions.get(0);
        assertStages(session,
                stage(ActivityKind.SLEEP_ANY, 0, 600),
                stage(ActivityKind.LIGHT_SLEEP, 600, 1260)
        );
        assertEquals(660, session.getLightSleepDuration());
        assertEquals(0, session.getDeepSleepDuration());
        assertEquals(1260, session.getTotalSleepDuration());
    }

    @Test
    public void multipleSessionsSeparatedByActivity_twoSessions() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(600, ActivityKind.DEEP_SLEEP),
                activity(601, 200),
                sleep(602, ActivityKind.LIGHT_SLEEP),
                sleep(1202, ActivityKind.REM_SLEEP)
        );
        List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);
        assertEquals(2, sessions.size());

        assertStages(sessions.get(0), stage(ActivityKind.DEEP_SLEEP, 0, 660));
        assertStages(sessions.get(1), stage(ActivityKind.REM_SLEEP, 602, 1262));
    }

    @Test
    public void stagesAreContiguous() {
        List<ActivitySample> samples = Arrays.asList(
                sleep(0, ActivityKind.LIGHT_SLEEP),
                sleep(300, ActivityKind.DEEP_SLEEP),
                sleep(600, ActivityKind.REM_SLEEP),
                sleep(900, ActivityKind.LIGHT_SLEEP)
        );
        List<SleepStage> stages = SleepSessionSegmenter.segment(samples).get(0).getStages();
        for (int i = 1; i < stages.size(); i++) {
            assertEquals("stage " + (i - 1) + " end must equal stage " + i + " start",
                    stages.get(i - 1).getEndTime(), stages.get(i).getStartTime());
        }
        // ... and every session's own bounds are never null/degenerate - start strictly precedes end.
        assertTrue(stages.get(0).getStartTime() < stages.get(stages.size() - 1).getEndTime());
    }

    private static void assertStages(SleepSession session, SleepStage... expected) {
        List<SleepStage> actual = session.getStages();
        assertEquals("stage count", expected.length, actual.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("stage " + i + " kind", expected[i].getKind(), actual.get(i).getKind());
            assertEquals("stage " + i + " start", expected[i].getStartTime(), actual.get(i).getStartTime());
            assertEquals("stage " + i + " end", expected[i].getEndTime(), actual.get(i).getEndTime());
        }
    }

    private static SleepStage stage(ActivityKind kind, long startSeconds, long endSeconds) {
        return new SleepStage(kind, startSeconds * 1000L, endSeconds * 1000L);
    }

    private static MockSample sleep(int timestamp, ActivityKind kind) {
        return new MockSample(timestamp, kind, 0);
    }

    private static MockSample idle(int timestamp) {
        return new MockSample(timestamp, ActivityKind.ACTIVITY, 0);
    }

    private static MockSample activity(int timestamp, int steps) {
        return new MockSample(timestamp, ActivityKind.ACTIVITY, steps);
    }

    private static class MockSample extends BaseMockSample {
        MockSample(int timestamp, ActivityKind kind, int steps) {
            super(timestamp, kind, steps);
        }
    }
}
