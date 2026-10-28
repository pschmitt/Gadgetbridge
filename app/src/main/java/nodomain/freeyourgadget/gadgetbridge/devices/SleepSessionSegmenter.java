package nodomain.freeyourgadget.gadgetbridge.devices;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;
import nodomain.freeyourgadget.gadgetbridge.model.SleepStage;

/**
 * Derives {@link SleepSession}s from an ordered list of activity samples.
 */
public final class SleepSessionSegmenter {

    /**
     * A pending sleep session shorter than this (including its trailing wake padding) is discarded.
     */
    public static final long MIN_SESSION_LENGTH_SECONDS = 5 * 60;

    /**
     * A run of non-sleep samples longer than this closes the session.
     */
    public static final long MAX_WAKE_PHASE_LENGTH_SECONDS = 60 * 60;

    /**
     * {@link SampleProvider#getAllActivitySamples(int, int)} guarantees exactly one sample per
     * minute, so a stage that runs up to the last sample of a session is extended by this much to
     * give it a real, non-zero duration.
     */
    public static final long TRAILING_STAGE_SECONDS = 60;

    private SleepSessionSegmenter() {
    }

    public static List<SleepSession> segment(final List<? extends ActivitySample> samples) {
        final List<SleepSession> result = new ArrayList<>();

        int firstSleepIdx = -1;
        int lastSleepIdx = -1;
        long durationSinceLastSleep = 0;
        ActivitySample previousSample = null;

        for (int i = 0; i < samples.size(); i++) {
            final ActivitySample sample = samples.get(i);
            final boolean isSleep = ActivityKind.isSleep(sample.getKind());

            if (isSleep) {
                if (firstSleepIdx == -1) {
                    firstSleepIdx = i;
                }
                lastSleepIdx = i;
                durationSinceLastSleep = 0;
            } else if (firstSleepIdx != -1) {
                final long gap = sample.getTimestamp() - previousSample.getTimestamp();
                final int steps = sample.getSteps();
                final boolean hasActivity = steps > 0;
                if (hasActivity || durationSinceLastSleep + gap > MAX_WAKE_PHASE_LENGTH_SECONDS) {
                    emit(result, samples, firstSleepIdx, lastSleepIdx);
                    firstSleepIdx = -1;
                    lastSleepIdx = -1;
                }
            }

            if (previousSample != null && !isSleep) {
                durationSinceLastSleep += sample.getTimestamp() - previousSample.getTimestamp();
                if (firstSleepIdx != -1 && durationSinceLastSleep > MAX_WAKE_PHASE_LENGTH_SECONDS) {
                    emit(result, samples, firstSleepIdx, lastSleepIdx);
                    firstSleepIdx = -1;
                    lastSleepIdx = -1;
                }
            }

            previousSample = sample;
        }

        if (firstSleepIdx != -1) {
            emit(result, samples, firstSleepIdx, lastSleepIdx);
        }

        return result;
    }

    /**
     * Builds a session from samples[firstSleepIdx...lastSleepIdx] (inclusive) and appends it to
     * {@code result} if it is long enough. firstSleepIdx and lastSleepIdx are always indices of
     * samples for which {@link ActivityKind#isSleep(ActivityKind)} is true, so the session's bounds
     * are never null.
     */
    private static void emit(final List<SleepSession> result,
                             final List<? extends ActivitySample> samples,
                             final int firstSleepIdx,
                             final int lastSleepIdx) {
        final long span = (samples.get(lastSleepIdx).getTimestamp() + TRAILING_STAGE_SECONDS) - samples.get(firstSleepIdx).getTimestamp();
        if (span <= MIN_SESSION_LENGTH_SECONDS) {
            return;
        }

        final List<SleepStage> stages = buildStages(samples, firstSleepIdx, lastSleepIdx);
        if (!stages.isEmpty()) {
            result.add(new SleepSession(stages));
        }
    }

    /**
     * Groups samples[firstIdx...lastIdx] (inclusive) into groups of the same kind. Non-sleep samples are
     * grouped into AWAKE_SLEEP.
     */
    private static List<SleepStage> buildStages(final List<? extends ActivitySample> samples,
                                                final int firstIdx,
                                                final int lastIdx) {
        final List<SleepStage> stages = new ArrayList<>();

        long stageStart = samples.get(firstIdx).getTimestamp();
        ActivityKind currentKind = toSleepKind(samples.get(firstIdx + 1));

        for (int i = firstIdx + 2; i <= lastIdx; i++) {
            final ActivityKind kind = toSleepKind(samples.get(i));
            if (kind != currentKind) {
                final long stageEnd = samples.get(i - 1).getTimestamp();
                stages.add(new SleepStage(currentKind, stageStart * 1000L, stageEnd * 1000L));
                currentKind = kind;
                stageStart = stageEnd;
            }
        }

        final long sessionEnd = samples.get(lastIdx).getTimestamp() + TRAILING_STAGE_SECONDS;
        stages.add(new SleepStage(currentKind, stageStart * 1000L, sessionEnd * 1000L));

        return stages;
    }

    private static ActivityKind toSleepKind(final ActivitySample sample) {
        return ActivityKind.isSleep(sample.getKind()) ? sample.getKind() : ActivityKind.AWAKE_SLEEP;
    }
}
