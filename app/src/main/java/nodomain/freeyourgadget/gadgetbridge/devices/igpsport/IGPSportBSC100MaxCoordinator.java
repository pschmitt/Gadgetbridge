package nodomain.freeyourgadget.gadgetbridge.devices.igpsport;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;

public class IGPSportBSC100MaxCoordinator extends IGPSportAbstractCoordinator {

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_igpsport_bsc100max;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("BSC 100Max");
    }

}
