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
package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Covers the conversion of the ZeppOS TLV structures into the vendor-neutral
 * {@link ActivityTrack} collections that the FIT export reads.
 */
public class ZeppOsActivityDetailsParserTest extends TestBase {
    private static final long BASE_MILLIS = 1700000000000L;

    // TLV type codes, see ZeppOsActivityDetailsParser.Type
    private static final int TYPE_TIMESTAMP = 1;
    private static final int TYPE_STATUS = 4;
    private static final int TYPE_HEARTRATE = 8;
    private static final int TYPE_SWIMMING_INTERVAL = 20;

    private static final int STATUS_STOP = 6;

    // ---- tests ----

    @Test
    public void poolSwimProducesOneLengthPerInterval() throws Exception {
        final byte[] bytes = stream(
                tlv(TYPE_TIMESTAMP, timestamp(BASE_MILLIS)),
                tlv(TYPE_HEARTRATE, heartRate(1_000, 120)),
                // 25 m freestyle in 30 s.
                tlv(TYPE_SWIMMING_INTERVAL, swimmingInterval(30_000, 1, 25, 125, 2, 1200, 50, 40, 30_000, 180, 5)),
                // Offsets are a signed short, so the device re-anchors with a new timestamp.
                tlv(TYPE_TIMESTAMP, timestamp(BASE_MILLIS + 30_000)),
                // 25 m freestyle in 32 s.
                tlv(TYPE_SWIMMING_INTERVAL, swimmingInterval(32_000, 2, 25, 126, 2, 1250, 52, 38, 32_000, 175, 5)),
                tlv(TYPE_STATUS, status(32_000, STATUS_STOP))
        );

        final ZeppOsActivityTrack track = parse(bytes);

        assertEquals(2, track.getSwimmingIntervals().size());
        final List<ActivityTrack.LengthInfo> lengths = track.getLengths();
        assertEquals(2, lengths.size());

        final ActivityTrack.LengthInfo first = lengths.get(0);
        assertEquals(BASE_MILLIS / 1000, first.startTimeSec);
        assertEquals(30.0, first.totalElapsedTimeSec, 0.001);
        assertEquals(30.0, first.totalTimerTimeSec, 0.001);
        // The length TLV carries no stroke count.
        assertNull(first.totalStrokes);
        assertEquals(25f / 30f, first.avgSpeed, 0.001f);
        assertEquals(Integer.valueOf(0), first.swimStroke); // freestyle
        assertEquals(Integer.valueOf(1), first.lengthType); // active
        assertEquals(Integer.valueOf(40), first.avgSwimmingCadence);

        final ActivityTrack.LengthInfo second = lengths.get(1);
        assertEquals((BASE_MILLIS + 30_000) / 1000, second.startTimeSec);
        assertNull(second.totalStrokes);
    }

    @Test
    public void swimStyleMapsToFitEnum() throws Exception {
        // ZeppOS 1 breaststroke, 2 freestyle, 3 backstroke, 4 butterfly, 6 medley, 9 unknown
        final int[] zeppOsStyles = {1, 2, 3, 4, 6, 9};
        final Integer[] expectedFit = {2, 0, 1, 3, 6, null};

        for (int i = 0; i < zeppOsStyles.length; i++) {
            final byte[] bytes = stream(
                    tlv(TYPE_TIMESTAMP, timestamp(BASE_MILLIS)),
                    tlv(TYPE_SWIMMING_INTERVAL, swimmingInterval(30_000, 1, 25, 125, zeppOsStyles[i], 1200, 50, 40, 30_000, 180, 5))
            );

            final ZeppOsActivityTrack track = parse(bytes);

            assertEquals(1, track.getLengths().size());
            assertEquals("style " + zeppOsStyles[i], expectedFit[i], track.getLengths().get(0).swimStroke);
        }
    }

    @Test
    public void plainTrackProducesNoLengths() throws Exception {
        final byte[] bytes = stream(
                tlv(TYPE_TIMESTAMP, timestamp(BASE_MILLIS)),
                tlv(TYPE_HEARTRATE, heartRate(1_000, 120)),
                tlv(TYPE_STATUS, status(2_000, STATUS_STOP))
        );

        final ZeppOsActivityTrack track = parse(bytes);

        assertNotNull(track);
        assertTrue(track.getLengths().isEmpty());
        assertEquals(1, track.getSegments().size());
    }

