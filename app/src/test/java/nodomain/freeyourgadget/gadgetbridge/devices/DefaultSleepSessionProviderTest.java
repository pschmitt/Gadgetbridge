package nodomain.freeyourgadget.gadgetbridge.devices;

import static org.junit.Assert.assertEquals;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.SleepSession;

public class DefaultSleepSessionProviderTest {

    // From sleep-session-realistic-night.csv
    private static final int NIGHT_START = 1705356000; // first sample
    private static final int NIGHT_LAST_SAMPLE = 1705376400; // last sample; session end = +60s pad
    private static final int NIGHT_END = (int) (NIGHT_LAST_SAMPLE + SleepSessionSegmenter.TRAILING_STAGE_SECONDS);

    @Test
    public void fetchesAPaddedWindow() {
        RecordingSampleProvider recording = new RecordingSampleProvider();
        DefaultSleepSessionProvider provider = providerFor(recording);

        int tsFrom = 10_000;
        int tsTo = 20_000;
        provider.getSleepSessions(tsFrom, tsTo);

        assertEquals(tsFrom - DefaultSleepSessionProvider.LOOK_BACK_SECONDS, recording.lastTsFrom);
        assertEquals(tsTo + DefaultSleepSessionProvider.LOOK_AHEAD_SECONDS, recording.lastTsTo);
    }

    @Test
    public void sessionStartingBeforeWindow_isExcluded() {
        DefaultSleepSessionProvider provider = providerFor(realisticNightSampleProvider());

        // The window opens exactly where the night ends: the whole night sits before it, so the
        // provider has to fetch back far enough to detect it correctly, but must still exclude it -
        // it belongs to an earlier window.
        int tsFrom = NIGHT_END;
        int tsTo = tsFrom + 20_000;

        List<SleepSession> sessions = provider.getSleepSessions(tsFrom, tsTo);
        assertEquals(0, sessions.size());
    }

    @SuppressWarnings("SpellCheckingInspection")
    @Test
    public void sessionStartingJustInsideWindow_isReturnedWholeEvenPastTsTo() {
        DefaultSleepSessionProvider provider = providerFor(realisticNightSampleProvider());

        // The window opens just before the night starts and closes a minute after - the night's
        // start falls inside it, but the night itself runs almost 6 hours past tsTo. Must come back
        // complete, not clipped to tsTo.
        int tsFrom = NIGHT_START - 100;
        int tsTo = NIGHT_START + 60;

        List<SleepSession> sessions = provider.getSleepSessions(tsFrom, tsTo);
        assertEquals(1, sessions.size());
        SleepSession session = sessions.get(0);
        assertEquals(NIGHT_START * 1000L, session.getStartTime());
        assertEquals(NIGHT_END * 1000L, session.getEndTime());
    }

    private static DefaultSleepSessionProvider providerFor(final SampleProvider<? extends AbstractActivitySample> sampleProvider) {
        final DeviceCoordinator coordinator = new FixedSampleDeviceCoordinator(sampleProvider);
        final GBDevice device = new GBDevice("00:00:00:00:00:00", "Test", "Test", null, DeviceType.UNKNOWN) {
            @NonNull
            @Override
            public DeviceCoordinator getDeviceCoordinator() {
                return coordinator;
            }
        };
        //noinspection DataFlowIssue
        return new DefaultSleepSessionProvider(device, null);
    }

    private static CsvSampleProvider realisticNightSampleProvider() {
        return new CsvSampleProvider(null, null, "/sleep-session-realistic-night.csv");
    }

    /**
     * Records the window it was asked for and returns nothing.
     */
    private static final class RecordingSampleProvider extends AbstractInMemorySampleProvider<CsvSampleProvider.CsvSample> {
        private int lastTsFrom = Integer.MIN_VALUE;
        private int lastTsTo = Integer.MIN_VALUE;

        RecordingSampleProvider() {
            super(null, null);
        }

        @Override
        protected List<CsvSampleProvider.CsvSample> getGBActivitySamples(final int timestampFrom, final int timestampTo) {
            lastTsFrom = timestampFrom;
            lastTsTo = timestampTo;
            return new ArrayList<>();
        }
    }
}
