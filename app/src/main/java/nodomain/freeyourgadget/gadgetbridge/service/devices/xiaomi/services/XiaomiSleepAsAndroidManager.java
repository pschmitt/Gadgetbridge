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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.services;

import android.os.Handler;
import android.os.SystemClock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericDurationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericDurationSample;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.AxisSensor;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.RawSensorBatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenReply;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatsPhone;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatsWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatusWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatusWatchSport;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.SleepAsAndroidSender;

/**
 * The band side of a Sleep as Android session, which runs over a hidden synthetic workout that
 * carries the raw accelerometer stream. It rides on the Health command type, so
 * {@link XiaomiHealthService} owns this and forwards the commands that belong to the session.
 */
public final class XiaomiSleepAsAndroidManager {
    private static final Logger LOG = LoggerFactory.getLogger(XiaomiSleepAsAndroidManager.class);

    // Synthetic-sport id used to mark the workout as hidden / non-persistent
    private static final int SYNTHETIC_SPORT = 810;     // AstroBox SportType.MOTION_SENSING_GAME
    // The band renders these values on its workout screen and blanks it if the stream stops
    // altogether. The full rate is only worth its radio traffic while the screen is likely to be
    // on, which for a sleep session is the first few seconds; the rest of the night runs at the
    // idle rate. Keeping the workout open is the keepalive's job, not this stream's.
    private static final long WORKOUT_STATS_INTERVAL_MS = 1_000L;
    private static final long WORKOUT_STATS_IDLE_INTERVAL_MS = 5_000L;
    private static final long WORKOUT_STATS_ACTIVE_WINDOW_MS = 10_000L;
    // The band closes the synthetic workout on its own unless the start status is repeated.
    private static final long KEEPALIVE_INTERVAL_MS = 24_000L;
    // Raw sensor batches arrive continuously; a longer gap means the band dropped the session.
    private static final long STALL_TIMEOUT_MS = 30_000L;
    // A band that answers none of the restarts will not answer the next one either, and the
    // session has to stop rather than reopen a workout every half minute until morning.
    private static final int MAX_RESTART_ATTEMPTS = 3;
    // The band ignores a finish that follows the preceding status too closely, and keeps the
    // workout open. A later start is then rejected until the band is power-cycled.
    private static final long FINISH_DELAY_MS = 500L;
    // Wall-clock start of the open synthetic workout, so the span the band billed as a workout is
    // known when it closes, whatever restarted in between.
    private static final String PREF_SESSION_STARTED_TS = "xiaomi_saa_session_started_ts";
    // Nothing on the band closes the synthetic workout by itself. Past this age a stored start
    // belongs to no session anybody is still waiting on.
    private static final long SESSION_MAX_AGE_MS = 12 * 60 * 60_000L;
    // How long a workout that survived a connection is left for Sleep as Android to claim. It
    // repeats START_TRACKING as its own watchdog roughly once a minute.
    private static final long RECOVERY_GRACE_MS = 120_000L;
    // Reported to the band until a real reading arrives.
    private static final int HEART_RATE_UNKNOWN = 255;

    private final XiaomiHealthService health;

    // The session is driven from the handler thread and fed from the Bluetooth callback thread,
    // so every field both of them touch is published.
    private volatile boolean rawSensorActive = false;
    // Covers the whole session: the gap between closing a stale workout and opening the new one,
    // and the gap between the closing pause and the closing finish. The band echoes its workout
    // status without a sport id, so this is the only way to tell those echoes apart from a workout
    // started on the band itself. It survives a reconnect, because the band keeps the workout open
    // across one and goes on echoing it.
    private volatile boolean sessionRequested = false;
    private volatile boolean heartRateRequested = false;
    private volatile long workoutStartedMs = 0;
    private volatile long statsActiveUntilMs = 0;
    private volatile long lastRawSensorBatchMs = 0;
    private volatile int restartAttempts = 0;
    private volatile int lastHeartRate = HEART_RATE_UNKNOWN;
    private final Handler workoutStatsHandler = new Handler();
    private final Handler keepaliveHandler = new Handler();
    private final Handler workoutStatusHandler = new Handler();

