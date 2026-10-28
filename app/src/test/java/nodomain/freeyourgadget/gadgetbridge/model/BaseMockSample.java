package nodomain.freeyourgadget.gadgetbridge.model;

import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;

public class BaseMockSample implements ActivitySample {
    private final int timestamp;
    private final ActivityKind kind;
    private final int steps;

    public BaseMockSample(final int timestamp,
                          final ActivityKind kind,
                          final int steps) {
        this.timestamp = timestamp;
        this.kind = kind;
        this.steps = steps;
    }

    @Override
    public int getTimestamp() {
        return timestamp;
    }

    @Override
    public ActivityKind getKind() {
        return kind;
    }

    @Override
    public int getSteps() {
        return steps;
    }

    @Override
    public SampleProvider<?> getProvider() {
        return null;
    }

    @Override
    public int getRawKind() {
        return kind.getCode();
    }

    @Override
    public int getRawIntensity() {
        return NOT_MEASURED;
    }

    @Override
    public float getIntensity() {
        return 0;
    }

    @Override
    public int getDistanceCm() {
        return NOT_MEASURED;
    }

    @Override
    public int getActiveCalories() {
        return NOT_MEASURED;
    }

    @Override
    public int getHeartRate() {
        return NOT_MEASURED;
    }

    @Override
    public void setHeartRate(int value) {
    }
}