    @Test
    public void decodesCumulativeDistanceInCentimetres() throws Exception {
        final long start = 1700000000000L;
        final ByteBuffer data = ByteBuffer.allocate(128).order(ByteOrder.LITTLE_ENDIAN);
        data.put((byte) 1).put((byte) 12).putInt(0).putLong(start);
        final int[] distances = {0, 118, 1045, 2719, 2719};
        for (int i = 0; i < distances.length; i++) {
            data.put((byte) 0x1F).put((byte) 0x09).put((byte) 6);
            data.putShort((short) (i * 1000)).putInt(distances[i]);
            // A second metric at the same time commits the updated distance.
            data.put((byte) 8).put((byte) 3).putShort((short) (i * 1000)).put((byte) 87);
        }
        final byte[] bytes = new byte[data.position()];
        data.flip();
        data.get(bytes);
        final BaseActivitySummary summary = new BaseActivitySummary();
        final User user = new User();
        user.setId(1L);
        final Device device = new Device();
        device.setId(1L);
        summary.setUser(user);
        summary.setDevice(device);
        final ZeppOsActivityTrack track = new ZeppOsActivityDetailsParser(summary).parse(bytes);
        final List<ActivityPoint> points = track.getSegments().get(0);
        assertEquals(distances.length, points.size());
        for (int i = 0; i < distances.length; i++) {
            assertEquals(start + i * 1000L, points.get(i).getTime().getTime());
            assertEquals(distances[i] / 100.0, points.get(i).getDistance(), 0.000001);
        }
    }

    @Test
    @Ignore("helper test for development, remove this while debugging")
    public void localTest() throws Exception {
        final byte[] bytes = Files.readAllBytes(Paths.get("/storage/downloads/raw_details.bin"));

        final ZeppOsActivityTrack track = parse(bytes);
        assertNotNull(track);
    }

    // ---- helpers ----

    private static ZeppOsActivityTrack parse(final byte[] bytes) throws Exception {
        final User user = new User();
        user.setId(1L);
        final Device device = new Device();
        device.setId(1L);

        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setUser(user);
        summary.setDevice(device);
        summary.setBaseLatitude(0);
        summary.setBaseLongitude(0);
        summary.setBaseAltitude(0);

        return new ZeppOsActivityDetailsParser(summary).parse(bytes);
    }

    /** Wraps a payload in the single-byte tag + single-byte length BER encoding the parser reads. */
    private static byte[] tlv(final int type, final byte[] payload) {
        return ByteBuffer.allocate(2 + payload.length)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put((byte) type)
                .put((byte) payload.length)
                .put(payload)
                .array();
    }

    private static byte[] stream(final byte[]... tlvs) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (final byte[] tlv : tlvs) {
            write(out, tlv);
        }
        return out.toByteArray();
    }

    private static void write(final ByteArrayOutputStream out, final byte[] bytes) {
        out.write(bytes, 0, bytes.length);
    }

    private static ByteBuffer payload(final int length) {
        return ByteBuffer.allocate(length).order(ByteOrder.LITTLE_ENDIAN);
    }

    private static byte[] timestamp(final long millis) {
        return payload(12).putInt(0).putLong(millis).array();
    }

    private static byte[] status(final int offsetMillis, final int statusCode) {
        return payload(4).putShort((short) offsetMillis).putShort((short) statusCode).array();
    }

    private static byte[] heartRate(final int offsetMillis, final int heartRate) {
        return payload(3).putShort((short) offsetMillis).put((byte) heartRate).array();
    }

    private static byte[] swimmingInterval(final int offsetMillis,
                                           final int number,
                                           final int poolLengthMeters,
                                           final int hr,
                                           final int style,
                                           final int pace,
                                           final int swolf,
                                           final int strokeRate,
                                           final int durationMillis,
                                           final int strokeDistance,
                                           final int calories) {
        return payload(31)
                .putShort((short) offsetMillis)
                .put((byte) 0)
                .putShort((short) number)
                .putShort((short) poolLengthMeters)
                .put((byte) 0)
                .put((byte) hr)
                .putShort((short) 1)
                .putShort((short) style)
                .putShort((short) pace)
                .putShort((short) swolf)
                .putShort((short) strokeRate)
                .putInt(durationMillis)
                .putInt(0)
                .putShort((short) strokeDistance)
                .putShort((short) calories)
                .array();
    }
}