    private SleepAsAndroidSender sender;

    public XiaomiSleepAsAndroidManager(final XiaomiHealthService health) {
        this.health = health;
    }

    public void setSender(final SleepAsAndroidSender sender) {
        this.sender = sender;
    }

    boolean isSessionRequested() {
        return sessionRequested;
    }

    boolean isRawSensorActive() {
        return rawSensorActive;
    }

    /**
     * Stop every timer driving the session, all of which belong to the connection that started
     * them. The session itself is kept: the band holds the workout across a reconnect and goes on
     * echoing it.
     */
    void reset() {
        workoutStatusHandler.removeCallbacksAndMessages(null);
        stopKeepalive();
        stopWorkoutStatsTicker();
        rawSensorActive = false;
    }

    /**
     * Deal with a synthetic workout the band kept open across a connect.
     * <p>
     * The band holds the workout on its own and goes on asking the phone to confirm it, so an
     * interrupted session leaves it running with nothing driving it: the keepalive, the stats
     * ticker and the raw stream all belong to the connection that went away. The session is kept
     * recognised while Sleep as Android is given the chance to claim it, which its repeated
     * START_TRACKING does by rebuilding all three. A workout nobody claims belongs to a session
     * that ended while the link was down, and only closing it gets the band off its workout screen.
     */
    void recover() {
        reset();
        if (!sessionStartedRecently()) {
            forgetSession();
            return;
        }

        sessionRequested = true;
        workoutStatusHandler.postDelayed(this::closeUnclaimedSession, RECOVERY_GRACE_MS);
    }

    private void closeUnclaimedSession() {
        if (rawSensorActive) {
            return;
        }

        LOG.info("Closing the synthetic workout left open by an earlier connection");
        stop();
    }

    /**
     * Start a synthetic workout on the band. Sequence:
     *  1. REALTIME_STATS_START -- enables HR/steps stream, only when heart rate was asked for
     *  2. WORKOUT_WATCH_STATUS(status=STARTED, sport=SYNTHETIC_SPORT) -- tells the band to
     *     open a hidden workout. Band then sends WORKOUT_WATCH_OPEN to us, answered by
     *     {@link #ackWorkoutOpen()} while the session is open, and the band starts streaming
     *     subtype-53 raw accel batches.
     * A WORKOUT_WATCH_STATUS(FINISHED) goes out first, {@link #FINISH_DELAY_MS} ahead of the
     * start, to close a workout the band may still hold from an earlier session.
     *
     * @param withHeartRate whether Sleep as Android asked for heart rate, which decides whether
     *                      the realtime stream is started at all
     */
    public void start(final boolean withHeartRate) {
        sessionRequested = true;
        rememberSession();
        restartAttempts = 0;
        closeThenOpenWorkout(withHeartRate, false);
    }

    private void closeThenOpenWorkout(final boolean withHeartRate, final boolean rearmRealtime) {
        heartRateRequested = withHeartRate;
        rawSensorActive = false;
        workoutStatusHandler.removeCallbacksAndMessages(null);
        stopKeepalive();
        stopWorkoutStatsTicker();

        // Otherwise the band rejects the workout opened below.
        sendWorkoutStatus(XiaomiHealthService.WORKOUT_FINISHED);
        workoutStatusHandler.postDelayed(() -> openWorkout(rearmRealtime), FINISH_DELAY_MS);
    }

    private void openWorkout(final boolean rearmRealtime) {
        rawSensorActive = true;
        workoutStartedMs = SystemClock.elapsedRealtime();
        lastRawSensorBatchMs = workoutStartedMs;
        statsActiveUntilMs = workoutStartedMs + WORKOUT_STATS_ACTIVE_WINDOW_MS;
        lastHeartRate = HEART_RATE_UNKNOWN;

        if (heartRateRequested) {
            if (rearmRealtime) {
                health.restartSleepAsAndroidRealtime();
            } else {
                health.setSleepAsAndroidRealtime(true);
            }
        }
        sendWorkoutStatus(XiaomiHealthService.WORKOUT_STARTED);
        startWorkoutStatsTicker();
        startKeepalive();
    }

