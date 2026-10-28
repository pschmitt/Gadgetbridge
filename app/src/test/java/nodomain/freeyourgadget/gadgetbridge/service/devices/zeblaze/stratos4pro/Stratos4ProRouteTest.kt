/*  Copyright (C) 2026 Vladimir Tasic

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro

import nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro.Stratos4ProGpxRouteInstallHandler.Companion.fitToGpx
import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.gpx.GpxParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Stratos4ProRouteTest : TestBase() {
    private fun resource(name: String) = javaClass.getResourceAsStream(name)!!.readBytes()

    @Test
    fun routeLayout() {
        val frames = routeFrames(1_778_384_896, 1234, "Ab", listOf(GPSCoordinate(21.4311987, 41.9775123)))
        val body = "0100046a000000" + "020004000004d2" + "030004" + "00410062" + "040008" + "190542930cc62433"
        assertEquals(listOf("01e9ab0500$body", "01e9ab05fd" + byteArrayOf(xor(frames[0])).toHex()), frames.map { it.toHex() })
        // Southern / western coordinates are two's complement.
        assertTrue(routeFrames(0, 0, "", listOf(GPSCoordinate(-151.2093, -33.8688)))[0].toHex().endsWith("ebd00800a5df4ab8"))
    }

    @Test
    fun largestRouteStaysUnderTheWatchLimit() {
        val points = simplifyRoute(List(100) { GPSCoordinate(it * 1e-3, 0.0) }, ROUTE_MAX_POINTS)
        val frames = routeFrames(0, 0, "x".repeat(100), points)
        val body = frames.dropLast(1).flatMap { it.drop(5) }
        assertEquals(7 + 7 + 3 + 2 * ROUTE_MAX_NAME + 3 + 8 * ROUTE_MAX_POINTS, body.size)
        assertTrue(body.size <= 832)
        assertEquals(listOf(0, 1, 2, 3), frames.dropLast(1).map { it[4].toInt() })
    }

    @Test
    fun simplifyKeepsEndsAndCorners() {
        // An L: 50 points east, then 50 north. The corner must survive a cut to 3.
        val pts = (0..49).map { GPSCoordinate(it * 1e-4, 0.0) } + (1..50).map { GPSCoordinate(49e-4, it * 1e-4) }
        assertEquals(listOf(pts.first(), pts[49], pts.last()), simplifyRoute(pts, 3))
        assertEquals(72, simplifyRoute(pts, 72).size)
        assertEquals(pts, simplifyRoute(pts, 200))
    }

    /** The course FIT in the test resources was made from this GPX, so the tracks must line up. */
    @Test
    fun fitCourseMatchesItsGpx() {
        val gpx = GpxParser.parseGpx(resource("/gpx-exporter-test-SampleTrack.gpx"))!!
        val want = gpx.tracks.flatMap { t -> t.trackSegments.flatMap { it.trackPoints } }
        val got = fitToGpx(resource("/gpx-exporter-test-SampleTrack.course.fit"), "fallback")!!.tracks.single().trackSegments.single().trackPoints
        assertEquals(want.size, got.size)
        for ((w, g) in want.zip(got)) {
            assertEquals(w.latitude, g.latitude, 1e-6)
            assertEquals(w.longitude, g.longitude, 1e-6)
        }
        assertNull(fitToGpx(resource("/gpx-exporter-test-SampleTrack.gpx"), "x"))
    }
}
