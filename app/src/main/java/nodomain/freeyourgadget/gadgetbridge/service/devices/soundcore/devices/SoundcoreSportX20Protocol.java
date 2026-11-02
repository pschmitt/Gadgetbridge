package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator.TapFunction;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.SoundcoreEqualizer;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.SoundcoreEqualizerPresets;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class SoundcoreSportX20Protocol extends SoundcoreProtocolImplV1 {
    private static final Logger LOG = LoggerFactory.getLogger(SoundcoreSportX20Protocol.class);

    // Offsets within CMD_GET_DEVICE_INFO payload for the equalizer preset and band values.
    // [38]    = preset ID (0x00–0x15 = named preset, 0xfe = custom)
    // [39]    = 0x00 for named presets, 0xfe for custom
    // [40..47] = 8 EQ band bytes, encoding: dB = round((raw - 120) / 10), range –6..+6
    private static final int DEVICE_INFO_EQUALIZER_PRESET_OFFSET = 38;
    private static final int DEVICE_INFO_BANDS_OFFSET = 40;

    // Payload offset (within CMD_GET_DEVICE_INFO response) where the 6 control-function
    // bytes start: L_single, R_single, L_double, R_double, L_long, R_long
    private static final int DEVICE_INFO_CONTROL_OFFSET = 110;
    private static final int DEVICE_INFO_CONTROL_LENGTH = 6;

    // Offset within CMD_GET_DEVICE_INFO payload where the 6-byte audio-mode state mirror starts.
    // Bytes [AUDIO_OFFSET .. AUDIO_OFFSET+5] have the same layout as CMD_NOTIFY_AUDIO_MODE payload:
    //   [0]=transparency_active  [1]=mode_byte  [2]=voice_mode  [3]=adaptive_nc  [4]=wind  [5]=0xff
    private static final int DEVICE_INFO_AUDIO_OFFSET = 117;
    private static final int DEVICE_INFO_AUDIO_LENGTH = 6;

    // Offset within CMD_GET_DEVICE_INFO payload for the 3D surround sound boolean (0x01=on, 0x00=off).
    private static final int DEVICE_INFO_3D_SURROUND_OFFSET = 124;
    // Offset within CMD_GET_DEVICE_INFO payload for the dual connection boolean (0x01=on, 0x00=off).
    private static final int DEVICE_INFO_DUAL_CONNECTION_OFFSET = 126;
    // Offset within CMD_GET_DEVICE_INFO payload for the touch tone boolean (0x01=on, 0x00=off).
    private static final int DEVICE_INFO_TOUCH_TONE_OFFSET = 127;

    // Offsets within CMD_GET_DEVICE_INFO payload for auto power off.
    // [128] = enabled flag (0x01=on, 0x00=never/disabled)
    // [129] = duration raw value when enabled: 0x00=10min, 0x01=20min, 0x02=30min, 0x03=60min
    //         maps to preference integer: duration = raw + 1  (1=10min, 2=20min, 3=30min, 4=60min)
    //         preference 0 = never (disabled)
    private static final int DEVICE_INFO_AUTO_POWER_OFF_ENABLED_OFFSET = 128;
    private static final int DEVICE_INFO_AUTO_POWER_OFF_OFFSET = 129;
    // Expected full length of CMD_GET_DEVICE_INFO payload as observed from the device.
    private static final int DEVICE_INFO_MIN_EXPECTED_LENGTH = 132; // 143

    public SoundcoreSportX20Protocol(final GBDevice device) {
        super(device);
    }

    @Override
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        // Sport X20 appends model-specific settings at offsets 38+, while the
        // common Liberty header still carries battery and version information.
        decodeControlFunctionsFromDeviceInfo(payload);
        return decodeStandardEarbudDeviceInfo(payload);
    }

    @Override
    protected GBDeviceEvent[] decodeDeviceState(final byte[] payload) {
        LOG.debug("Device state notification, {} bytes", payload.length);
        return new GBDeviceEvent[0];
    }

    /**
     * Reads all the settings embedded in the CMD_GET_DEVICE_INFO response and stores them in preferences.
     * This includes the equalizer preset and band values, button-control functions, audio-mode mirror,
     * 3D surround sound, dual connection, touch tone, and auto power off.
     *
     * Example 143-byte payload (Bass Boost preset, 3D off, dual-conn on, touch-tone on, APO 10 min):
     * <pre>
     * [  0]  01 01 05 05 00 00 30 31 2e 36 35 30 31 2e 36 35   version
     * [ 16]  30 31 32 33 34 35 36 37 38 39 61 62 63 64 65 66   serial
     * [ 32]  00 00 00 00 00 05 [02] 00 [a0 96 82 78 78 78 78   [38]=preset=0x02, [40..47]=bands
     * [ 48]  78]00 ff ff ff ff ff ff ff ff ff 6c 02 00 9f a1   [48]=9th band (0x78)
     * [ 64]  8a 99 8a 80 77 64 3c 3c 9f a1 8a 99 8a 80 77 64   common block
     * [ 80]  3c 3c 69 d6 4e 6a 00 9f a1 8a 99 8a 80 77 64 3c   common block
     * [ 96]  3c 9f a1 8a 99 8a 80 77 64 3c 3c 00 00 [08 66 66   [110..115]=controls
     * [112]  32 33 44] 33 [02 30 01 01 00 ff] 32 [00] 00 [01]   [117..122]=audio, [124]=3D=0x00, [126]=dual
     * [128]  [01] [01] 00 00 ff ff ff ff ff ff ff ff ff ff ff   [127]=tone=0x01, [128]=APO_en, [129]=APO_dur
     * </pre>
     */
    protected void decodeControlFunctionsFromDeviceInfo(final byte[] payload) {
        if (payload.length < DEVICE_INFO_MIN_EXPECTED_LENGTH) {
            LOG.warn("CMD_GET_DEVICE_INFO payload too short: {} bytes (expected {})", payload.length, 143);
            return;
        }

        final SoundcoreEqualizer equalizer = SoundcoreEqualizer.fromPayload(
                payload, DEVICE_INFO_EQUALIZER_PRESET_OFFSET, DEVICE_INFO_BANDS_OFFSET);
        if (equalizer != null) {
            if (SoundcoreEqualizerPresets.fromIndex(equalizer.getIndex()) != null) {
                LOG.debug("Equalizer preset from device info: {}", equalizer.getIndex());
                storeEqualizer(equalizer);
            } else {
                LOG.warn("Unknown equalizer preset id in device info: 0x{}", Integer.toHexString(equalizer.getIndex()));
            }
        }

        // Button-control functions at offsets 110–115.
        final TapFunction lSingle = functionFromCode(payload[DEVICE_INFO_CONTROL_OFFSET]     & 0x0f);
        final TapFunction rSingle = functionFromCode(payload[DEVICE_INFO_CONTROL_OFFSET + 1] & 0x0f);
        final TapFunction lDouble = functionFromCode(payload[DEVICE_INFO_CONTROL_OFFSET + 2] & 0x0f);
        final TapFunction rDouble = functionFromCode(payload[DEVICE_INFO_CONTROL_OFFSET + 3] & 0x0f);
        final TapFunction lLong   = functionFromCode(payload[DEVICE_INFO_CONTROL_OFFSET + 4] & 0x0f);
        final TapFunction rLong   = functionFromCode(payload[DEVICE_INFO_CONTROL_OFFSET + 5] & 0x0f);

        LOG.debug("Control functions from device info: L_single={} R_single={} L_double={} R_double={} L_long={} R_long={}",
                lSingle, rSingle, lDouble, rDouble, lLong, rLong);
        getDevicePrefs().getPreferences().edit()
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_LEFT,  lSingle.name())
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_RIGHT, rSingle.name())
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_LEFT,  lDouble.name())
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_RIGHT, rDouble.name())
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_LEFT,  lLong.name())
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_RIGHT, rLong.name())
                .apply();

        // Audio state mirror at offsets 117–122, same layout as CMD_NOTIFY_AUDIO_MODE.
        final byte[] audioPayload = java.util.Arrays.copyOfRange(
                payload, DEVICE_INFO_AUDIO_OFFSET, DEVICE_INFO_AUDIO_OFFSET + DEVICE_INFO_AUDIO_LENGTH);
        LOG.debug("Decoding audio state from CMD_GET_DEVICE_INFO mirror: {}", StringUtils.bytesToHex(audioPayload));
        decodeAudioMode(audioPayload);

        // 3D surround sound boolean at offset 124 (0x01=on, 0x00=off).
        final boolean surround3d = payload[DEVICE_INFO_3D_SURROUND_OFFSET] == 0x01;
        LOG.debug("3D surround sound from device info: {}", surround3d);

        // Dual connection boolean at offset 126 (0x01=on, 0x00=off).
        final boolean dualConnection = payload[DEVICE_INFO_DUAL_CONNECTION_OFFSET] == 0x01;
        LOG.debug("Dual connection from device info: {}", dualConnection);
        broadcastMultipointStatus(dualConnection);

        // Touch tone boolean at offset 127 (0x01=on, 0x00=off).
        final boolean touchTone = payload[DEVICE_INFO_TOUCH_TONE_OFFSET] == 0x01;
        LOG.debug("Touch tone from device info: {}", touchTone);

        // Auto power off: enabled flag at offset 128, duration at offset 129.
        // duration=0 means never (disabled); 1=10min, 2=20min, 3=30min, 4=60min.
        final boolean autoPowerEnabled = payload[DEVICE_INFO_AUTO_POWER_OFF_ENABLED_OFFSET] == 0x01;
        final int autoPowerDuration = autoPowerEnabled ? (payload[DEVICE_INFO_AUTO_POWER_OFF_OFFSET] & 0xFF) + 1 : 0;
        LOG.debug("Auto power off from device info: enabled={} duration={}", autoPowerEnabled, autoPowerDuration);

        getDevicePrefs().getPreferences().edit()
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_3D_SURROUND, surround3d)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_DUAL_CONNECTION, dualConnection)
                .putBoolean(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE, touchTone)
                .putString(DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF, String.valueOf(autoPowerDuration))
                .apply();
    }

    /** Maps a low-nibble TapFunction code (0–15) back to the TapFunction enum. */
    private static TapFunction functionFromCode(final int code) {
        for (final TapFunction f : TapFunction.values()) {
            if (f.getCode() == code) {
                return f;
            }
        }
        return TapFunction.NONE;
    }
}
