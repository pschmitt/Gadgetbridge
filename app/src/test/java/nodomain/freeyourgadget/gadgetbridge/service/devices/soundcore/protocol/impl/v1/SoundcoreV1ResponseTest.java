package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Test;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventBatteryInfo;
import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEventVersionInfo;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreAeroFit2Coordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.soundcore.SoundcoreLiberty3ProCoordinator;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices.SoundcoreLibertyProtocol;

public class SoundcoreV1ResponseTest {
    @Test
    public void centralDispatcherRoutesBatteryNotificationsToHook() {
        final TestProtocol protocol = new TestProtocol();

        final GBDeviceEvent[] events = protocol.decodeResponse(
                devicePacket(SoundcoreProtocolImplV1.CMD_NOTIFY_BATTERY_INFO, new byte[]{1, 2, 3})
        );

        assertNotNull(events);
        assertEquals(1, events.length);
        assertEquals(SoundcoreProtocolImplV1.CMD_NOTIFY_BATTERY_INFO, protocol.lastCommand);
        assertEquals(3, protocol.lastPayloadLength);
    }

    @Test
    public void handleBatteryInfoBuildsLeftAndRightForTwoBytePayload() {
        final TestProtocol protocol = new TestProtocol();

        final GBDeviceEvent[] events = protocol.batteryInfo(new byte[]{2, 3});

        assertEquals(2, events.length);
        assertEquals(40, ((GBDeviceEventBatteryInfo) events[0]).level);
        assertEquals(60, ((GBDeviceEventBatteryInfo) events[1]).level);
    }

    @Test
    public void handleBatteryInfoBuildsLeftRightAndCaseForThreeBytePayload() {
        final TestProtocol protocol = new TestProtocol();

        final GBDeviceEvent[] events = protocol.batteryInfo(new byte[]{1, 2, 3});

        assertEquals(3, events.length);
        assertEquals(1, ((GBDeviceEventBatteryInfo) events[0]).batteryIndex);
        assertEquals(2, ((GBDeviceEventBatteryInfo) events[1]).batteryIndex);
        assertEquals(0, ((GBDeviceEventBatteryInfo) events[2]).batteryIndex);
        assertEquals(20, ((GBDeviceEventBatteryInfo) events[0]).level);
        assertEquals(40, ((GBDeviceEventBatteryInfo) events[1]).level);
        assertEquals(60, ((GBDeviceEventBatteryInfo) events[2]).level);
    }

    @Test
    public void handleBatteryInfoIgnoresBytesAfterCase() {
        final TestProtocol protocol = new TestProtocol();

        final GBDeviceEvent[] events = protocol.batteryInfo(new byte[]{1, 2, 3, 4, 5});

        assertEquals(3, events.length);
        assertEquals(1, ((GBDeviceEventBatteryInfo) events[0]).batteryIndex);
        assertEquals(2, ((GBDeviceEventBatteryInfo) events[1]).batteryIndex);
        assertEquals(0, ((GBDeviceEventBatteryInfo) events[2]).batteryIndex);
        assertEquals(20, ((GBDeviceEventBatteryInfo) events[0]).level);
        assertEquals(40, ((GBDeviceEventBatteryInfo) events[1]).level);
        assertEquals(60, ((GBDeviceEventBatteryInfo) events[2]).level);
    }

    @Test
    public void batteryInfoRejectsNullAndShortPayloads() {
        final TestProtocol protocol = new TestProtocol();

        for (final byte[] payload : new byte[][]{null, new byte[0], new byte[]{1}}) {
            assertEquals(0, protocol.batteryInfo(payload).length);
            assertEquals(0, protocol.defaultBatteryInfo(payload).length);
        }
    }

    @Test
    public void batteryNotificationAcceptsTrailingBytes() {
        final TestLibertyProtocol protocol = new TestLibertyProtocol();

        final GBDeviceEvent[] events = protocol.decodeResponse(
                devicePacket(SoundcoreProtocolImplV1.CMD_NOTIFY_BATTERY_INFO, new byte[]{1, 2, 3, 4})
        );

        assertEquals(3, events.length);
        assertEquals(20, ((GBDeviceEventBatteryInfo) events[0]).level);
        assertEquals(40, ((GBDeviceEventBatteryInfo) events[1]).level);
        assertEquals(60, ((GBDeviceEventBatteryInfo) events[2]).level);
    }

    @Test
    public void batteryNotificationRejectsShortPayloads() {
        final TestLibertyProtocol protocol = new TestLibertyProtocol();

        for (final byte[] payload : new byte[][]{new byte[0], new byte[]{1}}) {
            assertEquals(0, protocol.decodeResponse(
                    devicePacket(SoundcoreProtocolImplV1.CMD_NOTIFY_BATTERY_INFO, payload)
            ).length);
        }
    }

    @Test
    public void nullAndMalformedPacketsRemainUnsupported() {
        final TestProtocol protocol = new TestProtocol();

        assertNull(protocol.decodeResponse(null));
        assertNull(protocol.decodeResponse(new byte[]{0x00}));
    }

