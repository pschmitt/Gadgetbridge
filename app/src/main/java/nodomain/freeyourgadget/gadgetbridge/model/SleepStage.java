package nodomain.freeyourgadget.gadgetbridge.model;

import androidx.annotation.NonNull;

@SuppressWarnings("ClassCanBeRecord")
public class SleepStage {
    private final ActivityKind kind;
    private final long startTime;
    private final long endTime;

    public SleepStage(final ActivityKind kind,
                      final long startTime,
                      final long endTime) {
        this.kind = kind;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * The kind of this stage. One of DEEP_SLEEP, LIGHT_SLEEP, REM_SLEEP, AWAKE_SLEEP or SLEEP_ANY.
     */
    public ActivityKind getKind() {
        return kind;
    }

    /**
     * Start of this stage, in epoch milliseconds.
     */
    public long getStartTime() {
        return startTime;
    }

    /**
     * End of this stage, in epoch milliseconds.
     */
    public long getEndTime() {
        return endTime;
    }

    /**
     * Duration of this stage, in seconds.
     */
    public long getDuration() {
        return (endTime - startTime) / 1000L;
    }

    @NonNull
    @Override
    public String toString() {
        return "SleepStage{" +
                "startTime=" + startTime +
                ", endTime=" + endTime +
                ", kind=" + kind +
                '}';
    }
}
