package nodomain.freeyourgadget.gadgetbridge.model;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A single, complete sleep session: an ordered, contiguous list of {@link SleepStage}s.
 */
public class SleepSession {
    private final List<SleepStage> stages;
    private final Map<ActivityKind, Long> durationsByKind = new EnumMap<>(ActivityKind.class);

    public SleepSession(final List<SleepStage> stages) {
        if (stages == null || stages.isEmpty()) {
            throw new IllegalArgumentException("A sleep session must have at least one stage");
        }
        this.stages = Collections.unmodifiableList(stages);
        for (final SleepStage stage : stages) {
            durationsByKind.merge(stage.getKind(), stage.getDuration(), Long::sum);
        }
    }

    public List<SleepStage> getStages() {
        return stages;
    }

    /**
     * Start of this session, in epoch milliseconds. Equal to the first stage's start.
     */
    public long getStartTime() {
        return stages.get(0).getStartTime();
    }

    /**
     * End of this session, in epoch milliseconds. Equal to the last stage's end.
     */
    public long getEndTime() {
        return stages.get(stages.size() - 1).getEndTime();
    }

    /**
     * Total time spent in the given stage kind, in seconds.
     */
    public long getDuration(final ActivityKind kind) {
        final Long duration = durationsByKind.get(kind);
        return duration != null ? duration : 0L;
    }

    public long getLightSleepDuration() {
        return getDuration(ActivityKind.LIGHT_SLEEP);
    }

    public long getDeepSleepDuration() {
        return getDuration(ActivityKind.DEEP_SLEEP);
    }

    public long getRemSleepDuration() {
        return getDuration(ActivityKind.REM_SLEEP);
    }

    public long getAwakeSleepDuration() {
        return getDuration(ActivityKind.AWAKE_SLEEP);
    }

    /**
     * Total time actually asleep, in seconds - light + deep + rem + sleep-any, excluding awake.
     */
    public long getTotalSleepDuration() {
        return getLightSleepDuration() + getDeepSleepDuration() + getRemSleepDuration() + getDuration(ActivityKind.SLEEP_ANY);
    }
}
