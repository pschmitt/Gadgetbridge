package nodomain.freeyourgadget.gadgetbridge.devices;

import androidx.annotation.NonNull;

import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;

public class FixedSampleDeviceCoordinator extends AbstractDeviceCoordinator {
    private final SampleProvider<? extends AbstractActivitySample> sampleProvider;

    public FixedSampleDeviceCoordinator(final SampleProvider<? extends AbstractActivitySample> sampleProvider) {
        this.sampleProvider = sampleProvider;
    }

    @NonNull
    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(@NonNull final GBDevice device,
                                                                      @NonNull final DaoSession session) {
        return sampleProvider;
    }

    @NonNull
    @Override
    public String getManufacturer() {
        return "Gadgetbridge Tests";
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return DeviceSupport.class;
    }

    @Override
    public int getDeviceNameResource() {
        return 0;
    }

    @NonNull
    @Override
    public DeviceCoordinator.DeviceKind getDeviceKind(@NonNull final GBDevice device) {
        return DeviceCoordinator.DeviceKind.UNKNOWN;
    }
}
