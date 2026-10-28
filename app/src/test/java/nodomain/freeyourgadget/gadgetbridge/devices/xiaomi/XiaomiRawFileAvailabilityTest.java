/*  Copyright (C) 2026 Dany Mestas

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiActivityFile;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.workout.Workout;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileFetcher;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityFileId;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.XiaomiActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.impl.WorkoutSummaryParser;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;
import nodomain.freeyourgadget.gadgetbridge.util.CheckSums;

/**
 * The raw summary, details and GPS files of a workout stay reachable from the workout whatever
 * its subtype and whether any parser understands them.
 */
public class XiaomiRawFileAvailabilityTest extends TestBase {
    private static final Date START = new Date(1_780_000_000_000L);
    private static final int TYPE_SPORTS = 1;
    /** Not in {@link XiaomiActivityFileId.Subtype}. */
    private static final int SUBTYPE_UNKNOWN = 0x11;
    private static final int SUBTYPE_FREESTYLE = 0x08;

    @Rule
    public final TemporaryFolder tmp = new TemporaryFolder();

    private GBDevice device;

    @Before
    public void setUpDevice() {
        // The address names the export directory, and ':' is not allowed in a Windows path
        device = createDummyGDevice("000000000032");
    }

    private static XiaomiActivityFileId fileId(final int subtype,
                                               final XiaomiActivityFileId.DetailType detailType,
                                               final int version) {
        return new XiaomiActivityFileId(START, 0, TYPE_SPORTS, subtype, detailType.getCode(), version);
    }

    /** File id, padding byte, payload, CRC32, as the fetcher stores it. */
    private static byte[] fileBytes(final XiaomiActivityFileId fileId, final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.allocate(7 + 1 + payload.length + 4).order(ByteOrder.LITTLE_ENDIAN);
        buf.put(fileId.toBytes());
        buf.put((byte) 0);
        buf.put(payload);
        buf.putInt(CheckSums.getCRC32(buf.array(), 0, buf.capacity() - 4));
        return buf.array();
    }

    private static File write(final File file, final byte[] bytes) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (OutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
        return file;
    }

    private static BaseActivitySummary summaryOf(final int subtype) {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setStartTime(START);
        summary.setRawSummaryData(fileBytes(fileId(subtype, XiaomiActivityFileId.DetailType.SUMMARY, 1), new byte[16]));
        return summary;
    }

    @Test
    public void reprocessRegistersDetailsNoParserUnderstands() throws IOException {
        final XiaomiActivityFileId detailsId = fileId(SUBTYPE_UNKNOWN, XiaomiActivityFileId.DetailType.DETAILS, 1);
        final File details = write(new File(tmp.getRoot(), "details.bin"), fileBytes(detailsId, new byte[]{1, 2, 3, 4}));

        XiaomiSettingsCustomizer.reprocessActivityFile(getContext(), device, details);

        final List<XiaomiActivityFile> rows = daoSession.getXiaomiActivityFileDao().loadAll();
        assertEquals(1, rows.size());
        assertEquals(details.getAbsolutePath(), rows.get(0).getFilePath());
        assertEquals(details.getAbsoluteFile(), XiaomiActivityTrackProvider.getRawFile(
                device, summaryOf(SUBTYPE_UNKNOWN), XiaomiActivityFileId.DetailType.DETAILS).getAbsoluteFile());
    }

    @Test
    public void rawFilesWithoutRegistryRowAreFoundOnDisk() throws IOException {
        final XiaomiActivityFileId detailsId = fileId(SUBTYPE_UNKNOWN, XiaomiActivityFileId.DetailType.DETAILS, 3);
        final XiaomiActivityFileId gpsId = fileId(SUBTYPE_UNKNOWN, XiaomiActivityFileId.DetailType.GPS_TRACK, 2);
        final File details = write(XiaomiActivityFileFetcher.getRawFile(device, detailsId), fileBytes(detailsId, new byte[4]));
        final File gps = write(XiaomiActivityFileFetcher.getRawFile(device, gpsId), fileBytes(gpsId, new byte[4]));

        final BaseActivitySummary summary = summaryOf(SUBTYPE_UNKNOWN);

        assertEquals(0, daoSession.getXiaomiActivityFileDao().count());
        assertEquals(details.getAbsoluteFile(), XiaomiActivityTrackProvider.getRawFile(
                device, summary, XiaomiActivityFileId.DetailType.DETAILS).getAbsoluteFile());
        assertEquals(gps.getAbsoluteFile(), XiaomiActivityTrackProvider.getRawFile(
                device, summary, XiaomiActivityFileId.DetailType.GPS_TRACK).getAbsoluteFile());
    }

    @Test
    public void summaryThatFailsToParseIsStoredWithItsRawBytes() {
        // Freestyle v8 has a 6-byte header; 2 payload bytes plus the CRC run out right after it
        final XiaomiActivityFileId summaryId = fileId(SUBTYPE_FREESTYLE, XiaomiActivityFileId.DetailType.SUMMARY, 8);
        final byte[] bytes = fileBytes(summaryId, new byte[]{1, 2});

        assertFalse(new WorkoutSummaryParser().parse(getContext(), device, summaryId, bytes));

        final List<BaseActivitySummary> summaries = daoSession.getBaseActivitySummaryDao().loadAll();
        assertEquals(1, summaries.size());
        final BaseActivitySummary stored = summaries.get(0);
        assertEquals(START, stored.getStartTime());
        assertEquals(ActivityKind.UNKNOWN.getCode(), stored.getActivityKind());
        assertNotNull(stored.getRawSummaryData());
        assertArrayEquals(bytes, stored.getRawSummaryData());

        final Workout workout = new WorkoutSummaryParser().parseWorkout(stored, false);
        assertEquals(START, workout.getSummary().getEndTime());
        assertEquals(ActivityKind.UNKNOWN.getCode(), workout.getSummary().getActivityKind());
    }
}
