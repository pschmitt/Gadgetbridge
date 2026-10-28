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

import android.os.Bundle;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.SleepAsAndroidFeature;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

/**
 * Whether accelerometer samples handed over a batch at a time are measured.
 */
public class SleepAsAndroidSenderAccelTest extends TestBase {

    private static final String ADDRESS = "00:11:22:33:44:55";

    private GBDevice device;
    private SleepAsAndroidSender sender;

    @Before
    public void setUpSender() {
        device = createDummyGDevice(ADDRESS);
        device.setState(GBDevice.State.INITIALIZED);

        GBApplication.getPrefs().getPreferences().edit()
                .putBoolean(GBPrefs.SLEEP_AS_ANDROID_ENABLED, true)
                .putString(GBPrefs.SLEEP_AS_ANDROID_DEVICE, ADDRESS)
                .putBoolean("pref_key_sleepasandroid_feat_movement", true)
                .apply();

        sender = senderWith(EnumSet.of(SleepAsAndroidFeature.ACCELEROMETER, SleepAsAndroidFeature.HEART_RATE));
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

    @Test
    public void samplesAreOnlyAcceptedWhileTrackingRuns() {
        Assert.assertFalse(sender.acceptsAccelSamples());

        sender.startTracking(new Bundle());
        Assert.assertTrue(sender.acceptsAccelSamples());

        sender.pauseTracking(true);
        Assert.assertFalse(sender.acceptsAccelSamples());

        sender.pauseTracking(false);
        Assert.assertTrue(sender.acceptsAccelSamples());

        sender.stopTracking();
        Assert.assertFalse(sender.acceptsAccelSamples());
    }

    @Test
    public void samplesAreNotAcceptedByADeviceWithoutTheSensor() {
        final SleepAsAndroidSender heartRateOnly = senderWith(EnumSet.of(SleepAsAndroidFeature.HEART_RATE));
        heartRateOnly.startTracking(new Bundle());
        try {
            Assert.assertFalse(heartRateOnly.acceptsAccelSamples());
        } finally {
            heartRateOnly.stopTracking();
        }
    }

    @Test
    public void samplesAreNotAcceptedWhenMovementIsTurnedOff() {
        GBApplication.getPrefs().getPreferences().edit()
                .putBoolean("pref_key_sleepasandroid_feat_movement", false)
                .apply();
        sender.startTracking(new Bundle());

        Assert.assertFalse(sender.acceptsAccelSamples());
    }
}
