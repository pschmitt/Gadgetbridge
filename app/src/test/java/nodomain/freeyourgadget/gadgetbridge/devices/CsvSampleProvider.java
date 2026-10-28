package nodomain.freeyourgadget.gadgetbridge.devices;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;

public class CsvSampleProvider extends AbstractInMemorySampleProvider<CsvSampleProvider.CsvSample> {
    /// Sorted by timestamp
    private final List<CsvSample> samples;

    public CsvSampleProvider(final GBDevice device, final DaoSession session, final String resourcePath) {
        super(device, session);
        try (InputStream in = CsvSampleProvider.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalArgumentException("CSV not found on classpath: " + resourcePath);
            }
            this.samples = parse(in);
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to load CSV: " + resourcePath, e);
        }
    }

    CsvSampleProvider(final GBDevice device,
                      final DaoSession session,
                      final InputStream in) throws IOException {
        super(device, session);
        this.samples = parse(in);
    }

    @SuppressWarnings("DataFlowIssue")
    private List<CsvSample> parse(final InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line = nextCsvLine(reader);
            if (line == null) {
                return new ArrayList<>();
            }

            final Map<String, Integer> columnIndex = new HashMap<>();
            final String[] header = line.split(",", -1);
            for (int i = 0; i < header.length; i++) {
                columnIndex.put(header[i].trim(), i);
            }
            requireColumn(columnIndex, "timestamp");
            requireColumn(columnIndex, "kind");

            final List<CsvSample> result = new ArrayList<>();
            while ((line = nextCsvLine(reader)) != null) {
                final String[] fields = line.split(",", -1);
                final int timestamp = Integer.parseInt(fields[columnIndex.get("timestamp")].trim());
                final ActivityKind kind = ActivityKind.valueOf(fields[columnIndex.get("kind")].trim());
                final int steps = intColumn(fields, columnIndex, "steps");
                final int heartRate = intColumn(fields, columnIndex, "heartRate");
                result.add(new CsvSample(timestamp, kind, steps, heartRate));
            }
            result.sort(Comparator.comparingInt(CsvSample::getTimestamp));
            return result;
        }
    }

    private static String nextCsvLine(final BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            final String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return line;
            }
        }
        return null;
    }

    private static void requireColumn(final Map<String, Integer> columnIndex, final String name) {
        if (!columnIndex.containsKey(name)) {
            throw new IllegalArgumentException("CSV is missing required column '" + name + "'");
        }
    }

    private static int intColumn(final String[] fields, final Map<String, Integer> columnIndex, final String name) {
        final Integer idx = columnIndex.get(name);
        if (idx == null || idx >= fields.length || fields[idx].trim().isEmpty()) {
            return ActivitySample.NOT_MEASURED;
        }
        return Integer.parseInt(fields[idx].trim());
    }

    @Override
    protected List<CsvSample> getGBActivitySamples(final int timestampFrom, final int timestampTo) {
        final List<CsvSample> result = new ArrayList<>();
        for (final CsvSample sample : samples) {
            if (sample.getTimestamp() >= timestampFrom && sample.getTimestamp() <= timestampTo) {
                result.add(sample);
            }
        }
        return result;
    }

    public List<CsvSample> getAllSamples() {
        return samples;
    }

    public class CsvSample extends AbstractActivitySample {
        private int timestamp;
        private final ActivityKind kind;
        private int steps;
        private int heartRate;

        CsvSample(final int timestamp, final ActivityKind kind, final int steps, final int heartRate) {
            this.timestamp = timestamp;
            this.kind = kind;
            this.steps = steps;
            this.heartRate = heartRate;
        }

        @Override
        public SampleProvider<?> getProvider() {
            return CsvSampleProvider.this;
        }

        @Override
        public void setTimestamp(final int timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public void setUserId(final long userId) {
        }

        @Override
        public void setDeviceId(final long deviceId) {
        }

        @Override
        public long getDeviceId() {
            return 0;
        }

        @Override
        public long getUserId() {
            return 0;
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
        public int getRawKind() {
            return kind.getCode();
        }

        @Override
        public float getIntensity() {
            return 0;
        }

        @Override
        public int getSteps() {
            return steps;
        }

        @Override
        public void setSteps(int steps) {
            this.steps = steps;
        }

        @Override
        public int getHeartRate() {
            return heartRate;
        }

        @Override
        public void setHeartRate(final int value) {
            this.heartRate = value;
        }
    }
}
