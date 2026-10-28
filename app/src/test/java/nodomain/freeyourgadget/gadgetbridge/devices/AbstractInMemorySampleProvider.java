package nodomain.freeyourgadget.gadgetbridge.devices;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

public abstract class AbstractInMemorySampleProvider<T extends AbstractActivitySample> extends AbstractSampleProvider<T> {
    protected AbstractInMemorySampleProvider(final GBDevice device, final DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<T, ?> getSampleDao() {
        return null; // not database-backed
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return null; // not database-backed
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        //noinspection DataFlowIssue not database-backed
        return null;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        //noinspection DataFlowIssue not database-backed
        return null;
    }

    @Override
    public ActivityKind normalizeType(final int rawType) {
        return ActivityKind.fromCode(rawType);
    }

    @Override
    public int toRawActivityKind(final ActivityKind activityKind) {
        return activityKind.getCode();
    }

    @Override
    public float normalizeIntensity(final int rawIntensity) {
        return rawIntensity;
    }

    @Override
    public T createActivitySample() {
        throw new UnsupportedOperationException("read-only sample provider");
    }
}
