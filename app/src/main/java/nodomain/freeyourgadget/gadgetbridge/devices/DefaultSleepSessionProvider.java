package nodomain.freeyourgadget.gadgetbridge.devices;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;

/**
 * Derives sessions from a device's activity samples via {@link SleepSessionSegmenter}.
 */
public class DefaultSleepSessionProvider implements SleepSessionProvider {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultSleepSessionProvider.class);

    /**
     * How far before tsFrom to fetch samples from, so a session that started before tsFrom is seen
     * and correctly excluded (rather than showing up truncated).
     */
    static final int LOOK_BACK_SECONDS = 6 * 60 * 60;

    /**
     * How far past tsTo to fetch samples from, so a session starting right at the edge of the
     * requested range is captured whole rather than cut off.
     */
    static final int LOOK_AHEAD_SECONDS = 12 * 60 * 60;

    private final GBDevice device;
    private final DaoSession session;

    public DefaultSleepSessionProvider(@NonNull final GBDevice device,
                                       @NonNull final DaoSession session) {
        this.device = device;
        this.session = session;
    }

    @NonNull
    @Override
    public List<SleepSession> getSleepSessions(final int tsFrom, final int tsTo) {
        final SampleProvider<? extends ActivitySample> provider = device.getDeviceCoordinator().getSampleProvider(device, session);
        if (provider == null) {
            LOG.error("Activity sample provider is null for {}", device);
            return new ArrayList<>();
        }

        final List<? extends ActivitySample> samples = provider.getAllActivitySamples(
                tsFrom - LOOK_BACK_SECONDS,
                tsTo + LOOK_AHEAD_SECONDS
        );

        final List<SleepSession> sessions = SleepSessionSegmenter.segment(samples);

        final List<SleepSession> result = new ArrayList<>();
        for (final SleepSession candidate : sessions) {
            final long startSeconds = candidate.getStartTime() / 1000L;
            if (startSeconds >= tsFrom && startSeconds < tsTo) {
                result.add(candidate);
            }
        }
        return result;
    }
}
