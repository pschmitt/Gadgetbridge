package nodomain.freeyourgadget.gadgetbridge.devices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;

public class CsvSampleProviderTest {

    @Test
    public void parsesRequiredAndOptionalColumns() throws IOException {
        CsvSampleProvider csv = fromString(
                """
                        timestamp,kind,steps,heartRate
                        1000,LIGHT_SLEEP,0,55
                        1060,DEEP_SLEEP,0,50
                        """
        );

        List<CsvSampleProvider.CsvSample> samples = csv.getAllSamples();
        assertEquals(2, samples.size());
        assertEquals(1000, samples.get(0).getTimestamp());
        assertEquals(ActivityKind.LIGHT_SLEEP, samples.get(0).getKind());
        assertEquals(0, samples.get(0).getSteps());
        assertEquals(55, samples.get(0).getHeartRate());
    }

    @Test
    public void columnsCanAppearInAnyOrder() throws IOException {
        CsvSampleProvider csv = fromString(
                """
                        heartRate,kind,timestamp
                        55,LIGHT_SLEEP,1000
                        """
        );

        CsvSampleProvider.CsvSample sample = csv.getAllSamples().get(0);
        assertEquals(1000, sample.getTimestamp());
        assertEquals(ActivityKind.LIGHT_SLEEP, sample.getKind());
        assertEquals(55, sample.getHeartRate());
    }

    @Test
    public void optionalColumnsDefaultToNotMeasuredWhenAbsent() throws IOException {
        CsvSampleProvider csv = fromString(
                """
                        timestamp,kind
                        1000,LIGHT_SLEEP
                        """
        );

        CsvSampleProvider.CsvSample sample = csv.getAllSamples().get(0);
        assertEquals(ActivitySample.NOT_MEASURED, sample.getSteps());
        assertEquals(ActivitySample.NOT_MEASURED, sample.getHeartRate());
    }

    @Test
    public void skipsBlankLinesAndComments() throws IOException {
        CsvSampleProvider csv = fromString(
                """
                        # a fixture describing one short session
                        timestamp,kind

                        # the session itself
                        1000,LIGHT_SLEEP

                        """
        );

        assertEquals(1, csv.getAllSamples().size());
    }

    @Test
    public void sortsSamplesByTimestampRegardlessOfFileOrder() throws IOException {
        CsvSampleProvider csv = fromString(
                """
                        timestamp,kind
                        2000,DEEP_SLEEP
                        1000,LIGHT_SLEEP
                        """
        );

        List<CsvSampleProvider.CsvSample> samples = csv.getAllSamples();
        assertEquals(1000, samples.get(0).getTimestamp());
        assertEquals(2000, samples.get(1).getTimestamp());
    }

    @Test
    public void missingRequiredColumn_throws() {
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () ->
                fromString("kind\nLIGHT_SLEEP\n"));
        assertTrue(Objects.requireNonNull(e.getMessage()).contains("timestamp"));
    }

    @Test
    public void getAllActivitySamples_filtersToInclusiveRange() throws IOException {
        CsvSampleProvider csv = fromString(
                """
                        timestamp,kind
                        1000,LIGHT_SLEEP
                        2000,DEEP_SLEEP
                        3000,REM_SLEEP
                        """
        );

        List<CsvSampleProvider.CsvSample> windowed = csv.getAllActivitySamples(1000, 2000);
        assertEquals(2, windowed.size());
        assertEquals(1000, windowed.get(0).getTimestamp());
        assertEquals(2000, windowed.get(1).getTimestamp());
    }

    @Test
    public void emptyFile_yieldsNoSamples() throws IOException {
        assertEquals(0, fromString("").getAllSamples().size());
    }

    private static CsvSampleProvider fromString(final String csv) throws IOException {
        return new CsvSampleProvider(null, null, new ByteArrayInputStream(csv.getBytes(StandardCharsets.UTF_8)));
    }
}
