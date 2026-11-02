package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.protocol.impl.v1.SoundcoreProtocolImplV1;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

public class SoundcoreSpaceOneProtocol extends SoundcoreProtocolImplV1 {
    private static final Logger LOG = LoggerFactory.getLogger(SoundcoreSpaceOneProtocol.class);

    public SoundcoreSpaceOneProtocol(final GBDevice device) {
        super(device);
    }

    @Override
    protected GBDeviceEvent[] decodeDeviceInfo(final byte[] payload) {
        if (payload == null || payload.length < 23) {
            LOG.warn("CMD_GET_DEVICE_INFO payload too short: {} bytes", payload == null ? 0 : payload.length);
            return new GBDeviceEvent[0];
        }

        LOG.debug("Got device info: {}", GB.hexdump(payload));

        return new GBDeviceEvent[]{
                handleBatteryInfo(payload[0], 0)[0],
                buildVersionInfo(payload, 2)
        };
    }
}
