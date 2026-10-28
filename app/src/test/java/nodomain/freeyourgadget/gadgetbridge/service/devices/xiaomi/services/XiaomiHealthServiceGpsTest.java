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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Looper;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.externalevents.gps.GBLocationService;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutLocation;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenReply;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutOpenWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatsWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.WorkoutStatusWatch;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.SleepAsAndroidSender;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Sending the phone's location to the band during a workout the band started. The band asks for
 * location with an open request, confirms the workout with a status, and expects one location
 * per phone fix until it reports the workout finished.
 */
public class XiaomiHealthServiceGpsTest extends TestBase {
    private static final int CMD_WORKOUT_STATUS = 26;
    private static final int CMD_WORKOUT_OPEN = 30;
    private static final int CMD_WORKOUT_LOCATION = 48;
    private static final int CMD_WORKOUT_LIVE_STATS = 50;

    private static final int STATUS_STARTED = 0;
    private static final int STATUS_FINISHED = 3;

    private static final int OPEN_OK = 0;
    private static final int OPEN_NO_PERMISSION = 3;
    private static final int ACCURACY_HIGH = 2;

    private static final int SPORT_OUTDOOR_RUN = 1;
    private static final int OPEN_TIMEOUT_MS = 5000;

    private GBDevice device;
    private XiaomiSupport support;
    private XiaomiHealthService service;
    private final List<XiaomiProto.Command> sent = new ArrayList<>();
    private final List<String> locationActions = new ArrayList<>();
    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            locationActions.add(intent.getAction());
        }
    };

    @Before
    public void setUpService() {
        device = createDummyGDevice("00:00:00:00:00:58");
        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).edit()
                .putBoolean(DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND, true)
                .putString(DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND_TIMEOUT, String.valueOf(OPEN_TIMEOUT_MS))
                .commit();
        setGpsEnabled(true);

        support = mock(XiaomiSupport.class);
        when(support.getDevice()).thenReturn(device);
        when(support.getContext()).thenReturn(getContext());
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(1));
            return null;
        }).when(support).sendCommand(anyString(), any(XiaomiProto.Command.class));

        final IntentFilter filter = new IntentFilter();
        filter.addAction(GBLocationService.ACTION_START);
        filter.addAction(GBLocationService.ACTION_STOP);
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(locationReceiver, filter);

        service = newService();
    }

    @After
    public void tearDownReceiver() {
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(locationReceiver);
    }

    @Test
    public void openRequestWithoutStartStopsLocationAfterTimeout() {
        bandOpens();
        idle(OPEN_TIMEOUT_MS - 100);
        assertLocationActions(GBLocationService.ACTION_START);

        idle(200);
        assertLocationActions(GBLocationService.ACTION_START, GBLocationService.ACTION_STOP);
    }

    @Test
    public void fixBeforeStartAnswersTheOpenRequestOnly() {
        bandOpens();
        phoneFix(1);

        assertEquals(0, locationsSent());
        final List<WorkoutOpenReply> replies = openReplies();
        assertEquals(1, replies.size());
        assertEquals(OPEN_OK, replies.get(0).getCode());
        assertEquals(ACCURACY_HIGH, replies.get(0).getGpsAccuracy());
    }

    @Test
    public void everyFixIsSentOnceTheWorkoutStarted() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        phoneFix(1);
        phoneFix(2);

        final List<WorkoutLocation> locations = sentLocations();
        assertEquals(2, locations.size());
        assertEquals(48.2, locations.get(1).getLatitude(), 1e-9);
        assertEquals(11.4, locations.get(1).getLongitude(), 1e-9);
        assertEquals(1_700_000_002, locations.get(1).getTimestamp());
    }

    @Test
    public void startedWorkoutKeepsLocationWhenOpenRequestsStop() {
        // some firmwares repeat the open request for a while after the start, then go quiet
        bandOpens();
        bandStatus(STATUS_STARTED);
        bandOpens();
        bandOpens();
        idle(OPEN_TIMEOUT_MS * 3);

        assertLocationActions(GBLocationService.ACTION_START);
        phoneFix(1);
        assertEquals(1, locationsSent());
    }

    @Test
    public void finishedWorkoutStopsLocation() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        bandStatus(STATUS_FINISHED);
        idle(0);

        assertLocationActions(GBLocationService.ACTION_START, GBLocationService.ACTION_STOP);
    }

    @Test
    public void disabledSettingRefusesTheOpenRequest() {
        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).edit()
                .putBoolean(DeviceSettingsPreferenceConst.PREF_WORKOUT_SEND_GPS_TO_BAND, false)
                .commit();
        bandOpens();
        idle(0);

        assertEquals(OPEN_NO_PERMISSION, openReplies().get(0).getCode());
        assertLocationActions();
    }

    @Test
    public void disabledPhoneGpsRefusesTheOpenRequest() {
        setGpsEnabled(false);
        bandOpens();
        idle(0);

        assertEquals(OPEN_NO_PERMISSION, openReplies().get(0).getCode());
        assertLocationActions();
    }

    @Test
    public void reconnectDuringWorkoutKeepsSendingLocation() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        bandLiveStats();
        phoneFix(1);

        // the support keeps this service across a reconnect and initializes it again
        service.onDisconnect();
        service.initialize();
        bandLiveStats();
        phoneFix(2);
        phoneFix(3);

        assertEquals(3, locationsSent());
    }

    @Test
    public void freshConnectionToARunningWorkoutResumesLocation() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        service.dispose();

        service = newService();
        service.initialize();
        bandOpens();
        for (int i = 0; i < 3 * OPEN_TIMEOUT_MS / 1000; i++) {
            bandLiveStats();
            idle(1000);
        }
        phoneFix(1);

        assertEquals(1, locationsSent());
        assertTrue(locationRunning());
        assertNoStackedStart();
    }

    @Test
    public void liveStatsKeepALongWorkoutRunning() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        for (int i = 0; i < 180; i++) {
            bandLiveStats();
            idle(1000);
        }
        phoneFix(1);

        assertTrue(locationRunning());
        assertEquals(1, locationsSent());
    }

    @Test
    public void liveStatsAfterAQuietSpellStartLocationAgain() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        bandLiveStats();
        idle(120_000);
        assertFalse(locationRunning());

        bandLiveStats();
        phoneFix(1);

        assertTrue(locationRunning());
        assertEquals(1, locationsSent());
    }

    @Test
    public void workoutThatEndedWhileDisconnectedStopsLocation() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        bandLiveStats();

        service.onDisconnect();
        service.initialize();
        idle(120_000);
        phoneFix(1);

        assertFalse(locationRunning());
        assertEquals(0, locationsSent());
    }

    @Test
    public void reconnectWithoutWorkoutEvidenceStopsLocation() {
        // a firmware that never pushes live stats gives no way to tell whether the workout survived
        bandOpens();
        bandStatus(STATUS_STARTED);

        service.onDisconnect();
        service.initialize();
        idle(0);

        assertFalse(locationRunning());
    }

    @Test
    public void liveStatsWithoutLocationRequestDoNotStartLocation() {
        // an indoor workout already running when the connection comes up
        for (int i = 0; i < 10; i++) {
            bandLiveStats();
            idle(1000);
        }

        assertFalse(locationRunning());
        assertTrue(locationActions.isEmpty());
    }

    @Test
    public void indoorWorkoutStartedWhileConnectedNeverStartsLocation() {
        bandStatus(STATUS_STARTED);
        bandLiveStats();
        idle(120_000);
        bandLiveStats();
        idle(1000);

        assertFalse(locationRunning());
        assertTrue(locationActions.isEmpty());
    }

    @Test
    public void indoorWorkoutAfterAnUnfinishedOutdoorOneDoesNotResumeLocation() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        bandLiveStats();
        // the outdoor workout ends while disconnected, so no finished status arrives
        service.onDisconnect();
        service.initialize();
        idle(120_000);
        assertFalse(locationRunning());

        bandStatus(STATUS_STARTED);
        bandLiveStats();
        idle(120_000);
        bandLiveStats();
        idle(1000);

        assertFalse(locationRunning());
    }

    @Test
    public void indoorWorkoutEndedWhileDisconnectedKeepsTheOpenRequestTimeout() {
        bandStatus(STATUS_STARTED);
        // the indoor workout ends while disconnected, so no finished status arrives
        service.onDisconnect();
        service.initialize();

        bandOpens();
        idle(OPEN_TIMEOUT_MS + 100);

        assertFalse(locationRunning());
    }

    @Test
    public void indoorWorkoutAfterAnUnansweredOpenRequestDoesNotStartLocation() {
        bandOpens();
        idle(OPEN_TIMEOUT_MS + 100);
        assertFalse(locationRunning());

        bandStatus(STATUS_STARTED);
        bandLiveStats();
        idle(120_000);
        bandLiveStats();
        idle(1000);

        assertFalse(locationRunning());
    }

    @Test
    public void disposeStopsLocation() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        service.dispose();

        assertFalse(locationRunning());
    }

    @Test
    public void finishedWorkoutDoesNotLeakIntoTheNextOne() {
        bandOpens();
        bandStatus(STATUS_STARTED);
        bandStatus(STATUS_FINISHED);

        bandOpens();
        phoneFix(1);
        idle(OPEN_TIMEOUT_MS + 100);

        assertEquals("no location before the band confirms the new workout", 0, locationsSent());
        assertFalse("the open request timed out", locationRunning());
    }

    @Test
    public void sleepSessionStatsNeverStartLocation() {
        service.getSleepAsAndroidManager().setSender(mock(SleepAsAndroidSender.class));
        service.getSleepAsAndroidManager().start(false);
        // before and after the synthetic workout is open
        bandLiveStats();
        idle(1000);
        bandLiveStats();
        idle(1000);

        assertFalse(locationRunning());
        assertTrue(locationActions.isEmpty());
    }

    private XiaomiHealthService newService() {
        return new XiaomiHealthService(support);
    }

    private void setGpsEnabled(final boolean enabled) {
        final LocationManager locationManager = (LocationManager) getContext().getSystemService(Context.LOCATION_SERVICE);
        shadowOf(locationManager).setProviderEnabled(LocationManager.GPS_PROVIDER, enabled);
    }

    private void idle(final long ms) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms));
    }

    private void bandOpens() {
        service.handleCommand(command(CMD_WORKOUT_OPEN, Health.newBuilder()
                .setWorkoutOpenWatch(WorkoutOpenWatch.newBuilder()
                        .setSport(SPORT_OUTDOOR_RUN)
                        .setSupportedVersions(2)
                        .setMainSport(SPORT_OUTDOOR_RUN))));
    }

    private void bandStatus(final int status) {
        service.handleCommand(command(CMD_WORKOUT_STATUS, Health.newBuilder()
                .setWorkoutStatusWatch(WorkoutStatusWatch.newBuilder()
                        .setSport(SPORT_OUTDOOR_RUN)
                        .setStatus(status))));
    }

    private void bandLiveStats() {
        service.handleCommand(command(CMD_WORKOUT_LIVE_STATS, Health.newBuilder()
                .setWorkoutStatsWatch(WorkoutStatsWatch.newBuilder()
                        .setHeartRate(130)
                        .setCalories(20)
                        .setSteps(900)
                        .setDistance(750))));
    }

    private void phoneFix(final int second) {
        final Location location = new Location(LocationManager.GPS_PROVIDER);
        location.setLatitude(48.0 + second / 10.0);
        location.setLongitude(11.0 + second / 5.0);
        location.setTime((1_700_000_000L + second) * 1000L);
        service.onSetGpsLocation(location);
    }

    private static XiaomiProto.Command command(final int subtype, final Health.Builder health) {
        return XiaomiProto.Command.newBuilder()
                .setType(XiaomiHealthService.COMMAND_TYPE)
                .setSubtype(subtype)
                .setHealth(health)
                .build();
    }

    private List<WorkoutOpenReply> openReplies() {
        final List<WorkoutOpenReply> replies = new ArrayList<>();
        for (final XiaomiProto.Command cmd : sent) {
            if (cmd.getSubtype() == CMD_WORKOUT_OPEN && cmd.getHealth().hasWorkoutOpenReply()) {
                replies.add(cmd.getHealth().getWorkoutOpenReply());
            }
        }
        return replies;
    }

    private List<WorkoutLocation> sentLocations() {
        final List<WorkoutLocation> locations = new ArrayList<>();
        for (final XiaomiProto.Command cmd : sent) {
            if (cmd.getSubtype() == CMD_WORKOUT_LOCATION) {
                locations.add(cmd.getHealth().getWorkoutLocation());
            }
        }
        return locations;
    }

    private int locationsSent() {
        return sentLocations().size();
    }

    /** A stop ends every provider of the device, so location runs if the last action was a start. */
    private boolean locationRunning() {
        idle(0);
        return !locationActions.isEmpty()
                && GBLocationService.ACTION_START.equals(locationActions.get(locationActions.size() - 1));
    }

    /** Each start adds a provider, so a second start without a stop in between doubles every fix. */
    private void assertNoStackedStart() {
        idle(0);
        for (int i = 1; i < locationActions.size(); i++) {
            assertTrue("location started twice: " + locationActions,
                    !GBLocationService.ACTION_START.equals(locationActions.get(i))
                            || !GBLocationService.ACTION_START.equals(locationActions.get(i - 1)));
        }
    }

    private void assertLocationActions(final String... expected) {
        idle(0);
        assertEquals(List.of(expected), locationActions);
    }
}
