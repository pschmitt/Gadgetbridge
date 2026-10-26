package nodomain.freeyourgadget.gadgetbridge.service.devices.beurer;

import nodomain.freeyourgadget.gadgetbridge.service.devices.generic_bp.GenericBloodPressureSupport;

public class BeurerBm59Support extends GenericBloodPressureSupport {
    @Override
    protected boolean shouldSyncTime() {
        return true;
    }
}
