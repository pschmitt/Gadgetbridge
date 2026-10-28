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
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.SpO2;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Spo2AlarmLow;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.Spo2Mode;
import nodomain.freeyourgadget.gadgetbridge.proto.xiaomi.XiaomiProto;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiPreferences;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * A watch reporting a non-zero SpO2 interval offers a choice of off, sleep-only and, when
 * allDayCapable is set, all-day. A watch reporting interval 0 has a plain all-day switch.
 */
public class XiaomiSpo2ModeTest extends TestBase {
    /** Subtype of the SpO2 config reply, {@code CMD_CONFIG_SPO2_GET}. */
    private static final int SPO2_CONFIG_REPLY = 8;

    private final List<GBDeviceEvent> events = new ArrayList<>();
    private final List<XiaomiProto.Command> sent = new ArrayList<>();
    private GBDevice device;
    private XiaomiHealthService service;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        device = createDummyGDevice("AA:BB:CC:DD:EE:02");
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
    public void watchWithPlainSwitchOffersNoModeChoice() {
        final Map<String, Object> prefs = receive(SpO2.newBuilder()
                .setAllDayCapable(1)
                .setMode(Spo2Mode.SPO2_MODE_ALL_DAY)
                .setInterval(0));

        assertEquals(false, prefs.get(XiaomiPreferences.FEAT_SPO2_MODE_CHOICE));
        assertEquals(true, prefs.get(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING));
    }

    @Test
    public void watchWithIntervalOffersModesAndReportsAllDaySupport() {
        final Map<String, Object> prefs = receive(SpO2.newBuilder()
                .setAllDayCapable(0)
                .setMode(Spo2Mode.SPO2_MODE_SLEEP_ONLY)
                .setInterval(30));

        assertEquals(true, prefs.get(XiaomiPreferences.FEAT_SPO2_MODE_CHOICE));
        assertEquals(false, prefs.get(XiaomiPreferences.FEAT_SPO2_ALL_DAY));
        assertEquals("1", prefs.get(DeviceSettingsPreferenceConst.PREF_SPO2_MONITORING_MODE));
    }

    @Test
    public void chosenModeIsWrittenAndUnmanagedFieldsAreKept() {
        receive(SpO2.newBuilder()
                .setAllDayCapable(1)
                .setMode(Spo2Mode.SPO2_MODE_OFF)
                .setInterval(30)
                .setAlarmLow(Spo2AlarmLow.newBuilder().setAlarmLowEnabled(true).setAlarmLowThreshold(85)));
        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).edit()
                .putBoolean(XiaomiPreferences.FEAT_SPO2_MODE_CHOICE, true)
                .putString(DeviceSettingsPreferenceConst.PREF_SPO2_MONITORING_MODE, "1")
                .putString(DeviceSettingsPreferenceConst.PREF_SPO2_LOW_ALERT_THRESHOLD, "0")
                .commit();

        service.onSendConfiguration(DeviceSettingsPreferenceConst.PREF_SPO2_MONITORING_MODE, null);

        final SpO2 spo2 = lastSentSpo2();
        assertEquals(Spo2Mode.SPO2_MODE_SLEEP_ONLY, spo2.getMode());
        assertEquals(30, spo2.getInterval());
        assertEquals(1, spo2.getAllDayCapable());
        assertFalse(spo2.getAlarmLow().getAlarmLowEnabled());
        assertEquals(85, spo2.getAlarmLow().getAlarmLowThreshold());
    }

    @Test
    public void plainSwitchWatchGetsAllDayOrOff() {
        GBApplication.getDeviceSpecificSharedPrefs(device.getAddress()).edit()
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, true)
                .commit();

        service.onSendConfiguration(DeviceSettingsPreferenceConst.PREF_SPO2_ALL_DAY_MONITORING, null);

        final SpO2 spo2 = lastSentSpo2();
        assertEquals(Spo2Mode.SPO2_MODE_ALL_DAY, spo2.getMode());
        assertEquals(1, spo2.getAllDayCapable());
    }

    private Map<String, Object> receive(final SpO2.Builder spo2) {
        service.handleCommand(XiaomiProto.Command.newBuilder()
                .setType(XiaomiHealthService.COMMAND_TYPE)
                .setSubtype(SPO2_CONFIG_REPLY)
                .setHealth(Health.newBuilder().setSpo2(spo2))
                .build());

        for (final GBDeviceEvent event : events) {
            if (event instanceof GBDeviceEventUpdatePreferences) {
                return ((GBDeviceEventUpdatePreferences) event).preferences;
            }
        }
        throw new AssertionError("the SpO2 config reply did not update any preference");
    }

    private SpO2 lastSentSpo2() {
        assertFalse("nothing was sent", sent.isEmpty());
        return sent.get(sent.size() - 1).getHealth().getSpo2();
    }
}
