/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.test;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.amazfitbip.BipActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.export.GPXExporter;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.service.btle.BLETypeConversions;
import nodomain.freeyourgadget.gadgetbridge.service.devices.huami.HuamiActivityDetailsParser;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.FileUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HuamiActivityDetailsParserTest extends TestBase {
    // Captured on device: Mi Band 6 running workout
    private static final String DETAILS =
            "0b056e040000003e" +
            "0bfeae041501ae4b" +
            "0b02ad041501a600" +
            "0b02ad041501a600"; // The same point repeated twice to test for duplicate removal.
    private static final URL DETAILS_1 = HuamiActivityDetailsParserTest.class.getClassLoader().getResource("ActivityDetailsDump1.txt");
    private static final long MAX_DETAILS = 1024 * 1024;
    private static Date baseTime;

    @Test
    public void testDuplicateRemoval() throws Exception{
        List<ActivityPoint> points = parseToPoints(defaultSummary());

        // The same point is repeated twice in the input, but it shouldn't in the output
        assertEquals(3, points.size());

        assertFalse(points.get(0).equals(points.get(1)));
        assertFalse(points.get(1).equals(points.get(2)));
    }

    @Test
    public void testTimeOffsetOverflow() throws Exception{
        List<ActivityPoint> points = parseToPoints(defaultSummary());

        assertEquals(points.get(0).getTime(), new Date(0x05 * 1000));
        assertEquals(points.get(1).getTime(), new Date(0xfe * 1000));

        // 0x02 is less than 0xFE, but timeOffset should overflow.
        assertTrue(points.get(2).getTime().after(points.get(1).getTime()));
    }

    @Test
    public void testDropValues() throws Exception{
        BaseActivitySummary summary = defaultSummary();
        summary.setActivityKind(ActivityKind.CYCLING.getCode());
        List<ActivityPoint> points = parseToPoints(summary);

        // For activity types other than running these values shouldn't be set.
        for (ActivityPoint point : points){
            assertEquals(point.getCadence(), -1);
            assertEquals(point.getStepLength(), -1);
        }

        // HR should be set for all activity types.
        assertEquals(points.get(0).getHeartRate(), 0x6E);
        assertEquals(points.get(1).getHeartRate(), 0xAE);
        assertEquals(points.get(2).getHeartRate(), 0xAD);
    }

    @Test
    public void testValues() throws Exception{
        BaseActivitySummary summary = defaultSummary();
        summary.setActivityKind(ActivityKind.RUNNING.getCode());
        List<ActivityPoint> points = parseToPoints(summary);

        assertEquals(points.get(0).getHeartRate(), 0x6E);
        assertEquals(points.get(1).getHeartRate(), 0xAE);
        assertEquals(points.get(2).getHeartRate(), 0xAD);

        // If a value is 0 in input it shouldn't be set
        assertEquals(points.get(0).getCadence(), -1);
        assertEquals(points.get(1).getCadence(), 0xAE);
        assertEquals(points.get(2).getCadence(), 0xA6);

        assertEquals(points.get(0).getStepLength() / 10, 0x3E);
        assertEquals(points.get(1).getStepLength() / 10, 0x4B);
        assertEquals(points.get(2).getStepLength(), -1);
    }

    @BeforeClass
    public static void setUpSuite() throws Exception {
        baseTime = DateTimeUtils.ISO_8601_FORMAT.parse("2017-01-20T14:00:00-00:00"); // yyyy-mm-dd'T'hh:mm:ssZ
    }

    @Test
    public void testActivityDetails() throws Exception {
        BipActivitySummary summary = createSummary();

        HuamiActivityDetailsParser parser = new HuamiActivityDetailsParser(summary);
        parser.setSkipCounterByte(true);
        try (InputStream in = getContents(DETAILS_1)) {
            ActivityTrack track = parser.parse(FileUtils.readAll(in, MAX_DETAILS));
            assertEquals("SuperBand 2000", track.getDevice().getName());
            assertEquals("Elvis", track.getUser().getName());

            List<List<ActivityPoint>> segments = track.getSegments();
            assertEquals(1, segments.size());
            assertEquals(972, segments.get(0).size());
        }
    }

    @Test
    public void testGPXExport() throws Exception {
        BipActivitySummary summary = createSummary();

        int baseLongi = BLETypeConversions.toUint32((byte) 0xd6, (byte) 0xc4,(byte) 0x62,(byte) 0x02);
        int baseLati = BLETypeConversions.toUint32((byte) 0xff, (byte) 0xa9, (byte) 0x61, (byte) 0x9);
        int baseAlti = BLETypeConversions.toUint32((byte) 0x30, (byte) 0x0, (byte) 0x0, (byte) 0x0);

        summary.setBaseLongitude(baseLongi);
        summary.setBaseLatitude(baseLati);
        summary.setBaseAltitude(baseAlti);

        HuamiActivityDetailsParser parser = new HuamiActivityDetailsParser(summary);
        parser.setSkipCounterByte(true);
        try (InputStream in = getContents(DETAILS_1)) {
            ActivityTrack track = parser.parse(FileUtils.readAll(in, MAX_DETAILS));

            List<List<ActivityPoint>> segments = track.getSegments();
            assertEquals(1, segments.size());
            assertEquals(972, segments.get(0).size());

            GPXExporter exporter = new GPXExporter();
            exporter.setIncludeHeartRate(false);
            exporter.setCreator(getClass().getName());
            File targetFile = File.createTempFile("gadgetbridge-track", ".gpx");
            System.out.println("Writing GPX file: " + targetFile);
            exporter.performExport(track, targetFile, summary);

            assertTrue(targetFile.length() > 1024);
        }

    }

    private List<ActivityPoint> parseToPoints(BaseActivitySummary summary) throws Exception{
        byte[] bytes = GB.hexStringToByteArray(DETAILS);
        HuamiActivityDetailsParser parser = new HuamiActivityDetailsParser(summary);
        return parser.parse(bytes).getAllPoints();
    }

    private BaseActivitySummary defaultSummary(){
        BaseActivitySummary summary = new BaseActivitySummary();
        summary.setStartTime(new Date(0l)); // Set date to epoch for easier offset calculations
        summary.setBaseLongitude(1);
        summary.setBaseLatitude(1);
        summary.setBaseAltitude(1);
        summary.setUser(new User(0l));
        summary.setDevice(new Device(0l));

        return summary;
    }

    private BipActivitySummary createSummary() {
        BipActivitySummary summary = new BipActivitySummary();
        summary.setBaseLongitude(1);
        summary.setBaseLatitude(1);
        summary.setBaseAltitude(1);
        summary.setStartTime(baseTime);
        User dummyUser = new User(0L);
        dummyUser.setName("Elvis");
        summary.setName("testtrack");
        summary.setUser(dummyUser);
        Device device = new Device(0l);
        device.setName("SuperBand 2000");
        summary.setDevice(device);

        return summary;
    }

    private InputStream getContents(URL hexFile) throws IOException {
        return new HexToBinaryInputStream(hexFile.openStream());
    }
}
