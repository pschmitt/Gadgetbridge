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
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericDurationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericDurationSample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The calories the band bills to the synthetic workout Sleep as Android needs must not read back
 * as the wearer's.
 */
public class XiaomiSampleProviderTest extends TestBase {

    private static final String ADDRESS = "00:11:22:33:44:55";

    private static final int WINDOW_SECONDS = 3600;

    /** Stored kcal are scaled to milli-kcal on read. */
    private static final int STORED_CALORIES = 5;
    private static final int READ_CALORIES = STORED_CALORIES * 1000;

    private GBDevice gbDevice;
    private XiaomiSampleProvider provider;
    private long deviceId;
    private long userId;
    private int windowStartSeconds;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        gbDevice = createDummyGDevice(ADDRESS);
        final Device device = DBHelper.getDevice(gbDevice, daoSession);
        final User user = DBHelper.getUser(daoSession);
        deviceId = device.getId();
        userId = user.getId();

        provider = new XiaomiSampleProvider(gbDevice, daoSession);
        windowStartSeconds = (int) (System.currentTimeMillis() / 1000) - 2 * WINDOW_SECONDS;
    }

    private void storeSamples(final int... timestamps) {
        for (final int timestamp : timestamps) {
            storeSample(timestamp, STORED_CALORIES, ActivitySample.NOT_MEASURED);
        }
    }

    private void storeSample(final int timestamp, final int calories, final int steps) {
        final XiaomiActivitySample sample = provider.createActivitySample();
        sample.setDeviceId(deviceId);
        sample.setUserId(userId);
        sample.setTimestamp(timestamp);
        sample.setRawKind(ActivitySample.NOT_MEASURED);
        sample.setRawIntensity(ActivitySample.NOT_MEASURED);
        sample.setSteps(steps);
        sample.setHeartRate(ActivitySample.NOT_MEASURED);
        sample.setDistanceCm(ActivitySample.NOT_MEASURED);
        sample.setEnergy(ActivitySample.NOT_MEASURED);
        sample.setActiveCalories(calories);
        provider.addGBActivitySamples(new ArrayList<>(List.of(sample)));
    }

    private void storeWindow(final int startSeconds) {
        storeWindow(gbDevice, startSeconds);
    }

    private void storeWindow(final GBDevice device, final int startSeconds) {
        final GenericDurationSampleProvider windows = new GenericDurationSampleProvider(device, daoSession);
        final GenericDurationSample window = windows.createSample();
        window.setTimestamp(startSeconds * 1000L);
        window.setEndTimestamp((startSeconds + WINDOW_SECONDS) * 1000L);
        window.setType(XiaomiSampleProvider.DURATION_TYPE_SLEEP_AS_ANDROID);
        Assert.assertTrue(windows.persistSamples(window, null));
    }

    private void storeWorkout(final int startSeconds, final int endSeconds) {
        final BaseActivitySummary workout = new BaseActivitySummary();
        workout.setDeviceId(deviceId);
        workout.setUserId(userId);
        workout.setName("Morning run");
        workout.setActivityKind(ActivityKind.RUNNING.getCode());
        workout.setStartTime(new Date(startSeconds * 1000L));
        workout.setEndTime(new Date(endSeconds * 1000L));
        daoSession.getBaseActivitySummaryDao().insert(workout);
    }

    private int caloriesAt(final int timestamp) {
        final List<XiaomiActivitySample> samples = provider.getAllActivitySamples(timestamp, timestamp);
        Assert.assertEquals("one sample at " + timestamp, 1, samples.size());
        return samples.get(0).getActiveCalories();
    }

    @Test
    public void caloriesInsideTheWindowAreDropped() {
        storeSamples(windowStartSeconds + 1800);
        storeWindow(windowStartSeconds);

        Assert.assertEquals(0, caloriesAt(windowStartSeconds + 1800));
    }

    @Test
    public void caloriesOutsideTheWindowSurvive() {
        storeSamples(windowStartSeconds - 60, windowStartSeconds + WINDOW_SECONDS + 60);
        storeWindow(windowStartSeconds);

        Assert.assertEquals(READ_CALORIES, caloriesAt(windowStartSeconds - 60));
        Assert.assertEquals(READ_CALORIES, caloriesAt(windowStartSeconds + WINDOW_SECONDS + 60));
    }

    @Test
    public void bothEndsOfTheWindowAreCovered() {
        storeSamples(windowStartSeconds, windowStartSeconds + WINDOW_SECONDS);
        storeWindow(windowStartSeconds);

        Assert.assertEquals(0, caloriesAt(windowStartSeconds));
        Assert.assertEquals(0, caloriesAt(windowStartSeconds + WINDOW_SECONDS));
    }

    @Test
    public void withoutAWindowNothingIsDropped() {
        storeSamples(windowStartSeconds + 1800);

        Assert.assertEquals(READ_CALORIES, caloriesAt(windowStartSeconds + 1800));
    }

    @Test
    public void anEarlierNightIsStillCovered() {
        final int earlierNight = windowStartSeconds - 86400;
        storeSamples(windowStartSeconds + 1800, earlierNight + 1800, earlierNight + WINDOW_SECONDS + 60);
        storeWindow(earlierNight);
        storeWindow(windowStartSeconds);

        Assert.assertEquals(0, caloriesAt(windowStartSeconds + 1800));
        Assert.assertEquals(0, caloriesAt(earlierNight + 1800));
        Assert.assertEquals(READ_CALORIES, caloriesAt(earlierNight + WINDOW_SECONDS + 60));
    }

    @Test
    public void aNightMonthsAgoIsStillCovered() {
        final int monthsAgo = windowStartSeconds - 90 * 86400;
        storeSamples(monthsAgo + 1800);
        storeWindow(monthsAgo);

        Assert.assertEquals(0, caloriesAt(monthsAgo + 1800));
    }

    @Test
    public void aWindowOfAnotherDeviceIsIgnored() {
        final GBDevice otherDevice = createDummyGDevice("66:77:88:99:AA:BB");
        DBHelper.getDevice(otherDevice, daoSession);
        storeSamples(windowStartSeconds + 1800);
        storeWindow(otherDevice, windowStartSeconds);

        Assert.assertEquals(READ_CALORIES, caloriesAt(windowStartSeconds + 1800));
    }

    /**
     * A session the band never closed is only closed on the next connection, so its window can
     * reach well past the morning and over a workout the wearer really did.
     */
    @Test
    public void aWorkoutInsideTheWindowKeepsItsCalories() {
        final int workoutStart = windowStartSeconds + 1800;
        storeSamples(windowStartSeconds + 600, workoutStart + 300);
        storeWorkout(workoutStart, workoutStart + 900);
        storeWindow(windowStartSeconds);

        Assert.assertEquals("the sleeping minutes are the band's invention",
                0, caloriesAt(windowStartSeconds + 600));
        Assert.assertEquals("the workout's minutes were earned",
                READ_CALORIES, caloriesAt(workoutStart + 300));
    }

    @Test
    public void aWorkoutOnAnotherDeviceDoesNotShieldTheWindow() {
        final GBDevice otherDevice = createDummyGDevice("66:77:88:99:AA:BB");
        final Device otherRow = DBHelper.getDevice(otherDevice, daoSession);

        final int workoutStart = windowStartSeconds + 1800;
        storeSamples(workoutStart + 300);

        final BaseActivitySummary workout = new BaseActivitySummary();
        workout.setDeviceId(otherRow.getId());
        workout.setUserId(userId);
        workout.setName("Someone else's run");
        workout.setActivityKind(ActivityKind.RUNNING.getCode());
        workout.setStartTime(new Date(workoutStart * 1000L));
        workout.setEndTime(new Date((workoutStart + 900) * 1000L));
        daoSession.getBaseActivitySummaryDao().insert(workout);

        storeWindow(windowStartSeconds);

        Assert.assertEquals(0, caloriesAt(workoutStart + 300));
    }

    @Test
    public void aWorkoutOutsideEveryWindowIsUntouched() {
        final int workoutStart = windowStartSeconds + WINDOW_SECONDS + 600;
        storeSamples(workoutStart + 300);
        storeWorkout(workoutStart, workoutStart + 900);
        storeWindow(windowStartSeconds);

        Assert.assertEquals(READ_CALORIES, caloriesAt(workoutStart + 300));
    }

    /**
     * The shape a Mi Band 10 really produces: 5 kcal every minute of the session while the wearer
     * is asleep and still, and nothing at all for a minute of genuine walking later that day.
     */
    @Test
    public void theNightIsTheOnlyThingTheBandBilled() {
        final int asleep = windowStartSeconds + 600;
        final int walking = windowStartSeconds + WINDOW_SECONDS + 3600;
        storeSample(asleep, 5, 0);
        storeSample(walking, 0, 102);
        storeWindow(windowStartSeconds);

        Assert.assertEquals("the sleeping minute was the band's own invention",
                0, caloriesAt(asleep));
        Assert.assertEquals("walking earned nothing to begin with",
                0, caloriesAt(walking));
    }
}
