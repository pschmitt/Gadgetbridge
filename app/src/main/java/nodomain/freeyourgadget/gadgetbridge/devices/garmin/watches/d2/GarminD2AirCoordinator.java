package nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.d2;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenuCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class GarminD2AirCoordinator extends GarminVenuCoordinator {
    @Override
    public boolean isExperimental() {
        // Not tested, and the supported device name below is unconfirmed
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        // TODO: unconfirmed device name
        return Pattern.compile("^D2 Air( - \\d+mm)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_d2_air;
    }

    @Override
    public boolean supportsTrainingStatus(@NonNull final GBDevice device) {
        return false;
    }
}
