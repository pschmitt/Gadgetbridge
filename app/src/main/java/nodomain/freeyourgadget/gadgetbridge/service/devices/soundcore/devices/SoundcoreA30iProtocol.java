package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices;

import androidx.annotation.Nullable;

import java.nio.ByteBuffer;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator.AncMode;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator.AmbientSoundMode;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class SoundcoreA30iProtocol extends SoundcoreProtocolImplV1 {

    private static final int battery_earphone_left = 0;
    private static final int battery_earphone_right = 1;

    public SoundcoreA30iProtocol(GBDevice device) {
        super(device);
    }

    @Override
    protected void decodeAudioMode(final byte[] payload) {
        // A30i audio settings use a separate seven-byte layout in device info.
    }

    @Override
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        if (payload == null || payload.length < 78) {
            return new GBDeviceEvent[0];
        }

        //-------------------------------------------------------------------------
        // Active Noise Cancelling [63 to 70 (7 bytes)]
        final AmbientSoundMode ambientSoundControl = getAmbientSoundControl(payload[64], payload[67]);
        final AncMode ancMode = AncMode.fromByte(payload[70]);
        int noiseCancellingLevel = decodeNoiseCancellationLevel(payload[65]);
        boolean wind_noise_reduction = payload[68] == 0x01;
        //-------------------------------------------------------------------------
        boolean touchTone = payload[72] == 0x01;
        boolean surround3d = payload[74] == 0x01;
        boolean low_battery = payload[77] == 0x01;
        // AutoPowerOff duration=0 means never (disabled); 1=10min, 2=20min, 3=30min, 4=60min.
        final boolean autoPowerEnabled = payload[75] == 0x01;
        final int autoPowerDuration = autoPowerEnabled ? (payload[76] & 0xFF) + 1 : 0;

        getDevicePrefs().getPreferences().edit()
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL, ambientSoundControl != null ? ambientSoundControl.toString() : "Unknown Mode")
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE, ancMode != null ? ancMode.toString() : "Unknown Mode")
                .putInt(DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL, noiseCancellingLevel)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION, wind_noise_reduction)

                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_3D_SURROUND, surround3d)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_BATTERY_LOW_TONE, low_battery)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE, touchTone)
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF, String.valueOf(autoPowerDuration))
                .apply();

        return decodeStandardEarbudDeviceInfo(payload);
    }

    @Override
    protected int[] getBatteryIndexes(final int payloadLength) {
        return new int[]{battery_earphone_left, battery_earphone_right};
    }

    @Nullable
    @Override
    public byte[] encodeSendConfiguration(final String config) {
        switch (config) {
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION:
            case DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL:
                final Prefs prefs = getDevicePrefs();
                final AmbientSoundMode ambientSoundControl = AmbientSoundMode.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL, AmbientSoundMode.OFF.toString()));
                final AncMode ancMode = AncMode.fromPreference(
                        prefs.getString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ANC_MODE, AncMode.TRANSPORT.toString()));
                final int noiseCancellingLevel = prefs.getInt(DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL, 1);
                final boolean windNoiseReduction = prefs.getBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION, false);

                final byte[] command = encodeAudioMode(ambientSoundControl, ancMode, noiseCancellingLevel, windNoiseReduction);
                return command != null ? command : super.encodeSendConfiguration(config);
            default:
                return super.encodeSendConfiguration(config);
        }
    }

    /**
     * It returns the current ambient sound control.
     */
    private AmbientSoundMode getAmbientSoundControl(byte ambientSoundControlSelection, byte mode) {
        return switch (ambientSoundControlSelection) {
            case 0x00 -> switch (mode) {
                case 0x00 -> AmbientSoundMode.NOISE_CANCELLING;
                case 0x01 -> AmbientSoundMode.ADAPTIVE_ANC;
                case 0x02 -> AmbientSoundMode.AMBIENT_SOUND;
                default -> null;
            };
            case 0x01 -> AmbientSoundMode.TRANSPARENCY;
            case 0x02 -> AmbientSoundMode.OFF;
            default -> null;
        };
    }

    /**
     * It builds the content for the ANC command.
     * It always needs all information, independent of the mode.
     * @return The content for the ANC command; it returns null if an unknown value was requested.
     */
    private byte[] encodeAudioMode(AmbientSoundMode ambientSoundControl, AncMode ancMode, int noiseCancellingLevel, boolean wind_noise_reduction) {
        if (ambientSoundControl == null || ancMode == null) {
            return null;
        }
        final Byte ancStrength = encodeNoiseCancellationLevel(noiseCancellingLevel);
        if (ancStrength == null) {
            return null;
        }
        ByteBuffer buf = ByteBuffer.allocate(7);
        // A30i ambient sound is an ANC subtype, not its transparency mode.
        final byte mode = ambientSoundControl == AmbientSoundMode.AMBIENT_SOUND
                ? AmbientSoundMode.NOISE_CANCELLING.toByte()
                : ambientSoundControl.toByte();

        buf.put(mode);

        buf.put(ancStrength.byteValue());

        buf.put(mode); // Probably a duplicated value cached from before.

        switch (ambientSoundControl) {
            case ADAPTIVE_ANC -> buf.put((byte) 0x01);
            case AMBIENT_SOUND -> buf.put((byte) 0x02);
            case NOISE_CANCELLING, OFF, TRANSPARENCY -> buf.put((byte) 0x00);
            default -> {
                return null;
            }
        }

        buf.put(encodeBoolean(wind_noise_reduction));

        buf.put((byte) 0x00); // Byte seems to be unused.

        buf.put(ancMode.toByte());
        return encodeCommand(CMD_SET_AUDIO_MODE, buf.array());
    }
}
