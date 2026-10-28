/*  Copyright (C) 2026 Dany Mestas

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import android.content.SharedPreferences;
import android.os.Looper;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.robolectric.Shadows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericDurationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericDurationSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.AxisSensor;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RawSensorBatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenReply;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatusWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.SleepAsAndroidSender;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services.XiaomiHealthService;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The band side of a Sleep as Android session: a synthetic workout carries the raw sensor stream,
 * so it has to be opened, kept alive, rebuilt when the band goes silent and closed cleanly, all
 * without disturbing the realtime stream its other consumers share or the real workout logic.
 */
public class XiaomiSleepAsAndroidWorkoutTest extends TestBase {

    private static final int COMMAND_TYPE = 8;
    private static final int CMD_WORKOUT_WATCH_STATUS = 26;
    private static final int CMD_WORKOUT_WATCH_OPEN = 30;
    private static final int CMD_REALTIME_STATS_START = 45;
    private static final int CMD_REALTIME_STATS_STOP = 46;
    private static final int CMD_WORKOUT_STATS_PHONE = 49;
    private static final int CMD_RAW_SENSOR_BATCH = 53;

    private static final int WORKOUT_OPEN_NO_PERMISSION = 3;

    private static final int WORKOUT_STARTED = 0;
    private static final int WORKOUT_PAUSED = 1;
    private static final int WORKOUT_FINISHED = 3;

    private static final int SAA_SYNTHETIC_SPORT = 810;
    private static final int SPORT_OUTDOOR_RUNNING = 1;
    /** The band leaves the sport out of the status it echoes back. */
    private static final int SPORT_UNSET = 0;

    private static final long OPEN_DELAY_MS = 500L;
    private static final long KEEPALIVE_MS = 24_000L;
    private static final long FULL_INTERVAL_MS = 1_000L;
    private static final long IDLE_INTERVAL_MS = 5_000L;
    private static final long ACTIVE_WINDOW_MS = 10_000L;
    private static final long RECOVERY_GRACE_MS = 120_000L;

