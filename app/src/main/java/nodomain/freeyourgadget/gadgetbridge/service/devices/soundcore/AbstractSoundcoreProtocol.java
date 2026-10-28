package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.serial.GBDeviceProtocol;

public abstract class AbstractSoundcoreProtocol extends GBDeviceProtocol {

    protected AbstractSoundcoreProtocol(GBDevice device) {
        super(device);
    }

    protected GBDeviceEventBatteryInfo buildBatteryInfo(int batteryIndex, int level) {
        GBDeviceEventBatteryInfo info = new GBDeviceEventBatteryInfo();
        info.batteryIndex = batteryIndex;
        info.level = level;
        return info;
    }

    protected GBDeviceEventVersionInfo buildVersionInfo(String firmware1, String firmware2, String serialNumber) {
        GBDeviceEventVersionInfo info = new GBDeviceEventVersionInfo();
        info.hwVersion = serialNumber;
        info.fwVersion = firmware1;
        info.fwVersion2 = (firmware2 != null && firmware2.equals(firmware1)) ? null : firmware2;
        return info;
    }

    protected GBDeviceEventVersionInfo buildVersionInfo(String firmware1, String serialNumber) {
        return buildVersionInfo(firmware1, "", serialNumber);
    }

    protected final GBDeviceEventVersionInfo buildVersionInfo(final byte[] payload,
                                                              final int firmwareOffset) {
        return buildVersionInfo(payload, firmwareOffset, 16);
    }

    /** Reads a single five-byte firmware version followed by the model's serial field. */
    protected final GBDeviceEventVersionInfo buildVersionInfo(final byte[] payload,
                                                              final int firmwareOffset,
                                                              final int serialLength) {
        return buildVersionInfo(
                readString(payload, firmwareOffset, 5),
                readString(payload, firmwareOffset + 5, serialLength)
        );
    }

    protected String readString(byte[] data, int position, int size) {
        if (position + size > data.length) throw new IllegalStateException();
        return new String(data, position, size, StandardCharsets.UTF_8);
    }

    protected byte encodeBoolean(boolean bool) {
        if (bool) return 0x01;
        else return 0x00;
    }

    protected SoundcorePacket decodePacket(final byte[] responseData) {
        return SoundcorePacket.decode(ByteBuffer.wrap(responseData));
    }

    protected byte[] encodeRequest(final short command) {
        return new SoundcorePacket(command).encode();
    }

    protected byte[] encodeCommand(final short command, final byte[] payload) {
        return new SoundcorePacket(command, payload).encode();
    }

    protected byte[] encodeBooleanCommand(final short command, final boolean enabled) {
        return encodeCommand(command, new byte[]{encodeBoolean(enabled)});
    }

    protected byte[] encodeByteCommand(final short command, final byte value) {
        return encodeCommand(command, new byte[]{value});
    }

    protected byte[] encodeByteCommand(final short command, final int value) {
        return encodeCommand(command, new byte[]{(byte) value});
    }

}
