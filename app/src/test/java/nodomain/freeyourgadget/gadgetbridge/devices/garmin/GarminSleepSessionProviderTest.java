package nodomain.freeyourgadget.gadgetbridge.devices.garmin;

import org.junit.Test;

import java.time.Instant;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminEventSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminNapSample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminSleepStageSample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

import static org.junit.Assert.assertEquals;

// Guards the SleepSessionProvider contract for the Garmin native provider: sessions are returned
// whole and filtered by start, so the HC sleep sync never sees a clipped tail (issue #6453).
public class GarminSleepSessionProviderTest extends TestBase {
    private GBDevice dummyGBDevice;

    // Whole-second night: 2023-11-14T22:13:20Z, 8h long.
    private static final long T0 = 1_700_000_000_000L;
    private static final long NIGHT_MS = 8 * 60 * 60 * 1000L;
    private static final long T1 = T0 + NIGHT_MS;
    private static final long NAP_START = T0 + 12 * 60 * 60 * 1000L;
    private static final long NAP_END = NAP_START + 60 * 60 * 1000L;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        dummyGBDevice = createDummyGDevice("00:00:00:00:10");
        DBHelper.getDevice(dummyGBDevice, daoSession);
    }

    private void seedNight() {
        final User user = DBHelper.getUser(daoSession);
        final long deviceId = DBHelper.getDevice(dummyGBDevice, daoSession).getId();

        daoSession.getGarminEventSampleDao().insertOrReplaceInTx(List.of(
                new GarminEventSample(T0, deviceId, user.getId(), 74, 0, -1L),
                new GarminEventSample(T1, deviceId, user.getId(), 74, 1, -1L)
        ));
        // Stage samples are upper-bound timestamps of each stage.
        daoSession.getGarminSleepStageSampleDao().insertOrReplaceInTx(List.of(
                new GarminSleepStageSample(T0 + 3600_000L, deviceId, user.getId(), 3), // DEEP
                new GarminSleepStageSample(T0 + 7200_000L, deviceId, user.getId(), 4), // REM
                new GarminSleepStageSample(T0 + 14400_000L, deviceId, user.getId(), 2), // LIGHT
                new GarminSleepStageSample(T1, deviceId, user.getId(), 1) // AWAKE
        ));
        daoSession.getGarminNapSampleDao().insertOrReplaceInTx(List.of(
                new GarminNapSample(NAP_START, deviceId, user.getId(), NAP_END)
        ));
    }

    private GarminSleepSessionProvider provider() {
        return new GarminSleepSessionProvider(dummyGBDevice, daoSession);
    }

    @Test
    public void sessionStartingInWindow_returnedWholeEvenPastTsTo() {
        seedNight();
        // Window opens at the night's start and closes 1h in: the 8h night must come back complete.
        final int tsFrom = (int) (T0 / 1000L);
        final int tsTo = tsFrom + 3600;

        final List<SleepSession> sessions = provider().getSleepSessions(tsFrom, tsTo);

        assertEquals(1, sessions.size());
        final SleepSession session = sessions.get(0);
        assertEquals(T0, session.getStartTime());
        assertEquals(T1, session.getEndTime());
        assertEquals(4, session.getStages().size());
        assertEquals(ActivityKind.DEEP_SLEEP, session.getStages().get(0).getKind());
        assertEquals(T0, session.getStages().get(0).getStartTime());
        assertEquals(T0 + 3600_000L, session.getStages().get(0).getEndTime());
        assertEquals(ActivityKind.AWAKE_SLEEP, session.getStages().get(3).getKind());
        assertEquals(T1, session.getStages().get(3).getEndTime());
    }

    @Test
    public void sessionStartingBeforeWindow_isExcluded() {
        seedNight();
        // Window opens right after the night ends: the padded fetch still finds it, but the
        // filter-by-start must exclude it - it belongs to an earlier window.
        final int tsFrom = (int) (T1 / 1000L);
        final int tsTo = tsFrom + 3600;

        assertEquals(0, provider().getSleepSessions(tsFrom, tsTo).size());
    }

    @Test
    public void noStageSamples_singleLightStageForWholeSession() {
        final User user = DBHelper.getUser(daoSession);
        final long deviceId = DBHelper.getDevice(dummyGBDevice, daoSession).getId();
        daoSession.getGarminEventSampleDao().insertOrReplaceInTx(List.of(
                new GarminEventSample(T0, deviceId, user.getId(), 74, 0, -1L),
                new GarminEventSample(T1, deviceId, user.getId(), 74, 1, -1L)
        ));

        final List<SleepSession> sessions = provider().getSleepSessions((int) (T0 / 1000L), (int) (T0 / 1000L) + 3600);

        assertEquals(1, sessions.size());
        final List<SleepStage> stages = sessions.get(0).getStages();
        assertEquals(1, stages.size());
        assertEquals(ActivityKind.LIGHT_SLEEP, stages.get(0).getKind());
        assertEquals(T0, stages.get(0).getStartTime());
        assertEquals(T1, stages.get(0).getEndTime());
    }

    @Test
    public void nap_becomesOwnSession() {
        seedNight();
        final int tsFrom = (int) (NAP_START / 1000L);
        final int tsTo = tsFrom + 3600;

        final List<SleepSession> sessions = provider().getSleepSessions(tsFrom, tsTo);

        assertEquals(1, sessions.size());
        final List<SleepStage> stages = sessions.get(0).getStages();
        assertEquals(1, stages.size());
        assertEquals(ActivityKind.LIGHT_SLEEP, stages.get(0).getKind());
        assertEquals(NAP_START, stages.get(0).getStartTime());
        assertEquals(NAP_END, stages.get(0).getEndTime());
    }

    // The HC per-night registry persists spans at epochSecond precision and reloads them via
    // Instant.ofEpochSecond. A session boundary with a sub-second component would truncate on
    // reload, defeat the skip-unchanged guard, and rewrite the night every sync. Providers must
    // therefore emit whole-second boundaries.
    @Test
    public void sessionBoundaries_areWholeSeconds_registryRoundTrip() {
        seedNight();
        final SleepSession session = provider().getSleepSessions((int) (T0 / 1000L), (int) (T0 / 1000L) + 3600).get(0);

        assertEquals(
                Instant.ofEpochMilli(session.getStartTime()),
                Instant.ofEpochSecond(Instant.ofEpochMilli(session.getStartTime()).getEpochSecond()));
        assertEquals(
                Instant.ofEpochMilli(session.getEndTime()),
                Instant.ofEpochSecond(Instant.ofEpochMilli(session.getEndTime()).getEpochSecond()));
    }
}