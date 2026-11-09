package nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.quatix;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix7XCoordinator;

public class GarminQuatix7XCoordinator extends GarminFenix7XCoordinator {
    @Override
    public boolean isExperimental() {
        // Not tested, and the supported device name below is unconfirmed
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        // TODO: unconfirmed device name
        return Pattern.compile("^quatix 7x( - [\\d+]+mm)?$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_garmin_quatix_7x;
    }
}
