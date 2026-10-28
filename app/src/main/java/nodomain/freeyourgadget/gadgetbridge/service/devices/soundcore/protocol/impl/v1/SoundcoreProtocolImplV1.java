package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointDevice;
import nodomain.freeyourgadget.gadgetbridge.activities.multipoint.MultipointPairingActivity;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.sony.headphones.prefs.AmbientSoundControlButtonMode;

import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator.AmbientSoundMode;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator.ButtonAction;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreEarbudsCoordinator.TapFunction;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.FindDeviceTarget;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryState;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.AbstractSoundcoreProtocol;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.SoundcorePacket;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class SoundcoreProtocolImplV1 extends AbstractSoundcoreProtocol {
    private static final Logger LOG = LoggerFactory.getLogger(SoundcoreProtocolImplV1.class);

    public static final short CMD_GET_DEVICE_INFO = (short) 0x0101;
    public static final short CMD_GET_EXTENDED_INFO = (short) 0x0105;
    // Session handshake sent after CMD_GET_DEVICE_INFO; device ACKs with empty payload
    public static final short CMD_SESSION_INIT = (short) 0x8105;

    public static final short CMD_NOTIFY_BATTERY_INFO = (short) 0x0301;
    public static final short CMD_NOTIFY_CHARGING_INFO = (short) 0x0401;
    public static final short CMD_NOTIFY_AUDIO_MODE = (short) 0x0106;

    // Unsolicited notifications from the device on connection
    public static final short CMD_NOTIFY_PAIRED_DEVICES = (short) 0x010b;
    public static final short CMD_NOTIFY_CONNECTION_STATUS = (short) 0x020b;
    public static final short CMD_NOTIFY_DEVICE_STATE = (short) 0x0910;
    // Connect/disconnect a specific paired device; payload = 6-byte address (little-endian)
    public static final short CMD_DISCONNECT_DEVICE = (short) 0x810b;
    public static final short CMD_CONNECT_DEVICE = (short) 0x820b;
    public static final short CMD_FORGET_DEVICE = (short) 0x830b;
    public static final short CMD_SET_DUAL_CONNECTION = (short) 0x840b;

    public static final short CMD_SET_AUDIO_MODE = (short) 0x8106;
    public static final short CMD_SET_AMBIENT_SOUND_CONTROL_BUTTON_MODE = (short) 0x8206;
    public static final short CMD_SET_TAP_CONTROLS_FUNCTION = (short) 0x8104;
    public static final short CMD_SET_TAP_CONTROLS_ENABLE = (short) 0x9410;
    public static final short CMD_RESET_TAP_CONTROLS = (short) 0x8204;
    public static final short CMD_SET_TOUCH_TONE = (short) 0x8301;
    public static final short CMD_SET_WEARING_DETECTION = (short) 0x8101;
    public static final short CMD_SET_WEARING_TONE = (short) 0x8c01;
    public static final short CMD_SET_TOUCH_LOCK = (short) 0x8304;
    public static final short CMD_SET_AUTO_POWER_OFF = (short) 0x8601;
    public static final short CMD_SET_FIND_DEVICE = (short) 0x8910;
    public static final short CMD_ENABLE_PAIRING_MODE = (short) 0x850b;

    public static final short CMD_SET_3D_SURROUND = (short) 0x8602;
    public static final short CMD_SET_BATTERY_LOW_TONE = (short) 0x8210;

    public static final short CMD_SET_GAMING_MODE = (short) 0x8701;
    public static final short CMD_SET_EQUALIZER = (short) 0x8703;
    public static final short CMD_SET_FIT_TEST = (short) 0x0109;

    public static final short CMD_SET_VOICE_PROMPTS = (short) 0x9001;
    public static final short CMD_SET_BUTTON_BRIGHTNESS = (short) 0x9210;
    public static final short CMD_SET_LDAC_MODE = (short) 0xff01;
    public static final short CMD_SET_ADAPTIVE_DIRECTION = (short) 0x8a02;
    public static final short CMD_SET_EQUALIZER_PRESET = (short) 0x8b02;
    public static final short CMD_POWER_OFF = (short) 0x8901;

    public static final int battery_case = 0;
    public static final int battery_earphone_left = 1;
    public static final int battery_earphone_right = 2;

    private final List<MultipointDevice> pairedDevices = new ArrayList<>();
    private String activeSourceAddress;

    public SoundcoreProtocolImplV1(final GBDevice device) {
        super(device);
    }

    @Override
    public byte[] encodeSendConfiguration(final String config) {
        final Prefs prefs = getDevicePrefs();
        switch (config) {
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING:
            case DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL:
                return encodeAdvancedAudioMode();
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_LEFT:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_ACTION_RIGHT:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_LEFT:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_ACTION_RIGHT:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_LEFT:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_ACTION_RIGHT:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_LEFT:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_ACTION_RIGHT:
                return encodeConfiguredTapFunction(config, prefs);
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_SINGLE_TAP_DISABLED:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_DOUBLE_TAP_DISABLED:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TRIPLE_TAP_DISABLED:
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_LONG_PRESS_DISABLED:
                return encodeConfiguredTapLock(config, prefs);
            case DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_CONTROL_BUTTON_MODE:
                switch (AmbientSoundControlButtonMode.fromPreferences(prefs.getPreferences())) {
                    case NC_AS_OFF: return encodeControlAmbientMode(true, true, true);
                    case NC_AS: return encodeControlAmbientMode(true, true, false);
                    case NC_OFF: return encodeControlAmbientMode(true, false, true);
                    case AS_OFF: return encodeControlAmbientMode(false, true, true);
                }
                return null;
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_CONTROL_TOUCH_DISABLED:
                return encodeBooleanCommand(CMD_SET_TAP_CONTROLS_ENABLE, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TOUCH_TONE:
                return encodeBooleanCommand(CMD_SET_TOUCH_TONE, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WEARING_DETECTION:
                return encodeBooleanCommand(CMD_SET_WEARING_DETECTION, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WEARING_TONE:
                return encodeBooleanCommand(CMD_SET_WEARING_TONE, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_BATTERY_LOW_TONE:
                return encodeBooleanCommand(CMD_SET_BATTERY_LOW_TONE, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_3D_SURROUND:
                return encodeBooleanCommand(CMD_SET_3D_SURROUND, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_GAMING_MODE:
                return encodeBooleanCommand(CMD_SET_GAMING_MODE, prefs.getBoolean(config, false));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF:
                return encodeAutoPowerOffConfiguration(prefs);
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_VOICE_PROMPTS:
                return encodeBooleanCommand(CMD_SET_VOICE_PROMPTS, prefs.getBoolean(config, true));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_LDAC_MODE:
                return encodeBooleanCommand(CMD_SET_LDAC_MODE, prefs.getBoolean(config, true));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_DIRECTION:
                return encodeBooleanCommand(CMD_SET_ADAPTIVE_DIRECTION, prefs.getBoolean(config, true));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_BUTTON_BRIGHTNESS:
                return encodeByteCommand(CMD_SET_BUTTON_BRIGHTNESS, Integer.parseInt(prefs.getString(config, "0")));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_PRESET:
                return encodeByteCommand(CMD_SET_EQUALIZER_PRESET, Integer.parseInt(prefs.getString(config, "0")));
            case DeviceSettingsPreferenceConst.PREF_SOUNDCORE_FIT_TEST:
                return encodeCommand(CMD_SET_FIT_TEST, new byte[]{0x0a});
            default:
                return super.encodeSendConfiguration(config);
        }
    }

    /**
     * Bytes appended to the advanced audio mode payload.
     */
    protected byte[] getAdvancedAudioModeTrailer() {
        return new byte[0];
    }

    private byte[] encodeConfiguredTapFunction(final String config, final Prefs prefs) {
        // Action: single tap, double tap, hold.
        // Function: play, pause, next.
        for (final ButtonAction buttonAction : ButtonAction.values()) {
            final boolean right = config.equals(buttonAction.getRightPreference());
            if (!right && !config.equals(buttonAction.getLeftPreference())) {
                continue;
            }

            final TapFunction function = TapFunction.valueOf(prefs.getString(config, TapFunction.NONE.getValue()));
            // The action byte identifies the gesture; the function byte packs a model-specific
            // gesture group in the high nibble and the assigned function in the low nibble.
            final byte action = encodeButtonAction(buttonAction);
            final byte functionByte = (byte) ((getTapFunctionGroup(buttonAction) << 4) | function.getCode());
            return encodeControlFunction(right, action, functionByte);
        }
        throw new IllegalArgumentException("Unknown tap function preference: " + config);
    }

    private byte[] encodeConfiguredTapLock(final String config, final Prefs prefs) {
        for (final ButtonAction buttonAction : ButtonAction.values()) {
            if (!config.equals(buttonAction.getDisabledPreference())) {
                continue;
            }

            final byte action = encodeButtonAction(buttonAction);
            final boolean disabled = prefs.getBoolean(config, false);
            final byte enabledByte = (action == 0x02 || action == 0x05) // Single or Triple tap?
                    ? encodeBoolean(!disabled)
                    : (!disabled ? (byte) 0x11 : (byte) 0x10);
            return encodeCommand(CMD_SET_TOUCH_LOCK, new byte[]{0x00, action, enabledByte});
        }
        throw new IllegalArgumentException("Unknown tap lock preference: " + config);
    }

    /** Returns the model-specific high nibble of the function byte, not the gesture action byte. */
    protected int getTapFunctionGroup(final ButtonAction buttonAction) {
        switch (buttonAction) {
            case SINGLE_TAP: return 6;
            case DOUBLE_TAP: return 3;
            case TRIPLE_TAP:
            case LONG_PRESS: return 4;
            default: throw new IllegalArgumentException("Unknown button action: " + buttonAction);
        }
    }

    private byte encodeButtonAction(final ButtonAction buttonAction) {
        switch (buttonAction) {
            case SINGLE_TAP: return 0x02;
            case DOUBLE_TAP: return 0x00;
            case TRIPLE_TAP: return 0x05;
            case LONG_PRESS: return 0x01;
            default: throw new IllegalArgumentException("Unknown button action: " + buttonAction);
        }
    }

    protected byte[] encodeAutoPowerOffConfiguration(final Prefs prefs) {
        final int duration = Integer.parseInt(prefs.getString(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AUTO_POWER_OFF, "3"));
        return encodeAutoPowerOff(CMD_SET_AUTO_POWER_OFF, duration, (byte) 0x03);
    }

    /**
     * Decodes the common V1 response envelope and routes commands to device-specific hooks.
     *
     * <p>The payload layouts are not shared by all V1 devices, so this method deliberately
     * centralizes only packet parsing and command routing. Concrete protocols can incrementally
     * implement the protected hooks as their payload contracts are established.</p>
     */
    @Override
    public GBDeviceEvent[] decodeResponse(final byte[] responseData) {
        if (responseData == null) {
            return null;
        }

        final SoundcorePacket packet = decodePacket(responseData);

        if (packet == null) {
            return null;
        }

        final short command = packet.getCommand();
        final byte[] payload = packet.getPayload();

        switch (command) {
            case CMD_GET_DEVICE_INFO:
                return decodeDeviceInfo(payload);
            case CMD_GET_EXTENDED_INFO:
                return decodeExtendedInfo(payload);
            case CMD_NOTIFY_BATTERY_INFO:
                return decodeBatteryInfo(payload);
            case CMD_NOTIFY_CHARGING_INFO:
                return decodeChargingInfo(payload);
            case CMD_NOTIFY_AUDIO_MODE:
                decodeAudioMode(payload);
                return new GBDeviceEvent[0];
            case CMD_NOTIFY_PAIRED_DEVICES:
                return decodePairedDevices(payload);
            case CMD_NOTIFY_CONNECTION_STATUS:
                return decodeConnectionStatus(payload);
            case CMD_NOTIFY_DEVICE_STATE:
                return decodeDeviceState(payload);
            case CMD_SESSION_INIT:
            case CMD_DISCONNECT_DEVICE:
            case CMD_CONNECT_DEVICE:
            case CMD_FORGET_DEVICE:
            case CMD_SET_AUDIO_MODE:
            case CMD_SET_TAP_CONTROLS_FUNCTION:
            case CMD_SET_TAP_CONTROLS_ENABLE:
            case CMD_RESET_TAP_CONTROLS:
            case CMD_SET_TOUCH_TONE:
            case CMD_SET_TOUCH_LOCK:
            case CMD_SET_AUTO_POWER_OFF:
            case CMD_SET_FIND_DEVICE:
            case CMD_ENABLE_PAIRING_MODE:
                return decodeAcknowledgement(command, payload);
            default:
                return decodeCommand(command, payload);
        }
    }

    /** Decodes the standard earbud header, trying firmware offsets 4 and 6 in order. */
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        if (payload != null) {
            for (final int firmwareOffset : new int[]{4, 6}) {
                if (payload.length >= firmwareOffset + 26
                        && readString(payload, firmwareOffset, 5).matches("[0-9]{2}\\.[0-9]{2}")
                        && readString(payload, firmwareOffset + 5, 5).matches("[0-9]{2}\\.[0-9]{2}")) {
                    return decodeStandardEarbudDeviceInfo(payload, firmwareOffset);
                }
            }
        }

        // LOG.warn("CMD_GET_DEVICE_INFO has no valid firmware layout: {} bytes", payload == null ? 0 : payload.length);
        return new GBDeviceEvent[0];
    }

    /**
     * Decodes the device-info layout shared by Liberty-derived earbuds. Sport X20 uses this
     * layout for its common header and adds its own settings afterwards.
     */
    protected final GBDeviceEvent[] decodeStandardEarbudDeviceInfo(final byte[] payload) {
        return decodeStandardEarbudDeviceInfo(payload, 6);
    }

    protected final GBDeviceEvent[] decodeStandardEarbudDeviceInfo(final byte[] payload, final int firmwareOffset) {
        if (payload == null || firmwareOffset > payload.length - 26) {
            return new GBDeviceEvent[0];
        }

        final GBDeviceEvent[] batteryEvents = handleBatteryInfo(new byte[]{payload[2], payload[3]});
        return new GBDeviceEvent[]{
            batteryEvents[0],
            batteryEvents[1],
            buildVersionInfo(
                readString(payload, firmwareOffset, 5), // firmware1
                readString(payload, firmwareOffset + 5, 5), // firmware2
                readString(payload, firmwareOffset + 10, 16) // serialnumber
            )
        };
    }

    /** Hook for the device-specific CMD_GET_EXTENDED_INFO payload layout. */
    protected GBDeviceEvent[] decodeExtendedInfo(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /** Decodes the common left/right[/case] battery notification layout. */
    protected GBDeviceEvent[] decodeBatteryInfo(final byte[] payload) {
        return handleBatteryInfo(payload);
    }

    /** Converts a raw battery value using the concrete device model's encoding. */
    protected final int decodeBatteryLevel(final byte rawValue) {
        final SoundcoreEarbudsCoordinator.BatteryProperties settings =
                ((SoundcoreEarbudsCoordinator) getDevice().getDeviceCoordinator()).getBatteryProperties();
        return (rawValue + settings.getOffset()) * settings.getMultiplier();
    }

    /** Builds a battery event for devices, such as Q30, with one battery value. */
    protected final GBDeviceEvent[] handleBatteryInfo(final byte battery, final int batteryIndex) {
        return new GBDeviceEvent[]{buildBatteryInfo(batteryIndex, decodeBatteryLevel(battery))};
    }

    /** Builds earbud/case events, ignoring bytes after the optional charging-case value. */
    protected final GBDeviceEvent[] handleBatteryInfo(final byte[] payload) {
        if (payload == null || payload.length < 2) {
            return new GBDeviceEvent[0];
        }

        final int[] batteryIndexes = getBatteryIndexes(payload.length);
        final GBDeviceEvent[] events = new GBDeviceEvent[batteryIndexes.length];
        for (int index = 0; index < batteryIndexes.length; index++) {
            events[index] = buildBatteryInfo(batteryIndexes[index], decodeBatteryLevel(payload[index]));
        }
        return events;
    }

    /** Maps the available battery bytes to device slots, ignoring unrelated trailing data. */
    protected int[] getBatteryIndexes(final int payloadLength) {
        return payloadLength >= 3
                ? new int[]{battery_earphone_left, battery_earphone_right, battery_case}
                : new int[]{battery_earphone_left, battery_earphone_right};
    }

    /** Decodes the two earbud flags; trailing bytes do not describe a case battery here. */
    protected GBDeviceEvent[] decodeChargingInfo(final byte[] payload) {
        if (payload == null || payload.length < 2) {
            LOG.warn("CMD_NOTIFY_CHARGING_INFO payload too short: {} bytes", payload == null ? 0 : payload.length);
            return new GBDeviceEvent[0];
        }

        LOG.info("Left Earbud in Charging Case: {}, Right Earbud in Charging Case: {}",
                payload[0] == 0x01, payload[1] == 0x01);

        final int[] batteryIndexes = getBatteryIndexes(2);
        final List<GBDeviceEvent> events = new ArrayList<>(2);
        for (int index = 0; index < 2; index++) {
            if (payload[index] != 0x00 && payload[index] != 0x01) {
                LOG.warn("Unknown charging flag for earbud {}: {}", index, payload[index] & 0xff);
                continue;
            }

            final int batteryIndex = batteryIndexes[index];
            final GBDeviceEventBatteryInfo batteryInfo = buildBatteryInfo(
                    batteryIndex, getDevice().getBatteryLevel(batteryIndex));
            batteryInfo.voltage = getDevice().getBatteryVoltage(batteryIndex);
            batteryInfo.state = payload[index] == 0x01
                    ? BatteryState.BATTERY_CHARGING : BatteryState.BATTERY_NORMAL;
            events.add(batteryInfo);
        }
        return events.toArray(new GBDeviceEvent[0]);
    }

    /**
     * Decodes the paired-device list sent by the device (CMD_NOTIFY_PAIRED_DEVICES) and broadcasts
     * it to {@link MultipointPairingActivity}.
     *
     * Payload layout:
     * <pre>
     * [0..1]   = unknown header (01 01 even with two connected devices)
     * then, per paired device:
     *   [0]      = entry length (including this byte), e.g. 0x28 = 40
     *   [1]      = connection flag (0x01 = connected, 0x00 = not connected)
     *   [2..7]   = 6-byte Bluetooth address, little-endian
     *   [8..]    = device name, UTF-8, zero-padded, (entry length - 8) bytes
     * </pre>
     */
    protected GBDeviceEvent[] decodePairedDevices(final byte[] payload) {
        if (payload.length < 2) {
            return new GBDeviceEvent[0];
        }
        final List<MultipointDevice> devices = new ArrayList<>();
        int offset = 2; // skip the 2-byte header
        while (offset + 8 <= payload.length) {
            final int entrySize = Byte.toUnsignedInt(payload[offset]);
            if (entrySize < 8 || offset + entrySize > payload.length) {
                break;
            }
            final boolean connected = payload[offset + 1] != 0;
            final String address = formatMacAddress(payload, offset + 2);
            final int nameLen = entrySize - 8;
            int nameEnd = 0;
            while (nameEnd < nameLen && payload[offset + 8 + nameEnd] != 0) {
                nameEnd++;
            }
            final String name = new String(payload, offset + 8, nameEnd, StandardCharsets.UTF_8);
            LOG.debug("Paired device: addr={} name='{}' connected={}", address, name, connected);
            devices.add(new MultipointDevice(address, name, connected, false, ((SoundcoreEarbudsCoordinator) getDevice().getDeviceCoordinator())
                    .getSupportedMultipointActions().contains(MultipointPairingActivity.ACTION_MULTIPOINT_FORGET_DEVICE)));
            offset += entrySize;
        }
        pairedDevices.clear();
        pairedDevices.addAll(devices);
        broadcastPairedDevices(pairedDevices, activeSourceAddress);
        return new GBDeviceEvent[0];
    }

    /** Decodes the active source address from CMD_NOTIFY_CONNECTION_STATUS payload. */
    protected GBDeviceEvent[] decodeConnectionStatus(final byte[] payload) {
        if (payload.length != 7 || (payload[0] != 0x00 && payload[0] != 0x01)) {
            LOG.warn("Invalid connection status payload");
            return new GBDeviceEvent[0];
        }
        activeSourceAddress = payload[0] == 0x01 ? formatMacAddress(payload, 1) : null;
        broadcastPairedDevices(pairedDevices, activeSourceAddress);
        return new GBDeviceEvent[0];
    }

    /** Hook for the device-specific CMD_NOTIFY_DEVICE_STATE payload layout. */
    protected GBDeviceEvent[] decodeDeviceState(final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /** Hook for common command acknowledgements whose payload has no shared semantics. */
    protected GBDeviceEvent[] decodeAcknowledgement(final short command, final byte[] payload) {
        return new GBDeviceEvent[0];
    }

    /**
     * Handles shared commands; model-specific overrides delegate unrecognized commands here.
     * Unknown commands are logged here and consumed without emitting device events.
     */
    protected GBDeviceEvent[] decodeCommand(final short command, final byte[] payload) {
        if (command == CMD_SET_FIT_TEST) {
            if (payload.length >= 2) {
                decodeFitTestResult(payload);
            }
            return new GBDeviceEvent[0];
        }

        LOG.debug("Unknown incoming message - command: {} ({} bytes): {}",
                command, payload.length, GB.lazyHexdump(payload));
        return new GBDeviceEvent[0];
    }

    /**
     * Decodes the first two bytes of a fit-test result and shows a Toast.
     * payload[0] = left ear: 0x01 = excellent seal, 0x02 = poor seal
     * payload[1] = right ear: 0x01 = excellent seal, 0x02 = poor seal
     */
    private void decodeFitTestResult(final byte[] payload) {
        final Context ctx = GBApplication.getContext();
        final String excellent = ctx.getString(R.string.pref_soundcore_fit_test_excellent_seal);
        final String poor = ctx.getString(R.string.pref_soundcore_fit_test_poor_seal);
        final String left = (payload[0] == 0x01) ? excellent : poor;
        final String right = (payload[1] == 0x01) ? excellent : poor;
        final int severity = (payload[0] == 0x01 && payload[1] == 0x01) ? GB.INFO : GB.WARN;
        final String message = ctx.getString(R.string.pref_soundcore_fit_test_result, left, right);
        LOG.debug("Fit test result: left={} right={}", left, right);
        GB.toast(message, Toast.LENGTH_LONG, severity);
    }

    /** Requests the paired-device list from the device. */
    public byte[] encodePairedDevicesRequest() {
        return encodeRequest(CMD_NOTIFY_PAIRED_DEVICES);
    }

    /** Enables or disables dual connection (multipoint) on the device. */
    public byte[] encodeDualConnection(final boolean enabled) {
        return encodeBooleanCommand(CMD_SET_DUAL_CONNECTION, enabled);
    }

    /** Puts the device into pairing mode so a new device can be paired. */
    public byte[] encodeStartPairing() {
        return encodePairingMode();
    }

    /** Connects the device to the given paired device (address as "AA:BB:CC:DD:EE:FF"). */
    public byte[] encodeConnectDevice(final String address) {
        return encodeCommand(CMD_CONNECT_DEVICE, addressToLittleEndian(address));
    }

    /** Disconnects the device from the given paired device (address as "AA:BB:CC:DD:EE:FF"). */
    public byte[] encodeDisconnectDevice(final String address) {
        return encodeCommand(CMD_DISCONNECT_DEVICE, addressToLittleEndian(address));
    }

    /** Forgets/unpairs the given paired device (address as "AA:BB:CC:DD:EE:FF"). */
    public byte[] encodeForgetDevice(final String address) {
        return encodeCommand(CMD_FORGET_DEVICE, addressToLittleEndian(address));
    }

    /** Requests the current connection/active-audio status from the device. */
    public byte[] encodeConnectionStatusRequest() {
        return encodeRequest(CMD_NOTIFY_CONNECTION_STATUS);
    }

    /**
     * Converts a Bluetooth address string ("AA:BB:CC:DD:EE:FF") into the 6-byte little-endian
     * representation used by the device (the same byte order as reported in the paired-device list).
     */
    private static byte[] addressToLittleEndian(final String address) {
        final String[] parts = address.split(":");
        final byte[] mac = new byte[6];
        for (int i = 0; i < 6 && i < parts.length; i++) {
            mac[5 - i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return mac;
    }

    public void broadcastMultipointStatus(final boolean enabled) {
        final Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_STATUS_UPDATE);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_ENABLED, enabled);
        final Set<String> actions = ((SoundcoreEarbudsCoordinator) getDevice().getDeviceCoordinator())
                .getSupportedMultipointActions();
        intent.putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_DISABLE_SUPPORTED,
                actions.contains(MultipointPairingActivity.ACTION_MULTIPOINT_DISABLE));
        intent.putExtra(MultipointPairingActivity.EXTRA_MULTIPOINT_PAIR_SUPPORTED,
                actions.contains(MultipointPairingActivity.ACTION_MULTIPOINT_START_PAIRING));
        LocalBroadcastManager.getInstance(GBApplication.getContext()).sendBroadcast(intent);
    }

    public void broadcastMultipointPairing(final boolean enabled) {
        final Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_PAIRING_UPDATE);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putExtra(MultipointPairingActivity.EXTRA_PAIRING_ENABLED, enabled);
        LocalBroadcastManager.getInstance(GBApplication.getContext()).sendBroadcast(intent);
    }

    protected final void broadcastPairedDevices(final List<MultipointDevice> pairedDevices,
                                                final String activeSourceAddress) {
        final List<MultipointDevice> devices = new ArrayList<>();
        for (final MultipointDevice device : pairedDevices) {
            devices.add(new MultipointDevice(device.getAddress(), device.getName(), device.isConnected(),
                    device.isConnected() && device.getAddress().equalsIgnoreCase(activeSourceAddress),
                    device.getCanForget()));
        }
        broadcastMultipointList(devices);
    }

    /** Formats the six-byte little-endian Bluetooth address used by V1 multipoint commands. */
    public static String formatMacAddress(final byte[] payload, final int position) {
        StringBuilder mac = new StringBuilder(17);
        for (int i = 5; i >= 0; i--) {
            if (mac.length() > 0) {
                mac.append(':');
            }
            mac.append(String.format("%02X", payload[position + i] & 0xFF));
        }
        return mac.toString();
    }

    public void broadcastMultipointList(final List<MultipointDevice> devices) {
        final Intent intent = new Intent(MultipointPairingActivity.ACTION_MULTIPOINT_DEVICE_LIST);
        intent.putExtra(GBDevice.EXTRA_DEVICE, getDevice());
        intent.putParcelableArrayListExtra(
                MultipointPairingActivity.EXTRA_DEVICE_LIST, new ArrayList<>(devices));
        LocalBroadcastManager.getInstance(GBApplication.getContext()).sendBroadcast(intent);
    }

    /**
     * 0: No Auto Power off
     * 1: Auto Power off 10 min
     * 2: Auto Power off 20 min
     * 3: Auto Power off 30 min
     * 4: Auto Power off 60 min
     */
    protected byte[] encodeAutoPowerOff(final short command, final int duration, final byte disabledDuration) {
        final byte[] payload;

        if (duration > 0) {
            payload = new byte[]{0x01, (byte) (duration - 1)};
        } else {
            payload = new byte[]{0x00, disabledDuration};
        }

        return encodeCommand(command, payload);
    }

    public byte[] encodePowerOff() {
        return encodeRequest(CMD_POWER_OFF);
    }

    public byte[] encodeDeviceInfoRequest() {
        return encodeRequest(CMD_GET_DEVICE_INFO);
    }

    /** Requests extended device configuration (firmware details, serial, settings). */
    public byte[] encodeExtendedInfoRequest() {
        return encodeRequest(CMD_GET_EXTENDED_INFO);
    }

    /** Sent after CMD_GET_DEVICE_INFO to finalise the session with the device. */
    public byte[] encodeSessionInitRequest() {
        return encodeRequest(CMD_SESSION_INIT);
    }

    @Override
    public byte[] encodeFindDevice(final boolean start) {
        return encodeFindDevice(start, FindDeviceTarget.ALL);
    }

    @Override
    public byte[] encodeFindDevice(final boolean start, @NonNull final FindDeviceTarget target) {
        return encodeCommand(CMD_SET_FIND_DEVICE, new byte[]{
                encodeBoolean(start && target != FindDeviceTarget.RIGHT), // left
                encodeBoolean(start && target != FindDeviceTarget.LEFT), // right
                0x00
        });
    }

    protected byte[] encodePairingMode() {
        return encodeCommand(CMD_ENABLE_PAIRING_MODE, new byte[]{0x00, (byte) 0x90});
    }

    protected byte[] encodeControlFunction(final boolean right, final byte action, final byte function) {
        return encodeCommand(CMD_SET_TAP_CONTROLS_FUNCTION, new byte[]{encodeBoolean(right), action, function});
    }

    /** Encodes the audio-mode cycle assigned to a tap control. */
    protected byte[] encodeControlAmbientMode(final boolean anc,
                                              final boolean transparency,
                                              final boolean normal) {
        final byte ambientModes = (byte) (
            (normal       ? 1 << 2 : 0) | // 4
            (transparency ? 1 << 1 : 0) | // 2
            (anc          ? 1      : 0)   // 1
        );

        return encodeCommand(CMD_SET_AMBIENT_SOUND_CONTROL_BUTTON_MODE, new byte[]{ambientModes});
    }

    /**
     * Encodes the following settings to a payload to set the audio-mode on the headphones:
     * PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL If ANC, Transparent or neither should be active
     * PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING If the strenght of the ANC should be set manual or adaptively according to ambient noise
     * PREF_SONY_AMBIENT_SOUND_LEVEL How strong the ANC should be in manual mode
     * PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE If the Transparency should focus on vocals or should be fully transparent
     * PREF_SOUNDCORE_WIND_NOISE_REDUCTION If Transparency or ANC should reduce Wind Noise
     * @return The payload
    */
    protected byte[] encodeAdvancedAudioMode() {
        final Prefs prefs = getDevicePrefs();
        final AmbientSoundMode ambientSoundMode = AmbientSoundMode.fromPreference(prefs.getString(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL,
                "off"
        ));

        if (ambientSoundMode == null) {
            return null;
        }

        final Byte ancStrength = encodeNoiseCancellationLevel(prefs.getInt(
                DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL,
                1
        ));

        if (ancStrength == null) {
            return null;
        }

        final byte vocalMode = encodeBoolean(prefs.getBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE,
                false
        ));
        final byte adaptiveAnc = encodeBoolean(prefs.getBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING,
                true
        ));
        final byte windNoiseReduction = encodeBoolean(prefs.getBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION,
                false
        ));

        final byte[] payload = new byte[]{
                ambientSoundMode.toByte(),
                ancStrength.byteValue(),
                vocalMode,
                adaptiveAnc,
                windNoiseReduction
        };

        final byte[] trailer = getAdvancedAudioModeTrailer();
        final byte[] fullPayload = Arrays.copyOf(payload, payload.length + trailer.length);
        System.arraycopy(trailer, 0, fullPayload, payload.length, trailer.length);

        return encodeCommand(CMD_SET_AUDIO_MODE, fullPayload);
    }

    /** Decodes the shared five-field Liberty/Sport X20 audio layout; ignores trailing state bytes. */
    protected void decodeAudioMode(final byte[] payload) {
        if (payload.length < 5) {
            return;
        }

        final SharedPreferences.Editor editor = getDevicePrefs().getPreferences().edit();

        editor.putString(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_AMBIENT_SOUND_CONTROL,
                AmbientSoundMode.fromByte(payload[0]).toString()
        );
        editor.putInt(
                DeviceSettingsPreferenceConst.PREF_SONY_AMBIENT_SOUND_LEVEL,
                decodeNoiseCancellationLevel(payload[1])
        );
        editor.putBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_TRANSPARENCY_VOCAL_MODE,
                payload[2] == 0x01
        );
        editor.putBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_ADAPTIVE_NOISE_CANCELLING,
                payload[3] == 0x01
        );
        editor.putBoolean(
                DeviceSettingsPreferenceConst.PREF_SOUNDCORE_WIND_NOISE_REDUCTION,
                payload[4] == 0x01
        );
        editor.apply();
    }

    protected Byte encodeNoiseCancellationLevel(final int strength) {
        final byte[] levels = ((SoundcoreEarbudsCoordinator) getDevice().getDeviceCoordinator())
                .getNoiseCancellationLevelRange();
        if (strength < 1 || strength >= levels.length) {
            return null;
        }
        return levels[strength];
    }

    protected int decodeNoiseCancellationLevel(final byte strength) {
        final byte[] levels = ((SoundcoreEarbudsCoordinator) getDevice().getDeviceCoordinator())
                .getNoiseCancellationLevelRange();
        for (int i = 1; i < levels.length; i++) {
            if ((levels[i] & 0xF0) == (strength & 0xF0)) {
                return i;
            }
        }
        return 1;
    }
}
