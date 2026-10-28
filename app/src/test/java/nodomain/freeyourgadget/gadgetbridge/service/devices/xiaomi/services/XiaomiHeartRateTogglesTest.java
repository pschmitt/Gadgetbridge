/*  Copyright (C) 2026 beng1z, Dany Mestas

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
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Health;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.HeartRate;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiPreferences;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The irregular rhythm alert and sleep breathing rate fields of the heart rate config carry
 * 0 when the watch has no such toggle, 1 when it is on and 2 when it is off.
 */
public class XiaomiHeartRateTogglesTest extends TestBase {
    /** Subtype of the heart rate config reply, {@code CMD_CONFIG_HEART_RATE_GET}. */
    private static final int HEART_RATE_CONFIG_REPLY = 10;

    private final List<GBDeviceEvent> events = new ArrayList<>();
    private final List<XiaomiProto.Command> sent = new ArrayList<>();
    private GBDevice device;
    private XiaomiHealthService service;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        device = createDummyGDevice("AA:BB:CC:DD:EE:01");
        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).edit().clear().commit();

        final XiaomiSupport support = mock(XiaomiSupport.class);
        when(support.getDevice()).thenReturn(device);
        doAnswer(invocation -> {
            events.add(invocation.getArgument(0));
            return null;
        }).when(support).evaluateGBDeviceEvent(any(GBDeviceEvent.class));
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(1));
            return null;
        }).when(support).sendCommand(anyString(), any(XiaomiProto.Command.class));

        service = new XiaomiHealthService(support);
    }

    @Test
    public void watchWithoutTheTogglesReportsZeroAndHidesThem() {
        final Map<String, Object> prefs = receive(HeartRate.newBuilder().setDisabled(false));

        assertEquals(false, prefs.get(XiaomiPreferences.FEAT_IRREGULAR_RHYTHM_ALERT));
        assertEquals(false, prefs.get(XiaomiPreferences.FEAT_SLEEP_RESPIRATORY_RATE));
    }

    @Test
    public void reportedValuesMapToFeatureAndState() {
        final Map<String, Object> prefs = receive(HeartRate.newBuilder()
                .setDisabled(false)
                .setIrregularRhythmAlert(1)
                .setSleepRespiratoryRate(2));

        assertEquals(true, prefs.get(XiaomiPreferences.FEAT_IRREGULAR_RHYTHM_ALERT));
        assertEquals(true, prefs.get(DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_IRREGULAR_RHYTHM));
        assertEquals(true, prefs.get(XiaomiPreferences.FEAT_SLEEP_RESPIRATORY_RATE));
        assertEquals(false, prefs.get(DeviceSettingsPreferenceConst.PREF_HEARTRATE_SLEEP_RESPIRATORY_RATE_MONITORING));
    }

    @Test
    public void supportedTogglesAreWrittenAsOneOrTwo() {
        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).edit()
                .putBoolean(XiaomiPreferences.FEAT_IRREGULAR_RHYTHM_ALERT, true)
                .putBoolean(XiaomiPreferences.FEAT_SLEEP_RESPIRATORY_RATE, true)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_IRREGULAR_RHYTHM, true)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_SLEEP_RESPIRATORY_RATE_MONITORING, false)
                .commit();

        service.setHeartRateConfig();

        final HeartRate heartRate = lastSentHeartRate();
        assertEquals(1, heartRate.getIrregularRhythmAlert());
        assertEquals(2, heartRate.getSleepRespiratoryRate());
    }

    @Test
    public void unsupportedTogglesAreNotWritten() {
        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).edit()
                .putBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_ALERT_IRREGULAR_RHYTHM, true)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_HEARTRATE_SLEEP_RESPIRATORY_RATE_MONITORING, true)
                .commit();

        service.setHeartRateConfig();

        final HeartRate heartRate = lastSentHeartRate();
        assertFalse(heartRate.hasIrregularRhythmAlert());
        assertFalse(heartRate.hasSleepRespiratoryRate());
    }

    private Map<String, Object> receive(final HeartRate.Builder heartRate) {
        service.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(XiaomiHealthService.COMMAND_TYPE)
                .setSubtype(HEART_RATE_CONFIG_REPLY)
                .setHealth(Health.newBuilder().setHeartRate(heartRate))
                .build());

        for (final GBDeviceEvent event : events) {
            if (event instanceof GBDeviceEventUpdatePreferences) {
                return ((GBDeviceEventUpdatePreferences) event).preferences;
            }
        }
        throw new AssertionError("the heart rate config reply did not update any preference");
    }

    private HeartRate lastSentHeartRate() {
        assertFalse("nothing was sent", sent.isEmpty());
        final XiaomiProto.Command command = sent.get(sent.size() - 1);
        assertNotNull(command.getHealth());
        return command.getHealth().getHeartRate();
    }
}
