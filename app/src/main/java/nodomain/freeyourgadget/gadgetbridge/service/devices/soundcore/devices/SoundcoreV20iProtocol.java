/*  Copyright (C) 2026 David Giron

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices;

import android.graphics.Color;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class SoundcoreV20iProtocol extends SoundcoreProtocolImplV1 {
    // NOTE: different than Motion 300, uses same code as SET_TOUCH_TONE.
    private static final short CMD_SET_VOICE_PROMPTS = (short) 0x8301;

    public static final short CMD_SET_AUDIO_BALANCE = (short) 0x8c10;
    public static final short CMD_SET_LIGHT_INTENSITY = (short) 0x820e;
    public static final short CMD_SET_LIGHT_AUTO_OFF = (short) 0x840e;
    public static final short CMD_SET_LIGHT_COLOR = (short) 0x850e;
    public static final short CMD_SET_LIGHT_MODE = (short) 0x860e;
    public static final short CMD_SET_LIGHTS_ENABLED = (short) 0x870e;

    private static final int LEFT_LONG_PRESS_OFFSET = 65;

    public SoundcoreV20iProtocol(final GBDevice device) {
        super(device);
        updateLedColor();
    }

    @Override
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        final GBDeviceEvent[] events = decodeStandardEarbudDeviceInfo(payload, 4);
        if (events.length == 0) {
            return events;
        }

        // TODO CHECK
        // payload[61] -> SoundcoreEarbudsCoordinator.ButtonAction.DOUBLE_TAP
        // payload[65] -> SoundcoreEarbudsCoordinator.ButtonAction.LONG_PRESS

        if (payload.length > LEFT_LONG_PRESS_OFFSET) {
            final SoundcoreEarbudsCoordinator.ButtonAction action = SoundcoreEarbudsCoordinator.ButtonAction.LONG_PRESS;
            final int encodedFunction = Byte.toUnsignedInt(payload[LEFT_LONG_PRESS_OFFSET]);
            if ((encodedFunction >>> 4) == getTapFunctionGroup(action)) {
                for (final SoundcoreEarbudsCoordinator.TapFunction function : SoundcoreEarbudsCoordinator.TapFunction.values()) {
                    if (function.getCode() == (encodedFunction & 0x0f)) {
                        getDevicePrefs().getPreferences().edit().putString(
                                action.getLeftPreference(), function.getValue()).apply();
                        break;
                    }
                }
            }
        }

        return events;
    }

    @Override
    protected int[] getBatteryIndexes(final int payloadLength) {
        return new int[]{0, 1};
    }

    @Override
    protected GBDeviceEvent[] decodeCommand(final short command, final byte[] payload) {
        // Setter replies acknowledge receipt, not the resulting device state.
        return new GBDeviceEvent[0];
    }

    @Override
    public byte[] encodeSendConfiguration(final String config) {
        final Prefs prefs = getDevicePrefs();
        switch (config) {
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_VOICE_PROMPTS:
                return encodeBooleanCommand(CMD_SET_VOICE_PROMPTS, prefs.getBoolean(config, true));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUDIO_BALANCE: // -6 .. 6
                return encodeByteCommand(CMD_SET_AUDIO_BALANCE, (byte) (100 + prefs.getInt(config, 0)));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_ON:
                updateLedColor();
                getDevice().sendDeviceUpdateIntent(GBApplication.getContext());
                return encodeBooleanCommand(CMD_SET_LIGHTS_ENABLED, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_BRIGHTNESS: // 1-10
                return encodeByteCommand(CMD_SET_LIGHT_INTENSITY, prefs.getInt(config, 5));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_AUTO_OFF:  // 1-120
                return encodeByteCommand(CMD_SET_LIGHT_AUTO_OFF, prefs.getInt(config, 45));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_RED:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_GREEN:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_BLUE:
                final int red = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_RED, 23);
                final int green = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_GREEN, 187);
                final int blue = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_BLUE, 239);
                if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) {
                    return null;
                }
                final byte[] command = encodeLedColor((red << 16) | (green << 8) | blue);
                getDevice().sendDeviceUpdateIntent(GBApplication.getContext());
                return command;
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_MODE:
                final int mode = Integer.parseInt(prefs.getString(config, "0"));
                return encodeByteCommand(CMD_SET_LIGHT_MODE, (byte) mode);
            default:
                return super.encodeSendConfiguration(config);
        }
    }

    @Override
    public byte[] encodeLedColor(final int color) {
        getDevicePrefs().getPreferences().edit()
                .putInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_RED, Color.red(color))
                .putInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_GREEN, Color.green(color))
                .putInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_BLUE, Color.blue(color))
                .apply();
        updateLedColor();
        return encodeCommand(CMD_SET_LIGHT_COLOR, new byte[]{
                (byte) (color >> 16), (byte) (color >> 8), (byte) color
        });
    }

    private void updateLedColor() {
        final Prefs prefs = getDevicePrefs();
        final boolean enabled = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_ON, false);
        final int red = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_RED, 23);
        final int green = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_GREEN, 187);
        final int blue = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LIGHT_COLOR_BLUE, 239);
        // This is the configured color, not device readback.
        getDevice().setExtraInfo("led_color", enabled ? Color.rgb(red, green, blue) : null);
    }

    @Override
    protected int getTapFunctionGroup(final SoundcoreEarbudsCoordinator.ButtonAction action) {
        return action == SoundcoreEarbudsCoordinator.ButtonAction.DOUBLE_TAP ? 6 : 15;
    }
}
