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

import android.content.SharedPreferences;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.SoundcorePacket;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.*;

public class SoundcoreMotionPlusProtocol extends SoundcoreProtocolImplV1 {
    private static final int HEADER_LENGTH = 9;

    private static final short CMD_SET_VOLUME = (short) 0x8101;
    private static final short CMD_SET_EQUALIZER = (short) 0x8102;
    private static final short CMD_NOTIFY_EQUALIZER = (short) 0x0102;
    private static final short CMD_SET_CUSTOM_EQUALIZER = (short) 0x8302;

    private static final String[] EQUALIZER_BAND_PREFS = {
        PREF_SOUNDCORE_EQUALIZER_BAND1_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND2_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND3_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND4_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND5_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND6_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND7_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND8_VALUE,
        PREF_SOUNDCORE_EQUALIZER_BAND9_VALUE
    };

    public SoundcoreMotionPlusProtocol(final GBDevice device) {
        super(device);
    }

    // Soundcore Packet reimplementation:
    // This device does not have checksum byte at the end.
    // ---------------
    @Override
    protected byte[] encodeRequest(final short command) {
        return encodeCommand(command, new byte[0]);
    }

    @Override
    protected byte[] encodeCommand(final short command, final byte[] payload) {
        // Motion+ frames have no proprietary checksum.
        return ByteBuffer.allocate(HEADER_LENGTH + payload.length)
                .put(new byte[]{0x08, (byte) 0xee, 0x00, 0x00, 0x00})
                .put(BLETypeConversions.fromUint16(command & 0xffff))
                .put(BLETypeConversions.fromUint16(HEADER_LENGTH + payload.length))
                .put(payload)
                .array();
    }

    @Override
    protected SoundcorePacket decodePacket(final byte[] data) {
        if (data == null || data.length < HEADER_LENGTH
                || data[0] != 0x09 || data[1] != (byte) 0xff
                || data[2] != 0x00 || data[3] != 0x00 || data[4] != 0x01) {
            return null;
        }
        final int length = BLETypeConversions.toUint16(data, 7);
        if (length != data.length) {
            return null;
        }
        return new SoundcorePacket((short) BLETypeConversions.toUint16(data, 5),
                Arrays.copyOfRange(data, HEADER_LENGTH, length));
    }
    // ---------------

    @Override
    protected GBDeviceEvent[] decodeCommand(final short command, final byte[] payload) {
        if (command == CMD_NOTIFY_EQUALIZER) {
            return new GBDeviceEvent[]{new GBDeviceEventUpdatePreferences(
                    PREF_SOUNDCORE_EQUALIZER_PRESET, presetPreference(payload[0]))};
        }
        return super.decodeCommand(command, payload);
    }

    @Override
    public byte[] encodeSendConfiguration(final String config) {
        switch (config) {
            case PREF_VOLUME:
                final int volume = getDevicePrefs().getInt(PREF_VOLUME, 8);
                return encodeByteCommand(CMD_SET_VOLUME, volume);
            case PREF_SOUNDCORE_EQUALIZER_BAND1_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND2_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND3_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND4_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND5_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND6_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND7_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND8_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND9_VALUE:
                return encodeCustomEqualizer();
            case PREF_SOUNDCORE_EQUALIZER_PRESET:
                // CUSTOM preset ID is sent as FF (255), not FE (254),
                // incompatible with current list of presets.
                int preset = Integer.parseInt(getDevicePrefs().getString(config, "0"));
                if (preset == 254) { preset = 255; }
                return encodeByteCommand(CMD_SET_EQUALIZER, preset);
            default:
                return super.encodeSendConfiguration(config);
        }
    }

    /*
        09 ff 00 00 01 01 01 2d 00 00 04 03 00 03 [34 35
        2e 34 39] [41 43 43 52 37 4c 31 43 32 39 39 39 39
        39 39 39] [00] [08 08 04 06 04 07 08 0b 09]
    */
    @Override
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        if (payload == null || payload.length < 36) {
            return new GBDeviceEvent[0];
        }
        final Map<String, Object> preferences = new HashMap<>();
        final int volume = Math.max(Byte.toUnsignedInt(payload[2]), 16);
        preferences.put(PREF_VOLUME, volume);
        decodeEqualizer(Arrays.copyOfRange(payload, 26, 36));
        return new GBDeviceEvent[]{
                buildVersionInfo(payload, 5),
                handleBatteryInfo(payload[1], 0)[0],
                new GBDeviceEventUpdatePreferences(preferences)
        };
    }

    /** Decodes the preset selector followed by the nine stored custom-band gains. */
    private void decodeEqualizer(final byte[] payload) {
        if (payload == null || payload.length != EQUALIZER_BAND_PREFS.length + 1) {
            return;
        }
        final SharedPreferences.Editor editor = getDevicePrefs().getPreferences().edit();
        editor.putString(PREF_SOUNDCORE_EQUALIZER_PRESET, presetPreference(payload[0]));
        for (int band = 0; band < EQUALIZER_BAND_PREFS.length; band++) {
            final int gain = Byte.toUnsignedInt(payload[band + 1]);
            if (gain <= 12) {
                editor.putInt(EQUALIZER_BAND_PREFS[band], gain - 6);
            }
        }
        editor.apply();
    }

    private byte[] encodeCustomEqualizer() {
        final Prefs prefs = getDevicePrefs();
        final byte[] gains = new byte[EQUALIZER_BAND_PREFS.length];
        for (int band = 0; band < gains.length; band++) {
            final int gain = prefs.getInt(EQUALIZER_BAND_PREFS[band], 0);
            if (gain < -6 || gain > 6) {
                return null;
            }
            // Wire values 0..12 (0x0C) represent -6..6 dB; 6 is flat.
            gains[band] = (byte) (gain + 6);
        }
        return encodeCommand(CMD_SET_CUSTOM_EQUALIZER, gains);
    }

    private String presetPreference(final byte value) {
        final int preset = Byte.toUnsignedInt(value);
        return String.valueOf(preset == 255 ? 254 : preset);
    }

    @Override
    protected GBDeviceEvent[] decodeBatteryInfo(final byte[] payload) {
        if (payload == null || payload.length != 1) {
            return new GBDeviceEvent[0];
        }
        return handleBatteryInfo(payload[0], 0);
    }
}
