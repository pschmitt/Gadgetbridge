package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore.devices;

import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class SoundcoreLiberty4NCProtocol extends SoundcoreLibertyProtocol {
    public SoundcoreLiberty4NCProtocol(final GBDevice device) {
        super(device);
    }

    /**
     * Environment detection, transportation mode
     */
    @Override
    protected byte[] getAdvancedAudioModeTrailer() {
        return new byte[]{0x01, 0x00};
    }
}
