/*  Copyright (C) 2026 Gadgetbridge Contributors

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
package nodomain.freeyourgadget.gadgetbridge.deviceevents;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Locale;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.util.preferences.DevicePrefs;

/**
 * Fired when a device reports that a workout has started or stopped, so that this can be
 * forwarded as a configurable action (e.g. an Android broadcast that the Home Assistant
 * companion app can pick up via its "external app sends intent" sensor).
 */
public class GBDeviceEventWorkoutState extends GBDeviceEvent {
    private static final Logger LOG = LoggerFactory.getLogger(GBDeviceEventWorkoutState.class);

    public enum WorkoutStatus {
        STARTED,
        STOPPED,
    }

    public final WorkoutStatus workoutStatus;
    @Nullable
    public final ActivityKind activityKind;

    public GBDeviceEventWorkoutState(final WorkoutStatus workoutStatus, @Nullable final ActivityKind activityKind) {
        this.workoutStatus = workoutStatus;
        this.activityKind = activityKind;
    }

    @NonNull
    @Override
    public String toString() {
        return super.toString() + String.format(Locale.ROOT, "workoutStatus=%s, activityKind=%s", workoutStatus, activityKind);
    }

    @Override
    public void evaluate(final Context context, final GBDevice device) {
        final DevicePrefs devicePrefs = GBApplication.getDevicePrefs(device);

        final String selectionsPref;
        final String broadcastActionPref;
        final String broadcastActionDefault;
        final String broadcastPackagePref;

        switch (workoutStatus) {
            case STARTED:
                selectionsPref = DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_WORKOUT_START_SELECTIONS;
                broadcastActionPref = DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_WORKOUT_START_BROADCAST_ACTION;
                broadcastActionDefault = context.getString(R.string.prefs_events_forwarding_workoutstart_broadcast_default_value);
                broadcastPackagePref = DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_WORKOUT_START_BROADCAST_PACKAGE;
                break;
            case STOPPED:
                selectionsPref = DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_WORKOUT_STOP_SELECTIONS;
                broadcastActionPref = DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_WORKOUT_STOP_BROADCAST_ACTION;
                broadcastActionDefault = context.getString(R.string.prefs_events_forwarding_workoutstop_broadcast_default_value);
                broadcastPackagePref = DeviceSettingsPreferenceConst.PREF_DEVICE_ACTION_WORKOUT_STOP_BROADCAST_PACKAGE;
                break;
            default:
                LOG.warn("Unhandled workout status {}, aborting further evaluation", workoutStatus);
                return;
        }

        final Set<String> actions = devicePrefs.getStringSet(selectionsPref, Collections.emptySet());
        if (actions.isEmpty()) {
            return;
        }

        final String broadcastMessage = devicePrefs.getString(broadcastActionPref, broadcastActionDefault);
        final String broadcastPackage = devicePrefs.getString(broadcastPackagePref, "");

        handleDeviceAction(context, device, actions, broadcastMessage, broadcastPackage);
    }
}
