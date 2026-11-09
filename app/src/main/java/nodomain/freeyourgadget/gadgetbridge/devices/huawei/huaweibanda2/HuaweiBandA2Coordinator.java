package nodomain.freeyourgadget.gadgetbridge.devices.huawei.huaweibanda2;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiConstants;
import nodomain.freeyourgadget.gadgetbridge.devices.huawei.HuaweiLECoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class HuaweiBandA2Coordinator extends HuaweiLECoordinator {
    @Override
    public boolean isExperimental() {
        // #7280
        return true;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile(HuaweiConstants.HU_BANDA2_NAME + ".*", Pattern.CASE_INSENSITIVE);
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_huawei_band_a2;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.FITNESS_BAND;
    }

    @Override
    public boolean supportsVO2Max(@NonNull final GBDevice device) {
        return false;
    }
}