    private XiaomiSupport support;
    private XiaomiHealthService health;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        support = Mockito.mock(XiaomiSupport.class);
        final GBDevice device = createDummyGDevice("00:11:22:33:44:55");
        DBHelper.getDevice(device, daoSession);
        Mockito.when(support.getDevice()).thenReturn(device);
        health = new XiaomiHealthService(support);
        health.getSleepAsAndroidManager().setSender(Mockito.mock(SleepAsAndroidSender.class));
    }

    // --- driving ----------------------------------------------------------------------------

    private void idle(final long millis) {
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(millis));
    }

    /** Opens the synthetic workout and leaves every command it sent in the history. */
    private void openSession(final boolean withHeartRate) {
        health.getSleepAsAndroidManager().start(withHeartRate);
        idle(OPEN_DELAY_MS);
    }

    /** Opens the synthetic workout, then forgets the commands the open itself sent. */
    private void openSessionAndClearHistory(final boolean withHeartRate) {
        openSession(withHeartRate);
        Mockito.clearInvocations(support);
    }

    /** Pretend the band delivered a batch of accelerometer samples. */
    private void deliverRawBatch() {
        health.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(COMMAND_TYPE)
                .setSubtype(CMD_RAW_SENSOR_BATCH)
                .setHealth(Health.newBuilder().setRawSensorBatch(
                        RawSensorBatch.newBuilder()
                                .addAccel(AxisSensor.newBuilder()
                                        .setTimestamp(1L).setX(0f).setY(0f).setZ(9.81f))))
                .build());
    }

    private void deliverWorkoutOpen(final int sport) {
        health.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(COMMAND_TYPE)
                .setSubtype(CMD_WORKOUT_WATCH_OPEN)
                .setHealth(Health.newBuilder().setWorkoutOpenWatch(
                        WorkoutOpenWatch.newBuilder()
                                .setSport(sport)
                                .setSupportedVersions(2)
                                .setMainSport(sport)))
                .build());
    }

    private void deliverWorkoutStatus(final int sport, final int status) {
        health.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(COMMAND_TYPE)
                .setSubtype(CMD_WORKOUT_WATCH_STATUS)
                .setHealth(Health.newBuilder().setWorkoutStatusWatch(
                        WorkoutStatusWatch.newBuilder()
                                .setSport(sport)
                                .setStatus(status)))
                .build());
    }

    // --- observing --------------------------------------------------------------------------

    private List<XiaomiProto.Command> sent() {
        final ArgumentCaptor<XiaomiProto.Command> captor = ArgumentCaptor.forClass(XiaomiProto.Command.class);
        Mockito.verify(support, Mockito.atLeast(0)).sendCommand(Mockito.anyString(), captor.capture());
        return captor.getAllValues();
    }

    private List<XiaomiProto.Command> sentOfSubtype(final int subtype) {
        final List<XiaomiProto.Command> matching = new ArrayList<>();
        for (final XiaomiProto.Command command : sent()) {
            if (command.getSubtype() == subtype) {
                matching.add(command);
            }
        }
        return matching;
    }

    private int count(final int subtype) {
        return sentOfSubtype(subtype).size();
    }

    private List<Integer> workoutStatuses() {
        final List<Integer> statuses = new ArrayList<>();
        for (final XiaomiProto.Command command : sentOfSubtype(CMD_WORKOUT_WATCH_STATUS)) {
            statuses.add(command.getHealth().getWorkoutStatusWatch().getStatus());
        }
        return statuses;
    }

    /** A rebuild goes through a full close, so a finish in the history means the workout restarted. */
    private boolean restarted() {
        return workoutStatuses().contains(WORKOUT_FINISHED);
    }

    /**
     * Idle while the band keeps streaming, as it does for the whole of a healthy session. Silence
     * is what the stall detection watches for, and a rebuild would restart every timer under test.
     */
    private void idleWhileStreaming(final long millis) {
        final long step = 10_000L;
        for (long elapsed = 0; elapsed < millis; elapsed += step) {
            deliverRawBatch();
            idle(Math.min(step, millis - elapsed));
        }
    }

    /** How many stats packets go out over the next {@code millis} of a running session. */
    private int statsSentOver(final long millis) {
        final int before = count(CMD_WORKOUT_STATS_PHONE);
        idleWhileStreaming(millis);
        return count(CMD_WORKOUT_STATS_PHONE) - before;
    }

    // --- realtime stream ----------------------------------------------------------------------

    @Test
    public void sleepTrackingKeepsTheStreamWhenChartsClose() {
        openSession(true);
        final int startsAfterSession = count(CMD_REALTIME_STATS_START);

        health.enableRealtimeStats(true);
        health.enableRealtimeStats(false);

        Assert.assertEquals("chart should not have restarted the stream",
                startsAfterSession, count(CMD_REALTIME_STATS_START));
        Assert.assertEquals("closing the chart must not stop Sleep as Android's stream",
                0, count(CMD_REALTIME_STATS_STOP));
    }

    @Test
    public void heartRateIsOnlyStreamedWhenRequested() {
        openSession(false);

        Assert.assertEquals(0, count(CMD_REALTIME_STATS_START));
    }

    // --- session sequencing -------------------------------------------------------------------

    @Test
    public void startClosesAnyWorkoutLeftOpenBeforeOpeningANewOne() {
        health.getSleepAsAndroidManager().start(true);

        Assert.assertEquals(List.of(WORKOUT_FINISHED), workoutStatuses());

        idle(OPEN_DELAY_MS);

        Assert.assertEquals(List.of(WORKOUT_FINISHED, WORKOUT_STARTED), workoutStatuses());
    }

    @Test
    public void stopPausesBeforeFinishing() {
        openSessionAndClearHistory(true);

        health.getSleepAsAndroidManager().stop();

        // The finish must not follow the pause immediately, or the band keeps the workout open.
        Assert.assertEquals(List.of(WORKOUT_PAUSED), workoutStatuses());

        idle(OPEN_DELAY_MS);

        Assert.assertEquals(List.of(WORKOUT_PAUSED, WORKOUT_FINISHED), workoutStatuses());
    }

    @Test
    public void keepaliveRepeatsTheStartStatus() {
        openSessionAndClearHistory(true);

        idle(KEEPALIVE_MS);

        Assert.assertEquals(List.of(WORKOUT_STARTED), workoutStatuses());
    }

    @Test
    public void disposeSilencesEveryTimer() {
        openSession(true);
        health.dispose();
        Mockito.clearInvocations(support);

        idle(60_000);

        Assert.assertTrue(sent().isEmpty());
    }

    // --- stats pacing ---------------------------------------------------------------------------

    @Test
    public void theFirstSecondsRunAtTheFullRate() {
        openSession(true);

        Assert.assertEquals(5, statsSentOver(5 * FULL_INTERVAL_MS));
    }

    @Test
    public void theRestOfTheSessionRunsAtTheIdleRate() {
        openSession(true);
        idle(ACTIVE_WINDOW_MS);

        Assert.assertEquals(6, statsSentOver(6 * IDLE_INTERVAL_MS));
    }

    @Test
    public void nothingIsSentAfterTheSessionStops() {
        openSession(true);
        idle(30_000);

        health.getSleepAsAndroidManager().stop();

        Assert.assertEquals(0, statsSentOver(Duration.ofMinutes(5).toMillis()));
    }

    // --- stall detection --------------------------------------------------------------------

    @Test
    public void steadyBatchesKeepTheSessionAlive() {
        openSessionAndClearHistory(true);

        // Batches every 10 s, comfortably inside the stall window, across three keepalive ticks.
        for (int i = 0; i < 8; i++) {
            idle(10_000);
            deliverRawBatch();
        }

        Assert.assertFalse("a healthy session must never be rebuilt", restarted());
    }

    @Test
    public void silenceRebuildsTheWorkout() {
        openSessionAndClearHistory(true);

        // First tick at 24 s still counts as healthy; the second finds 48 s of silence.
        idle(50_000);

        Assert.assertTrue("the session must rebuild itself after a stall", restarted());
    }

    @Test
    public void aRebuildRestartsTheRealtimeStream() {
        openSessionAndClearHistory(true);

        idle(50_000);
        idle(OPEN_DELAY_MS);

        Assert.assertTrue(restarted());
        Assert.assertEquals(1, count(CMD_REALTIME_STATS_START));
    }

    @Test
    public void aRebuildKeepsTheHeartRateRequest() {
        // Sleep as Android asked for movement only, so the rebuild must not power the heart rate
        // sensor up.
        openSessionAndClearHistory(false);

        idle(50_000);
        idle(OPEN_DELAY_MS);

        Assert.assertTrue(restarted());
        Assert.assertEquals(0, count(CMD_REALTIME_STATS_START));
    }

    @Test
    public void aBandThatNeverAnswersIsGivenUpOn() {
        openSessionAndClearHistory(true);

        // Ten minutes of complete silence, far more than the restarts are allowed to cover.
        idle(10 * 60_000);
        idle(OPEN_DELAY_MS);
        Mockito.clearInvocations(support);

        idle(5 * KEEPALIVE_MS);

        Assert.assertTrue("a band that answers nothing must stop being talked to",
                workoutStatuses().isEmpty());
    }

    @Test
    public void aBatchResetsTheRestartBudget() {
        openSessionAndClearHistory(true);

        // Three stalls, each followed by the band coming back, is a session that recovers, not
        // one that has to be given up on.
        for (int i = 0; i < 4; i++) {
            idle(50_000);
            idle(OPEN_DELAY_MS);
            deliverRawBatch();
        }
        Mockito.clearInvocations(support);

        idle(KEEPALIVE_MS / 2);
        deliverRawBatch();
        idle(KEEPALIVE_MS);

        Assert.assertEquals(List.of(WORKOUT_STARTED), workoutStatuses());
    }

    @Test
    public void noRestartAfterTheSessionIsStopped() {
        openSession(true);
        health.getSleepAsAndroidManager().stop();
        idle(OPEN_DELAY_MS);
        Mockito.clearInvocations(support);

        idle(5 * KEEPALIVE_MS);

        Assert.assertTrue("a stopped session must stay stopped", workoutStatuses().isEmpty());
    }

    // --- handoff with the real workout logic --------------------------------------------------

    @Test
    public void syntheticOpenIsAcknowledgedWithoutGps() {
        openSessionAndClearHistory(true);

        deliverWorkoutOpen(SAA_SYNTHETIC_SPORT);

        final List<XiaomiProto.Command> replies = sentOfSubtype(CMD_WORKOUT_WATCH_OPEN);
        Assert.assertEquals(1, replies.size());

        final WorkoutOpenReply reply = replies.get(0).getHealth().getWorkoutOpenReply();
        // The band only starts streaming raw accelerometer data once it gets this exact reply.
        Assert.assertEquals(0, reply.getCode());
        Assert.assertEquals(2, reply.getSelectedVersion());
        Assert.assertEquals(2, reply.getGpsAccuracy());
    }

    @Test
    public void syntheticOpenIsAcknowledgedWhileTheSessionCloses() {
        // The band repeats the request every few seconds for as long as its workout is open, so
        // one lands in every gap the session leaves. Answering it as a real workout is what starts
        // the phone GPS.
        openSessionAndClearHistory(true);
        health.getSleepAsAndroidManager().stop();
        Mockito.clearInvocations(support);

        deliverWorkoutOpen(SAA_SYNTHETIC_SPORT);

        final List<XiaomiProto.Command> replies = sentOfSubtype(CMD_WORKOUT_WATCH_OPEN);
        Assert.assertEquals(1, replies.size());
        Assert.assertEquals(0, replies.get(0).getHealth().getWorkoutOpenReply().getCode());
    }

    @Test
    public void nothingElseCanOpenAWorkoutDuringASession() {
        openSessionAndClearHistory(true);

        deliverWorkoutOpen(SPORT_OUTDOOR_RUNNING);

        final List<XiaomiProto.Command> replies = sentOfSubtype(CMD_WORKOUT_WATCH_OPEN);
        Assert.assertEquals(1, replies.size());
        Assert.assertEquals("the band cannot hold a second workout, so this is still the synthetic one",
                0, replies.get(0).getHealth().getWorkoutOpenReply().getCode());
    }

    @Test
    public void syntheticStatusIsIgnoredEntirely() {
        openSessionAndClearHistory(true);

        deliverWorkoutStatus(SAA_SYNTHETIC_SPORT, WORKOUT_STARTED);
        deliverWorkoutStatus(SAA_SYNTHETIC_SPORT, WORKOUT_FINISHED);

        Assert.assertTrue("the synthetic workout must not be answered", workoutStatuses().isEmpty());
        // Reaching the real workout logic would require the device to read its preferences.
        Mockito.verify(support, Mockito.never()).getDevice();
    }

    @Test
    public void statusEchoedWhileTheSessionClosesIsNotAUserWorkout() {
        openSessionAndClearHistory(true);
        health.getSleepAsAndroidManager().stop();
        Mockito.clearInvocations(support);

        deliverWorkoutStatus(SPORT_UNSET, WORKOUT_STARTED);

        Mockito.verify(support, Mockito.never()).getDevice();
    }

    @Test
    public void realWorkoutStatusStillReachesTheWorkoutLogic() {
        deliverWorkoutStatus(SPORT_OUTDOOR_RUNNING, WORKOUT_STARTED);

        // It read the device preferences, so it did not take the synthetic short circuit.
        Mockito.verify(support, Mockito.atLeastOnce()).getDevice();
    }

    @Test
    public void realWorkoutIsUnaffectedAfterASyntheticSessionEnds() {
        openSession(true);
        health.getSleepAsAndroidManager().stop();
        idle(OPEN_DELAY_MS);
        Mockito.clearInvocations(support);

        deliverWorkoutOpen(SPORT_OUTDOOR_RUNNING);

        final List<XiaomiProto.Command> replies = sentOfSubtype(CMD_WORKOUT_WATCH_OPEN);
        Assert.assertEquals(1, replies.size());
        // With phone GPS off this is the "no location" reply, not the synthetic (0, 2, 2).
        Assert.assertNotEquals("the synthetic ack must not leak into a real workout",
                0, replies.get(0).getHealth().getWorkoutOpenReply().getCode());
    }

    // --- the window the band billed as a workout ------------------------------------------------

    /** Backdate the stored session so it looks like it started {@code millis} ago. */
    private void ageTheStoredSession(final long millis) {
        final String key = "xiaomi_saa_session_started_ts";
        final SharedPreferences prefs =
                GBApplication.getDeviceSpecificSharedPrefs(support.getDevice().getAddress());
        prefs.edit().putLong(key, prefs.getLong(key, 0L) - millis).apply();
    }

    private List<long[]> recordedWindows() {
        final List<long[]> windows = new ArrayList<>();
        for (final GenericDurationSample window : new GenericDurationSampleProvider(support.getDevice(), daoSession)
                .getSamplesOverlapping(XiaomiSampleProvider.DURATION_TYPE_SLEEP_AS_ANDROID, 0, Long.MAX_VALUE)) {
            windows.add(new long[]{window.getTimestamp(), window.getEndTimestamp()});
        }
        return windows;
    }

    @Test
    public void stoppingASessionRecordsTheWindowItCovered() {
        openSession(true);
        ageTheStoredSession(60_000);

        health.getSleepAsAndroidManager().stop();
        idle(OPEN_DELAY_MS);

        final List<long[]> windows = recordedWindows();
        Assert.assertEquals(1, windows.size());
        Assert.assertTrue("the window must span the session",
                windows.get(0)[1] - windows.get(0)[0] >= 60_000);
    }

    @Test
    public void theWatchdogRestartKeepsTheSessionStart() {
        openSession(true);
        ageTheStoredSession(60 * 60_000L);

        // Sleep as Android repeats START_TRACKING as its own watchdog.
        openSession(true);
        health.getSleepAsAndroidManager().stop();
        idle(OPEN_DELAY_MS);

        final List<long[]> windows = recordedWindows();
        Assert.assertEquals(1, windows.size());
        Assert.assertTrue("the window must start with the first START_TRACKING",
                windows.get(0)[1] - windows.get(0)[0] >= 60 * 60_000L);
    }

    @Test
    public void aSessionThatNeverRanRecordsNothing() {
        health.getSleepAsAndroidManager().stop();
        idle(OPEN_DELAY_MS);

        Assert.assertTrue(recordedWindows().isEmpty());
    }

    @Test
    public void aStoredStartTooOldToBelongToTheSessionIsReplaced() {
        openSession(true);
        // Past the twelve hours a session is kept, so it ended at some unknown time.
        ageTheStoredSession(13 * 60 * 60_000L);

        openSession(true);
        // The session start is wall-clock time, which idle() does not advance, so without this the
        // replacement start and the stop can fall in the same millisecond and record no window.
        ageTheStoredSession(1_000L);
        health.getSleepAsAndroidManager().stop();
        idle(OPEN_DELAY_MS);

        final List<long[]> windows = recordedWindows();
        Assert.assertEquals(1, windows.size());
        Assert.assertTrue("only the session in progress is billed",
                windows.get(0)[1] - windows.get(0)[0] < 60_000);
    }

    // --- recovering a session the connection interrupted ------------------------------------

    /** Run what a new connection runs, so whatever the band still holds is dealt with. */
    private void reconnect() {
        health.initialize();
        idle(OPEN_DELAY_MS);
    }

    @Test
    public void aSessionSleepAsAndroidClaimsBackSurvivesTheConnection() {
        openSession(true);
        health.dispose();
        Mockito.clearInvocations(support);

        reconnect();
        // Sleep as Android repeats START_TRACKING as its own watchdog.
        openSession(true);
        idleWhileStreaming(RECOVERY_GRACE_MS);

        Assert.assertEquals("the workout is closed and opened again",
                List.of(WORKOUT_FINISHED, WORKOUT_STARTED), workoutStatuses().subList(0, 2));
        // Every close starts with a pause, so one anywhere means the session was taken for an orphan.
        Assert.assertFalse("a claimed session must not be closed",
                workoutStatuses().contains(WORKOUT_PAUSED));
        Assert.assertEquals("the stream the session feeds on has to come back too",
                1, count(CMD_REALTIME_STATS_START));
    }

    @Test
    public void aWorkoutNobodyClaimsIsClosed() {
        openSession(true);
        health.dispose();
        Mockito.clearInvocations(support);

        reconnect();
        idle(RECOVERY_GRACE_MS);

        Assert.assertEquals("the orphan is paused then finished",
                List.of(WORKOUT_PAUSED, WORKOUT_FINISHED), workoutStatuses());
    }

    @Test
    public void theBandIsGivenTimeToBeClaimedBeforeTheWorkoutIsClosed() {
        openSession(true);
        health.dispose();
        Mockito.clearInvocations(support);

        reconnect();
        idle(RECOVERY_GRACE_MS / 2);

        Assert.assertTrue(workoutStatuses().isEmpty());
    }

    @Test
    public void aRestartedGadgetbridgeStillClosesTheOrphan() {
        openSession(true);
        health.dispose();

        // A fresh process knows nothing beyond what the session left in the preferences.
        health = new XiaomiHealthService(support);
        health.getSleepAsAndroidManager().setSender(Mockito.mock(SleepAsAndroidSender.class));
        Mockito.clearInvocations(support);

        reconnect();
        idle(RECOVERY_GRACE_MS);

        Assert.assertEquals(List.of(WORKOUT_PAUSED, WORKOUT_FINISHED), workoutStatuses());
    }

    @Test
    public void aSessionOlderThanTheCapIsForgotten() {
        openSession(true);
        health.dispose();
        Mockito.clearInvocations(support);

        ageTheStoredSession(Duration.ofHours(13).toMillis());
        reconnect();
        idle(RECOVERY_GRACE_MS);

        Assert.assertTrue("a night that old is over whatever the band still shows",
                workoutStatuses().isEmpty());
    }

    @Test
    public void aCleanlyStoppedSessionIsNotRecovered() {
        openSession(true);
        health.getSleepAsAndroidManager().stop();
        idle(OPEN_DELAY_MS);
        health.dispose();
        Mockito.clearInvocations(support);

        reconnect();
        idle(RECOVERY_GRACE_MS);

        Assert.assertTrue(workoutStatuses().isEmpty());
    }

    @Test
    public void statusEchoedAfterADroppedLinkIsNotAUserWorkout() {
        openSession(true);
        // The link drops and comes back; the band still has the synthetic workout open.
        health.dispose();
        Mockito.clearInvocations(support);

        deliverWorkoutStatus(SPORT_UNSET, WORKOUT_STARTED);

        Mockito.verify(support, Mockito.never()).getDevice();
    }

    @Test
    public void anOrphanedSyntheticOpenIsRefused() {
        // Nothing on this side is tracking, so the workout the band still holds belongs to nobody.
        deliverWorkoutOpen(SAA_SYNTHETIC_SPORT);

        final List<XiaomiProto.Command> replies = sentOfSubtype(CMD_WORKOUT_WATCH_OPEN);
        Assert.assertEquals(1, replies.size());
        Assert.assertEquals(WORKOUT_OPEN_NO_PERMISSION,
                replies.get(0).getHealth().getWorkoutOpenReply().getCode());
    }

    @Test
    public void closingAnUnclaimedSessionRecordsItsWindow() {
        openSession(true);
        ageTheStoredSession(60_000);
        health.dispose();

        reconnect();
        idle(RECOVERY_GRACE_MS);
        idle(OPEN_DELAY_MS);

        Assert.assertEquals(1, recordedWindows().size());
    }
}