    @Test
    public void libertyDecodesBatteryScalingAndOffsetSixDeviceInfo() {
        final TestLibertyProtocol protocol = new TestLibertyProtocol();
        final byte[] info = new byte[32];
        info[2] = 2;
        info[3] = 3;
        putAscii(info, 6, "01.01");
        putAscii(info, 11, "02.02");
        putAscii(info, 16, "1234567890123456");

        final GBDeviceEvent[] infoEvents = protocol.decodeResponse(
                devicePacket(SoundcoreProtocolImplV1.CMD_GET_DEVICE_INFO, info)
        );
        assertEquals(3, infoEvents.length);
        assertEquals(40, ((GBDeviceEventBatteryInfo) infoEvents[0]).level);
        assertEquals(60, ((GBDeviceEventBatteryInfo) infoEvents[1]).level);
        final GBDeviceEventVersionInfo versionInfo = (GBDeviceEventVersionInfo) infoEvents[2];
        assertEquals("01.01", versionInfo.fwVersion);
        assertEquals("02.02", versionInfo.fwVersion2);
        assertEquals("1234567890123456", versionInfo.hwVersion);

        final GBDeviceEvent[] batteryEvents = protocol.decodeResponse(
                devicePacket(SoundcoreProtocolImplV1.CMD_NOTIFY_BATTERY_INFO, new byte[]{1, 2, 3})
        );
        assertEquals(3, batteryEvents.length);
        assertEquals(1, ((GBDeviceEventBatteryInfo) batteryEvents[0]).batteryIndex);
        assertEquals(2, ((GBDeviceEventBatteryInfo) batteryEvents[1]).batteryIndex);
        assertEquals(0, ((GBDeviceEventBatteryInfo) batteryEvents[2]).batteryIndex);
        assertEquals(20, ((GBDeviceEventBatteryInfo) batteryEvents[0]).level);
        assertEquals(40, ((GBDeviceEventBatteryInfo) batteryEvents[1]).level);
        assertEquals(60, ((GBDeviceEventBatteryInfo) batteryEvents[2]).level);
    }

    @Test
    public void aeroFitKeepsItsOneBasedTenPercentBatteryEncoding() {
        final GBDevice device = org.mockito.Mockito.mock(GBDevice.class);
        org.mockito.Mockito.when(device.getDeviceCoordinator()).thenReturn(new SoundcoreAeroFit2Coordinator());
        final SoundcoreProtocolImplV1 protocol = new SoundcoreAeroFit2Coordinator().createProtocol(device);
        final byte[] info = new byte[30];
        info[2] = 0;
        info[3] = 9;
        putAscii(info, 4, "12.34");
        putAscii(info, 9, "56.78");
        putAscii(info, 14, "1234567890123456");

        final GBDeviceEvent[] events = protocol.decodeResponse(
                devicePacket(SoundcoreProtocolImplV1.CMD_GET_DEVICE_INFO, info)
        );

        assertEquals(10, ((GBDeviceEventBatteryInfo) events[0]).level);
        assertEquals(100, ((GBDeviceEventBatteryInfo) events[1]).level);
    }

    private static void putAscii(final byte[] target, final int offset, final String value) {
        final byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, target, offset, bytes.length);
    }

    private static byte[] devicePacket(final short command, final byte[] payload) {
        final ByteBuffer packet = ByteBuffer.allocate(10 + payload.length).order(ByteOrder.LITTLE_ENDIAN);
        packet.putShort((short) 0xff09);
        packet.putShort((short) 0x0000);
        packet.put((byte) 0x01);
        packet.putShort(command);
        packet.putShort((short) (10 + payload.length));
        packet.put(payload);
        byte checksum = 0;
        for (final byte value : packet.array()) {
            checksum += value;
        }
        packet.put(checksum);
        return packet.array();
    }

    private static GBDevice libertyDevice() {
        final GBDevice device = org.mockito.Mockito.mock(GBDevice.class);
        org.mockito.Mockito.when(device.getDeviceCoordinator()).thenReturn(new SoundcoreLiberty3ProCoordinator());
        return device;
    }

    private static final class TestProtocol extends SoundcoreProtocolImplV1 {
        private short lastCommand;
        private int lastPayloadLength;

        private TestProtocol() {
            super(libertyDevice());
        }

        @Override
        protected GBDeviceEvent[] decodeBatteryInfo(final byte[] payload) {
            lastCommand = CMD_NOTIFY_BATTERY_INFO;
            lastPayloadLength = payload.length;
            return new GBDeviceEvent[]{new GBDeviceEventBatteryInfo()};
        }

        private GBDeviceEvent[] defaultBatteryInfo(final byte[] payload) {
            return super.decodeBatteryInfo(payload);
        }

        private GBDeviceEvent[] batteryInfo(final byte[] payload) {
            return handleBatteryInfo(payload);
        }

    }

    private static final class TestLibertyProtocol extends SoundcoreLibertyProtocol {
        private TestLibertyProtocol() {
            super(libertyDevice());
        }
    }

}
