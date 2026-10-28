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
package nodomain.freeyourgadget.gadgetbridge.service;

import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowApplication;

import java.util.EnumSet;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.SleepAsAndroidFeature;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

/**
 * How a Sleep as Android session starts and stops, and the feature toggles that decide whether a
 * sample reaches it.
 */
public class SleepAsAndroidSenderSessionTest extends TestBase {

    private static final String ADDRESS = "00:11:22:33:44:55";
    private static final String ACTION_HEART_RATE_DATA_UPDATE = "com.urbandroid.sleep.watch.HR_DATA_UPDATE";

    private static final Set<SleepAsAndroidFeature> ALL_FEATURES = EnumSet.of(
            SleepAsAndroidFeature.HEART_RATE,
            SleepAsAndroidFeature.ACCELEROMETER,
            SleepAsAndroidFeature.ALARMS,
            SleepAsAndroidFeature.NOTIFICATIONS);

    private static final String[] FEATURE_PREF_KEYS = {
            "pref_key_sleepasandroid_feat_alarms",
            "pref_key_sleepasandroid_feat_notifications",
            "pref_key_sleepasandroid_feat_movement",
            "pref_key_sleepasandroid_feat_hr",
            "pref_key_sleepasandroid_feat_rr_intervals",
            "pref_key_sleepasandroid_feat_oximetry",
            "pref_key_sleepasandroid_feat_spo2",
    };

    private GBDevice device;
    private SleepAsAndroidSender sender;

    @Before
    public void setUpSender() {
        device = createDummyGDevice(ADDRESS);
        device.setState(GBDevice.State.INITIALIZED);

        // Preferences survive between tests in this class, so the per-feature toggles are cleared
        // back to "never written".
        final SharedPreferences.Editor editor = GBApplication.getPrefs().getPreferences().edit()
                .putBoolean(GBPrefs.SLEEP_AS_ANDROID_ENABLED, true)
                .putString(GBPrefs.SLEEP_AS_ANDROID_DEVICE, ADDRESS);
        for (final String key : FEATURE_PREF_KEYS) {
            editor.remove(key);
        }
        editor.apply();

        sender = senderWith(ALL_FEATURES);
        clearBroadcasts();
    }

    @After
    public void tearDownSender() {
        sender.stopTracking();
    }

    /** The test device's coordinator declares no Sleep as Android features. */
    private SleepAsAndroidSender senderWith(final Set<SleepAsAndroidFeature> features) {
        return new SleepAsAndroidSender(device) {
            @Override
            public boolean hasFeature(final SleepAsAndroidFeature feature) {
                return features.contains(feature);
            }
        };
    }

    /** Sleep as Android marks the sensors it wants by adding the extra at all. */
    private static Bundle trackingExtras(final boolean heartRate) {
        final Bundle extras = new Bundle();
        if (heartRate) {
            extras.putBoolean("DO_HR_MONITORING", true);
        }
        return extras;
    }

    private ShadowApplication shadowApp() {
        return Shadows.shadowOf((Application) GBApplication.getContext());
    }

    private void clearBroadcasts() {
        shadowApp().clearBroadcastIntents();
    }

    private int countBroadcasts(final String action) {
        int n = 0;
        for (final Intent intent : shadowApp().getBroadcastIntents()) {
            if (action.equals(intent.getAction())) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void aSessionStoppedWhileDisconnectedStaysStopped() {
        // Sleep as Android ends the session while the link is down, so the stop arrives for a
        // device that is no longer the provider. Whatever it left running would otherwise come
        // back with the next connection and feed a session nobody is recording.
        sender.startTracking(trackingExtras(true));
        device.setState(GBDevice.State.NOT_CONNECTED);

        sender.stopTracking();
        device.setState(GBDevice.State.INITIALIZED);
        clearBroadcasts();

        sender.onHrChanged(72f, 0);

        Assert.assertEquals(0, countBroadcasts(ACTION_HEART_RATE_DATA_UPDATE));
    }

    @Test
    public void featuresDefaultToEnabledBeforeTheSettingsScreenIsOpened() {
        for (final SleepAsAndroidFeature feature : EnumSet.allOf(SleepAsAndroidFeature.class)) {
            Assert.assertTrue(feature + " should default to enabled", sender.isFeatureEnabled(feature));
        }
    }

    @Test
    public void heartRateIsSentWithoutTouchingTheSettings() {
        sender.startTracking(trackingExtras(true));
        clearBroadcasts();

        sender.onHrChanged(72f, 0);

        Assert.assertEquals(1, countBroadcasts(ACTION_HEART_RATE_DATA_UPDATE));
    }

    @Test
    public void disablingAFeaturePrefSuppressesIt() {
        GBApplication.getPrefs().getPreferences().edit()
                .putBoolean("pref_key_sleepasandroid_feat_hr", false)
                .apply();
        sender.startTracking(trackingExtras(true));
        clearBroadcasts();

        sender.onHrChanged(72f, 0);

        Assert.assertEquals(0, countBroadcasts(ACTION_HEART_RATE_DATA_UPDATE));
    }
}