    /**
     * Repeat the start status every {@link #KEEPALIVE_INTERVAL_MS}, and rebuild the workout when no
     * raw sensor batch arrived for {@link #STALL_TIMEOUT_MS}. A batch resets the restart budget.
     */
    private void startKeepalive() {
        keepaliveHandler.removeCallbacksAndMessages(null);
        keepaliveHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!rawSensorActive) {
                    return;
                }

                final long sinceLastBatch = SystemClock.elapsedRealtime() - lastRawSensorBatchMs;
                if (sinceLastBatch > STALL_TIMEOUT_MS) {
                    if (++restartAttempts > MAX_RESTART_ATTEMPTS) {
                        LOG.warn("No raw sensor batch for {}ms after {} restarts, giving up on the synthetic workout",
                                sinceLastBatch, MAX_RESTART_ATTEMPTS);
                        stop();
                        return;
                    }

                    LOG.warn("No raw sensor batch for {}ms, restarting the synthetic workout ({}/{})",
                            sinceLastBatch, restartAttempts, MAX_RESTART_ATTEMPTS);
                    closeThenOpenWorkout(heartRateRequested, true);
                    return;
                }

                sendWorkoutStatus(XiaomiHealthService.WORKOUT_STARTED);
                keepaliveHandler.postDelayed(this, KEEPALIVE_INTERVAL_MS);
            }
        }, KEEPALIVE_INTERVAL_MS);
    }

    private void stopKeepalive() {
        keepaliveHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Tear down the synthetic workout. Sequence:
     *  1. REALTIME_STATS_STOP
     *  2. WORKOUT_WATCH_STATUS(status=PAUSED, ...)
     *  3. WORKOUT_WATCH_STATUS(status=FINISHED, ...) -- final close, {@link #FINISH_DELAY_MS}
     *     after the pause
     */
    public void stop() {
        if (!sessionRequested) {
            return;
        }

        workoutStatusHandler.removeCallbacksAndMessages(null);
        stopKeepalive();
        stopWorkoutStatsTicker();
        health.setSleepAsAndroidRealtime(false);
        sendWorkoutStatus(XiaomiHealthService.WORKOUT_PAUSED);
        rawSensorActive = false;

        // The session owns the workout until the close has actually gone out: the band keeps
        // echoing the status it was given until then.
        workoutStatusHandler.postDelayed(() -> {
            sendWorkoutStatus(XiaomiHealthService.WORKOUT_FINISHED);
            forgetSession();
        }, FINISH_DELAY_MS);
    }

    /**
     * Store when the synthetic workout opened. Sleep as Android repeats START_TRACKING as its own
     * watchdog, so a start already stored for a session still in progress is kept.
     */
    private void rememberSession() {
        if (sessionStartedRecently()) {
            return;
        }
        GBApplication.getDeviceSpecificSharedPrefs(deviceAddress())
                .edit()
                .putLong(PREF_SESSION_STARTED_TS, System.currentTimeMillis())
                .apply();
    }

    private boolean sessionStartedRecently() {
        final long startedMs = health.getDevicePrefs().getLong(PREF_SESSION_STARTED_TS, 0L);
        if (startedMs == 0) {
            return false;
        }
        final long age = System.currentTimeMillis() - startedMs;
        if (age < 0 || age > SESSION_MAX_AGE_MS) {
            LOG.info("Discarding a Sleep as Android session started {}ms ago", age);
            return false;
        }
        return true;
    }

    /**
     * End the session and record the span its synthetic workout covered, which the band billed as
     * exercise, for {@link nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiSampleProvider}.
     */
    private void forgetSession() {
        sessionRequested = false;

        final String address = deviceAddress();
        final long startedMs = health.getDevicePrefs().getLong(PREF_SESSION_STARTED_TS, 0L);
        final long now = System.currentTimeMillis();
        if (startedMs > 0 && now > startedMs && now - startedMs <= SESSION_MAX_AGE_MS) {
            recordWindow(startedMs, now);
        }

        GBApplication.getDeviceSpecificSharedPrefs(address)
                .edit()
                .remove(PREF_SESSION_STARTED_TS)
                .apply();
    }

    private void recordWindow(final long startMs, final long endMs) {
        try (DBHandler db = GBApplication.acquireDB()) {
            final GenericDurationSampleProvider provider =
                    new GenericDurationSampleProvider(health.getSupport().getDevice(), db.getDaoSession());
            final GenericDurationSample window = provider.createSample();
            window.setTimestamp(startMs);
            window.setEndTimestamp(endMs);
            window.setType(XiaomiSampleProvider.DURATION_TYPE_SLEEP_AS_ANDROID);
            provider.persistSamples(window, health.getSupport().getContext());
        } catch (final Exception e) {
            LOG.error("Failed to record the Sleep as Android window", e);
        }
    }

    private String deviceAddress() {
        return health.getSupport().getDevice().getAddress();
    }

    private void startWorkoutStatsTicker() {
        workoutStatsHandler.removeCallbacksAndMessages(null);
        workoutStatsHandler.post(new Runnable() {
            @Override
            public void run() {
                if (!rawSensorActive) {
                    return;
                }
                sendWorkoutStats();
                workoutStatsHandler.postDelayed(this, workoutStatsIntervalMs());
            }
        });
    }

    private long workoutStatsIntervalMs() {
        return SystemClock.elapsedRealtime() < statsActiveUntilMs
                ? WORKOUT_STATS_INTERVAL_MS
                : WORKOUT_STATS_IDLE_INTERVAL_MS;
    }

    private void stopWorkoutStatsTicker() {
        workoutStatsHandler.removeCallbacksAndMessages(null);
    }

    /**
     * Push the values the band shows on its workout screen. Only elapsed time and heart rate are
     * meaningful for the synthetic workout; the rest stay at zero so the band does not display
     * figures that were never measured.
     */
    private void sendWorkoutStats() {
        final int elapsedSeconds = (int) ((SystemClock.elapsedRealtime() - workoutStartedMs) / 1000);

        health.getSupport().sendCommand(
                "saa workout stats",
                XiaomiProto.Command.newBuilder()
                        .setType(XiaomiHealthService.COMMAND_TYPE)
                        .setSubtype(XiaomiHealthService.CMD_WORKOUT_STATS_PHONE)
                        .setHealth(Health.newBuilder().setWorkoutStatsPhone(
                                WorkoutStatsPhone.newBuilder()
                                        .setDurationSeconds(Math.max(0, elapsedSeconds))
                                        .setHeartRate(lastHeartRate)
                                        .setCalories(0)
                                        .setDistance(0)
                        ))
                        .build()
        );
    }

    private void sendWorkoutStatus(final int status) {
        final long now = System.currentTimeMillis();
        final int ts = (int) (now / 1000);
        final int tzOffsetQuarterHours = TimeZone.getDefault().getOffset(now) / 60000 / 15;
        health.getSupport().sendCommand(
                "saa workout status " + status,
                XiaomiProto.Command.newBuilder()
                        .setType(XiaomiHealthService.COMMAND_TYPE)
                        .setSubtype(XiaomiHealthService.CMD_WORKOUT_WATCH_STATUS)
                        .setHealth(Health.newBuilder().setWorkoutStatusWatch(
                                WorkoutStatusWatch.newBuilder()
                                        .setTimestamp(ts)
                                        .setSportInfo(WorkoutStatusWatchSport.newBuilder()
                                                .setTzOffsetQuarterHours(tzOffsetQuarterHours))
                                        .setSport(SYNTHETIC_SPORT)
                                        .setStatus(status)
                                        .setSupportedVersions(3)
                        ))
                        .build()
        );
    }

    /**
     * Whether a workout open request from the band belongs to the session. The band repeats the
     * request every few seconds for as long as the workout is open, so one arriving between two
     * workouts of the session belongs to it as well.
     */
    boolean ownsWorkoutOpen() {
        return sessionRequested;
    }

    /**
     * Whether a workout open request the session does not own is still for a synthetic workout,
     * left on the band by a session nothing on this side is tracking any more.
     */
    boolean isOrphanedWorkoutOpen(final WorkoutOpenWatch workoutOpenWatch) {
        return !sessionRequested && workoutOpenWatch.getSport() == SYNTHETIC_SPORT;
    }

    /**
     * Refuse a synthetic workout that belongs to no session. Refusing is how the band is told to
     * close it; acknowledging would keep it streaming for the rest of the day.
     */
    void refuseWorkoutOpen() {
        LOG.warn("Refusing a synthetic workout that belongs to no session");
        health.getSupport().sendCommand(
                "saa orphan open refuse",
                XiaomiProto.Command.newBuilder()
                        .setType(XiaomiHealthService.COMMAND_TYPE)
                        .setSubtype(XiaomiHealthService.CMD_WORKOUT_WATCH_OPEN)
                        .setHealth(Health.newBuilder().setWorkoutOpenReply(
                                WorkoutOpenReply.newBuilder()
                                        .setCode(XiaomiHealthService.WORKOUT_OPEN_NO_PERMISSION)
                                        .setSelectedVersion(XiaomiHealthService.WORKOUT_PROTOCOL_VERSION)
                                        .setGpsAccuracy(XiaomiHealthService.GPS_ACCURACY_UNKNOWN)
                        ))
                        .build()
        );
    }

    /**
     * Confirm the hidden workout with (0, 2, 2) and without starting GPS, so the band proceeds to
     * stream raw accel.
     */
    void ackWorkoutOpen() {
        health.getSupport().sendCommand(
                "saa raw-sensor open ack",
                XiaomiProto.Command.newBuilder()
                        .setType(XiaomiHealthService.COMMAND_TYPE)
                        .setSubtype(XiaomiHealthService.CMD_WORKOUT_WATCH_OPEN)
                        .setHealth(Health.newBuilder().setWorkoutOpenReply(
                                WorkoutOpenReply.newBuilder()
                                        .setCode(XiaomiHealthService.WORKOUT_OPEN_OK)
                                        .setSelectedVersion(XiaomiHealthService.WORKOUT_PROTOCOL_VERSION)
                                        .setGpsAccuracy(XiaomiHealthService.GPS_ACCURACY_HIGH)
                        ))
                        .build()
        );
    }

    /**
     * Whether a workout status is the synthetic workout's, which must not trigger OpenTracks or
     * any GPS bookkeeping. The band echoes the status it was given with the sport field left
     * empty, so a session in progress is what identifies those echoes rather than the sport.
     */
    boolean isSyntheticWorkoutStatus(final WorkoutStatusWatch workoutStatus) {
        return sessionRequested || workoutStatus.getSport() == SYNTHETIC_SPORT;
    }

    void onRealtimeHeartRate(final int heartRate) {
        if (heartRate <= 0) {
            return;
        }

        lastHeartRate = heartRate;

        if (sender != null) {
            sender.onHrChanged(heartRate, 0);
        }
    }

    /**
     * The stats the watch computes for itself while a workout is open, which it pushes whether the
     * watch or the phone started that workout. Its calorie count is its own: it keeps climbing at a
     * rate the sport type fixes, whatever the heart rate says and whatever the phone reports.
     */
    void handleWorkoutStatsWatch(final WorkoutStatsWatch stats) {
        LOG.debug("Got workout stats from watch: hr={} calories={} steps={} distance={}",
                stats.getHeartRate(), stats.getCalories(), stats.getSteps(), stats.getDistance());

        if (stats.getHeartRate() <= 0) {
            return;
        }

        lastHeartRate = stats.getHeartRate();

        if (rawSensorActive && sender != null) {
            sender.onHrChanged(stats.getHeartRate(), 0);
        }
    }

    void handleRawSensorBatch(final RawSensorBatch batch) {
        lastRawSensorBatchMs = SystemClock.elapsedRealtime();
        restartAttempts = 0;
        final int n = batch.getAccelCount();
        LOG.debug("Got raw sensor batch: {} accel samples", n);
        // Batches carry ten samples and arrive ten times a second, so whether Sleep as Android
        // wants them is decided once for the batch rather than once per sample.
        if (sender != null && n > 0 && sender.acceptsAccelSamples()) {
            for (int i = 0; i < n; i++) {
                final AxisSensor s = batch.getAccel(i);
                sender.submitAccelSample(s.getX(), s.getY(), s.getZ());
            }
        }
    }
}
