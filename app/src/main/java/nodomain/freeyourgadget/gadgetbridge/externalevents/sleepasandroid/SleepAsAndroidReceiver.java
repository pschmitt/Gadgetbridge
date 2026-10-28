package nodomain.freeyourgadget.gadgetbridge.externalevents.sleepasandroid;

import android.app.AlarmManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

public class SleepAsAndroidReceiver extends BroadcastReceiver {
    private static final Logger LOG = LoggerFactory.getLogger(SleepAsAndroidReceiver.class);

    /** An alarm this close to its time is ringing or about to, so leaving the system schedule is expected. */
    static final long ALARM_RINGING_MARGIN_MS = 60_000L;

    /**
     * Sleep as Android sends UPDATE_ALARM only while it is tracking, so it does not tell the watch
     * when its next alarm is skipped or dismissed ahead of time outside a tracking session. It
     * schedules that alarm as the system alarm clock, though, so the alarm forwarded last is
     * watched there: once it was seen as the next alarm clock and then disappears before its
     * time, the watch slot is disabled with an UPDATE_ALARM carrying a past TIMESTAMP.
     */
    private long armedAlarm;
    private boolean armedAlarmSeen;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        LOG.debug("Got Sleep as Android action {}", action);

        if (action == null || !GBApplication.getPrefs().getBoolean(GBPrefs.SLEEP_AS_ANDROID_ENABLED, false)) {
            return;
        }

        if (AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED.equals(action)) {
            checkArmedAlarm(context);
            return;
        }

        final Bundle extras = sanitizeExtras(intent);
        forward(action, extras);

        if (SleepAsAndroidAction.UPDATE_ALARM.equals(action)) {
            armedAlarm = extras != null ? extras.getLong("TIMESTAMP", 0L) : 0L;
            armedAlarmSeen = false;
            checkArmedAlarm(context);
        }
    }

    private void checkArmedAlarm(final Context context) {
        if (armedAlarm <= 0) {
            return;
        }
        if (armedAlarm - currentTimeMillis() < ALARM_RINGING_MARGIN_MS) {
            armedAlarm = 0;
            armedAlarmSeen = false;
            return;
        }

        final Long next = getNextAlarmClockTime(context);
        if (next != null && next / 60_000L == armedAlarm / 60_000L) {
            armedAlarmSeen = true;
            return;
        }

        // An earlier next alarm clock hides whether the armed one is still scheduled.
        if (armedAlarmSeen && (next == null || next > armedAlarm)) {
            LOG.info("Sleep as Android alarm at {} is no longer scheduled, next alarm clock is {}, disabling it on the device", armedAlarm, next);
            armedAlarm = 0;
            armedAlarmSeen = false;

            final Bundle extras = new Bundle();
            extras.putLong("TIMESTAMP", 0L);
            forward(SleepAsAndroidAction.UPDATE_ALARM, extras);
        }
    }

    @VisibleForTesting
    protected void forward(final String action, final Bundle extras) {
        GBApplication.deviceService().onSleepAsAndroidAction(action, extras);
    }

    /** @return the trigger time of the system's next alarm clock, or null when none is set */
    @VisibleForTesting
    @Nullable
    protected Long getNextAlarmClockTime(final Context context) {
        final AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        final AlarmManager.AlarmClockInfo info = alarmManager != null ? alarmManager.getNextAlarmClock() : null;
        return info != null ? info.getTriggerTime() : null;
    }

    @VisibleForTesting
    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    // This receiver is RECEIVER_EXPORTED, so extras come from an untrusted source. Forwarding
    // the received Bundle as-is into an Intent that starts our own service trips StrictMode's
    // unsafe intent launch detection, since the taint follows the Bundle even into a new Intent.
    // Copying only the known keys into a fresh Bundle breaks that taint.
    static Bundle sanitizeExtras(Intent intent) {
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            return null;
        }

        final Bundle sanitized = new Bundle();
        if (extras.containsKey("TIMESTAMP")) {
            sanitized.putLong("TIMESTAMP", getLong(extras, "TIMESTAMP", 0L));
        }
        if (extras.containsKey("SUSPENDED")) {
            sanitized.putBoolean("SUSPENDED", extras.getBoolean("SUSPENDED", false));
        }
        if (extras.containsKey("SIZE")) {
            sanitized.putLong("SIZE", getLong(extras, "SIZE", 12L));
        }
        if (extras.containsKey("REPEAT")) {
            sanitized.putInt("REPEAT", (int) getLong(extras, "REPEAT", 1L));
        }
        if (extras.containsKey("TITLE")) {
            sanitized.putString("TITLE", extras.getString("TITLE"));
        }
        if (extras.containsKey("TEXT")) {
            sanitized.putString("TEXT", extras.getString("TEXT"));
        }
        if (extras.containsKey("DELAY")) {
            sanitized.putInt("DELAY", (int) getLong(extras, "DELAY", 60000L));
        }
        // Sleep as Android signals the sensors it wants by the presence of these two, not by
        // their value.
        if (extras.containsKey("DO_HR_MONITORING")) {
            sanitized.putBoolean("DO_HR_MONITORING", true);
        }
        if (extras.containsKey("DO_OXIMETER_MONITORING")) {
            sanitized.putBoolean("DO_OXIMETER_MONITORING", true);
        }
        return sanitized;
    }

    /**
     * Sleep as Android is inconsistent about the width of its numeric extras, and
     * {@link Bundle#getLong} does not widen an Integer: it logs a type mismatch and returns the
     * default, which would silently pin the batch size or the alarm delay to a wrong value.
     */
    private static long getLong(final Bundle extras, final String key, final long fallback) {
        final Object value = extras.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        LOG.warn("Extra {} is {}, expected a number", key, value != null ? value.getClass().getSimpleName() : "absent");
        return fallback;
    }

    public IntentFilter getIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();

        intentFilter.addAction(SleepAsAndroidAction.START_TRACKING);
        intentFilter.addAction(SleepAsAndroidAction.STOP_TRACKING);
        intentFilter.addAction(SleepAsAndroidAction.SET_PAUSE);
        intentFilter.addAction(SleepAsAndroidAction.SET_SUSPENDED);
        intentFilter.addAction(SleepAsAndroidAction.SET_BATCH_SIZE);
        intentFilter.addAction(SleepAsAndroidAction.START_ALARM);
        intentFilter.addAction(SleepAsAndroidAction.STOP_ALARM);
        intentFilter.addAction(SleepAsAndroidAction.UPDATE_ALARM);
        intentFilter.addAction(SleepAsAndroidAction.SHOW_NOTIFICATION);
        intentFilter.addAction(SleepAsAndroidAction.HINT);
        intentFilter.addAction(SleepAsAndroidAction.CHECK_CONNECTED);
        intentFilter.addAction(SleepAsAndroidAction.CONFIRM_CONNECTED);
        intentFilter.addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED);

        return intentFilter;
    }
}
