package nodomain.freeyourgadget.gadgetbridge.devices.cmfwatchpro.samples;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.CmfSleepSessionSample;
import nodomain.freeyourgadget.gadgetbridge.entities.CmfSleepStageSample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import static org.junit.Assert.assertEquals;

// Guards the SleepSessionProvider contract for the Cmf native provider: sessions are returned
// whole and filtered by start, so the HC sleep sync never sees a clipped tail (issue #6453).
public class CmfSleepSessionProviderTest extends TestBase {
    private GBDevice dummyGBDevice;

    // Whole-second night: 8h, 2023-11-14T22:13:20Z. Cmf timestamps are stored in ms, stage
    // durations in seconds.
    private static final long T0 = 1_700_000_000_000L;
    private static final long T1 = T0 + 8 * 60 * 60 * 1000L;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        dummyGBDevice = createDummyGDevice("00:00:00:00:10");
        DBHelper.getDevice(dummyGBDevice, daoSession);
    }

    private CmfSleepSessionProvider provider() {
        return new CmfSleepSessionProvider(dummyGBDevice, daoSession);
    }

    private void seedSession() {
        final User user = DBHelper.getUser(daoSession);
        final long deviceId = DBHelper.getDevice(dummyGBDevice, daoSession).getId();
        daoSession.getCmfSleepSessionSampleDao().insertOrReplaceInTx(List.of(
                new CmfSleepSessionSample(T0, deviceId, user.getId(), T1, null)
        ));
    }

    private void seedStages(final List<StageSpec> specs) {
        final User user = DBHelper.getUser(daoSession);
        final long deviceId = DBHelper.getDevice(dummyGBDevice, daoSession).getId();
        daoSession.getCmfSleepStageSampleDao().insertOrReplaceInTx(specs.stream()
                .map(s -> new CmfSleepStageSample(s.timestamp(), deviceId, user.getId(), s.duration(), s.stage()))
                .toList());
    }

    private record StageSpec(long timestamp, int duration, int stage) {
    }

    @Test
    public void sessionStartingInWindow_returnedWholeEvenPastTsTo() {
        seedSession();
        seedStages(List.of(
                new StageSpec(T0, 3600, 1), // DEEP [T0, T0+3600)
                new StageSpec(T0 + 3600_000L, 7200, 2), // LIGHT [T0+3600, T0+10800)
                new StageSpec(T0 + 10_800_000L, 18_000, 3) // REM [T0+10800, T1)
        ));
        // Window opens at the session's start and closes 1h in: the 8h session must come back
        // complete, not clipped to tsTo.
        final int tsFrom = (int) (T0 / 1000L);
        final int tsTo = tsFrom + 3600;

        final List<SleepSession> sessions = provider().getSleepSessions(tsFrom, tsTo);

        assertEquals(1, sessions.size());
        final SleepSession session = sessions.get(0);
        assertEquals(T0, session.getStartTime());
        assertEquals(T1, session.getEndTime());
        assertEquals(3, session.getStages().size());
        assertEquals(ActivityKind.DEEP_SLEEP, session.getStages().get(0).getKind());
        assertEquals(T0, session.getStages().get(0).getStartTime());
        assertEquals(T0 + 3600_000L, session.getStages().get(0).getEndTime());
        assertEquals(ActivityKind.REM_SLEEP, session.getStages().get(2).getKind());
        assertEquals(T1, session.getStages().get(2).getEndTime());
    }

    @Test
    public void sessionStartingBeforeWindow_isExcluded() {
        seedSession();
        seedStages(List.of(new StageSpec(T0, 28_800, 2)));
        // Window opens right after the session ends: the padded fetch still finds it, but the
        // filter-by-start must exclude it - it belongs to an earlier window.
        final int tsFrom = (int) (T1 / 1000L);
        final int tsTo = tsFrom + 3600;

        assertEquals(0, provider().getSleepSessions(tsFrom, tsTo).size());
    }

    @Test
    public void duplicateStageResends_clippedAndMerged() {
        seedSession();
        seedStages(List.of(
                new StageSpec(T0, 3600, 1), // DEEP [T0, T0+3600)
                new StageSpec(T0 + 3600_000L, 3600, 2), // LIGHT [T0+3600, T0+7200)
                new StageSpec(T0 + 7200_000L, 3600, 2), // LIGHT continuation -> merges to [T0+3600, T0+10800)
                new StageSpec(T0 + 9000_000L, 19_800, 3) // REM resent with overlapping start -> clipped to [T0+10800, T1)
        ));

        final List<SleepSession> sessions = provider().getSleepSessions((int) (T0 / 1000L), (int) (T0 / 1000L) + 3600);

        assertEquals(1, sessions.size());
        final List<SleepStage> stages = sessions.get(0).getStages();
        assertEquals(3, stages.size());
        assertEquals(ActivityKind.DEEP_SLEEP, stages.get(0).getKind());
        assertEquals(T0, stages.get(0).getStartTime());
        assertEquals(T0 + 3600_000L, stages.get(0).getEndTime());
        assertEquals(ActivityKind.LIGHT_SLEEP, stages.get(1).getKind());
        assertEquals(T0 + 3600_000L, stages.get(1).getStartTime());
        assertEquals(T0 + 9000_000L, stages.get(1).getEndTime());
        assertEquals(ActivityKind.REM_SLEEP, stages.get(2).getKind());
        assertEquals(T0 + 9000_000L, stages.get(2).getStartTime());
        assertEquals(T1, stages.get(2).getEndTime());
    }

    @Test
    public void noStageSamples_singleLightStageForWholeSession() {
        seedSession();

        final List<SleepSession> sessions = provider().getSleepSessions((int) (T0 / 1000L), (int) (T0 / 1000L) + 3600);

        assertEquals(1, sessions.size());
        final List<SleepStage> stages = sessions.get(0).getStages();
        assertEquals(1, stages.size());
        assertEquals(ActivityKind.LIGHT_SLEEP, stages.get(0).getKind());
        assertEquals(T0, stages.get(0).getStartTime());
        assertEquals(T1, stages.get(0).getEndTime());
    }

    // The HC per-night registry persists spans at epochSecond precision and reloads them via
    // Instant.ofEpochSecond. A session boundary with a sub-second component would truncate on
    // reload, defeat the skip-unchanged guard, and rewrite the night every sync. Providers must
    // therefore emit whole-second boundaries.
    @Test
    public void sessionBoundaries_areWholeSeconds_registryRoundTrip() {
        seedSession();
        seedStages(List.of(new StageSpec(T0, 28_800, 2)));
        final SleepSession session = provider().getSleepSessions((int) (T0 / 1000L), (int) (T0 / 1000L) + 3600).get(0);

        assertEquals(
                Instant.ofEpochMilli(session.getStartTime()),
                Instant.ofEpochSecond(Instant.ofEpochMilli(session.getStartTime()).getEpochSecond()));
        assertEquals(
                Instant.ofEpochMilli(session.getEndTime()),
                Instant.ofEpochSecond(Instant.ofEpochMilli(session.getEndTime()).getEpochSecond()));
    }
}