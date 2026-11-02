package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches;

import android.util.Size;

import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class MiBand11ActiveCoordinator extends XiaomiCoordinator {
    @Override
    public boolean isExperimental() {
        return true;
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_miband11active;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Xiaomi( Smart)? Band 11 Active [0-9A-F]{4}$");
    }

    @Override
    public int getDefaultIconResource() {
        return R.drawable.ic_device_default;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.FITNESS_BAND;
    }

    @Override
    public Size getScreenSize() {
        return new Size(172, 320);
    }
}
