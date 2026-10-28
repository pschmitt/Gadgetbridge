/*  Copyright (C) 2024 Severin von Wnuck-Lipinski

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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventSendBytes;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdateDeviceState;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventUpdatePreferences;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.SoundcorePacket;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.*;

public class SoundcoreMotion300Protocol extends SoundcoreProtocolImplV1 {
    private static final Logger LOG = LoggerFactory.getLogger(SoundcoreMotion300Protocol.class);

    // Some of these commands are not used right now, they serve as documentation
    private static final short CMD_GET_LDAC_MODE = (short)0x7f01;
    private static final short CMD_GET_BUTTON_BRIGHTNESS = (short)0x9310;
    private static final short CMD_GET_EQUALIZER = (short)0x8902;
    private static final short CMD_GET_CURRENT_DIRECTION = (short)0x8c02;

    private static final short CMD_SET_EQUALIZER_CUSTOM = (short)0x8d02;

    private static final short CMD_NOTIFY_VOLUME_INFO = (short)0x0901;
    private static final short CMD_NOTIFY_PLAYBACK_INFO = (short)0x2101;
    private static final short CMD_NOTIFY_BASS_MODE = (short)0x8e02;


    public static final String[] EQUALIZER_PREFS_FREQ = new String[] {
        PREF_SOUNDCORE_EQUALIZER_BAND1_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND2_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND3_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND4_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND5_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND6_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND7_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND8_FREQ,
        PREF_SOUNDCORE_EQUALIZER_BAND9_FREQ
    };

    public static final String[] EQUALIZER_PREFS_VALUE = new String[] {
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

    private final GBDeviceEventBatteryInfo batteryInfo = new GBDeviceEventBatteryInfo();

    public SoundcoreMotion300Protocol(GBDevice device) {
        super(device);
    }

    @Override
    protected void decodeAudioMode(final byte[] payload) {
        // Motion 300 does not use the shared earbud audio-mode layout.
    }

    @Override
    public GBDeviceEvent[] decodeResponse(byte[] data) {
        final SoundcorePacket packet = decodePacket(data);

        if (packet == null)
            return null;

        short cmd = packet.getCommand();
        byte[] payload = packet.getPayload();

        switch (cmd) {
            case CMD_GET_DEVICE_INFO:
                return handlePacketDeviceInfo(payload);
            case CMD_GET_LDAC_MODE:
                return handlePacketLdacMode(payload);
            case CMD_GET_BUTTON_BRIGHTNESS:
                return handlePacketButtonBrightness(payload);
            case CMD_GET_EQUALIZER:
                return handlePacketEqualizer(payload);
            case CMD_SET_ADAPTIVE_DIRECTION:
                return handlePacketAdaptiveDirection(payload);
            case CMD_NOTIFY_BATTERY_INFO:
                return handlePacketBatteryInfo(payload);
            case CMD_NOTIFY_CHARGING_INFO:
                return handlePacketChargingInfo(payload);
        }

        return null;
    }

    private GBDeviceEvent[] handlePacketDeviceInfo(byte[] payload) {
        if (payload.length != 29)
            return null;

        ByteBuffer buf = ByteBuffer.wrap(payload);
        byte volume = buf.get();
        byte batteryLevel = buf.get();
        byte batteryCharging = buf.get();
        byte currentlyPlaying = buf.get();
        byte voicePrompts = buf.get();
        byte autoPowerOffEnabled = buf.get();
        byte autoPowerOffDuration = buf.get();
        final GBDeviceEventVersionInfo versionInfo = buildVersionInfo(payload, 7, 17);
        batteryInfo.state = batteryCharging == (byte)0x00 ? BatteryState.BATTERY_NORMAL : BatteryState.BATTERY_CHARGING;
        batteryInfo.level = decodeBatteryLevel(batteryLevel);

        LOG.debug(
            "Device info: volume={}, battery={}, charging={}, playing={}, prompts={}, autoOff={}, autoOffDuration={}",
            volume, batteryLevel, batteryCharging, currentlyPlaying, voicePrompts, autoPowerOffEnabled, autoPowerOffDuration
        );

        return new GBDeviceEvent[] {
            versionInfo,
            batteryInfo,
            new GBDeviceEventUpdatePreferences(PREF_SOUNDCORE_VOICE_PROMPTS, voicePrompts != (byte)0x00),
            new GBDeviceEventUpdatePreferences(PREF_SOUNDCORE_AUTO_POWER_OFF, String.valueOf(autoPowerOffDuration + 1)),
            new GBDeviceEventSendBytes(encodeRequest(CMD_GET_LDAC_MODE)),
        };
    }

    private GBDeviceEvent[] handlePacketLdacMode(byte[] payload) {
        if (payload.length != 1)
            return null;

        return new GBDeviceEvent[] {
            new GBDeviceEventUpdatePreferences(PREF_SOUNDCORE_LDAC_MODE, payload[0] != (byte)0x00),
            new GBDeviceEventSendBytes(encodeRequest(CMD_GET_BUTTON_BRIGHTNESS)),
        };
    }

    private GBDeviceEvent[] handlePacketButtonBrightness(byte[] payload) {
        if (payload.length != 1)
            return null;

        return new GBDeviceEvent[] {
            new GBDeviceEventUpdatePreferences(PREF_SOUNDCORE_BUTTON_BRIGHTNESS, String.valueOf(payload[0])),
            new GBDeviceEventSendBytes(encodeRequest(CMD_GET_EQUALIZER)),
        };
    }

    private GBDeviceEvent[] handlePacketEqualizer(byte[] payload) {
        if (payload.length != 57)
            return null;

        // Get direction chosen in custom equalizer preferences
        Prefs prefs = getDevicePrefs();
        int equalizerDirection = Integer.parseInt(prefs.getString(PREF_SOUNDCORE_EQUALIZER_DIRECTION, "0"));

        // Equalizer preset might be larger than 127 -> convert to unsigned int
        ByteBuffer buf = ByteBuffer.wrap(payload);
        byte adaptiveDirection = buf.get();
        byte currentDirection = buf.get();
        int equalizerPreset = Byte.toUnsignedInt(buf.get());
        byte[] equalizer = new byte[18];

        // Choose one of the three custom equalizer configurations
        buf.position(buf.position() + equalizerDirection * equalizer.length);
        buf.get(equalizer);

        LOG.debug(
            "Equalizer: adaptiveDirection={}, direction={}, preset={}",
            adaptiveDirection, currentDirection, equalizerPreset
        );

        Map<String, Object> newPrefs = equalizerToPrefs(equalizer);
        newPrefs.put(PREF_SOUNDCORE_ADAPTIVE_DIRECTION, adaptiveDirection != (byte)0x00);
        newPrefs.put(PREF_SOUNDCORE_EQUALIZER_PRESET, String.valueOf(equalizerPreset));

        return new GBDeviceEvent[] {
            new GBDeviceEventUpdatePreferences(newPrefs),
            new GBDeviceEventUpdateDeviceState(GBDevice.State.INITIALIZED),
        };
    }

    private GBDeviceEvent[] handlePacketAdaptiveDirection(byte[] payload) {
        if (payload.length != 0)
            return null;

        Prefs prefs = getDevicePrefs();

        // Ignore if adaptive direction is enabled
        if (prefs.getBoolean(PREF_SOUNDCORE_ADAPTIVE_DIRECTION, true))
            return null;

        // Set equalizer direction to "standing" and get current configuration
        return new GBDeviceEvent[] {
            new GBDeviceEventUpdatePreferences(PREF_SOUNDCORE_EQUALIZER_DIRECTION, "0"),
            new GBDeviceEventSendBytes(encodeRequest(CMD_GET_EQUALIZER)),
        };
    }

    private GBDeviceEvent[] handlePacketBatteryInfo(byte[] payload) {
        if (payload.length != 1)
            return null;

        batteryInfo.level = decodeBatteryLevel(payload[0]);

        return new GBDeviceEvent[] { batteryInfo };
    }

    private GBDeviceEvent[] handlePacketChargingInfo(byte[] payload) {
        if (payload.length != 1)
            return null;

        batteryInfo.state = payload[0] == (byte)0x00 ? BatteryState.BATTERY_NORMAL : BatteryState.BATTERY_CHARGING;

        return new GBDeviceEvent[] { batteryInfo };
    }

    @Override
    public byte[] encodeSendConfiguration(String config) {
        Prefs prefs = getDevicePrefs();

        switch (config) {
            case PREF_SOUNDCORE_EQUALIZER_DIRECTION:
                return encodeRequest(CMD_GET_EQUALIZER);
            case PREF_SOUNDCORE_EQUALIZER_BAND1_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND1_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND2_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND2_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND3_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND3_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND4_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND4_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND5_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND5_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND6_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND6_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND7_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND7_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND8_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND8_VALUE:
            case PREF_SOUNDCORE_EQUALIZER_BAND9_FREQ:
            case PREF_SOUNDCORE_EQUALIZER_BAND9_VALUE:
                return encodeCustomEqualizer(prefs);
        }

        return super.encodeSendConfiguration(config);
    }

    @Override
    public byte[] encodeAutoPowerOffConfiguration(Prefs prefs) {
        final int duration = Integer.parseInt(prefs.getString(PREF_SOUNDCORE_AUTO_POWER_OFF, "2"));
        return encodeAutoPowerOff(CMD_SET_AUTO_POWER_OFF, duration, (byte) 0x00);
    }

    private byte[] encodeCustomEqualizer(Prefs prefs) {
        ByteBuffer buf = ByteBuffer.allocate(21);
        int eqDirection = Integer.parseInt(prefs.getString(PREF_SOUNDCORE_EQUALIZER_DIRECTION, "0"));
        byte[] equalizer = equalizerFromPrefs(prefs);

        // Bit combination of the equalizer directions that should be changed
        buf.put((byte)(1 << eqDirection));
        buf.put((byte)0x01);
        buf.put((byte)0xff);
        buf.put(equalizer);

        return encodeCommand(CMD_SET_EQUALIZER_CUSTOM, buf.array());
    }

    private Map<String, Object> equalizerToPrefs(byte[] equalizer) {
        Map<String, Object> prefs = new HashMap<>();

        for (int i = 0; i < EQUALIZER_PREFS_FREQ.length; i++) {
            // Equalizer values range from 60 to 180, with 120 as "neutral"
            byte freq = equalizer[i * 2 + 1];
            int value = Byte.toUnsignedInt(equalizer[i * 2]);

            // Map equalizer value to preference range (0 to 120)
            prefs.put(EQUALIZER_PREFS_FREQ[i], freq);
            prefs.put(EQUALIZER_PREFS_VALUE[i], value - 60);
        }

        return prefs;
    }

    private byte[] equalizerFromPrefs(Prefs prefs) {
        byte[] equalizer = new byte[EQUALIZER_PREFS_FREQ.length * 2];

        for (int i = 0; i < EQUALIZER_PREFS_FREQ.length; i++) {
            int freq = Integer.parseInt(prefs.getString(EQUALIZER_PREFS_FREQ[i], "0"));
            int value = prefs.getInt(EQUALIZER_PREFS_VALUE[i], 60);

            // Map equalizer values from 0 - 120 back to 60 - 180
            equalizer[i * 2 + 1] = ((byte)freq);
            equalizer[i * 2] = (byte)(value + 60);
        }

        return equalizer;
    }
}
