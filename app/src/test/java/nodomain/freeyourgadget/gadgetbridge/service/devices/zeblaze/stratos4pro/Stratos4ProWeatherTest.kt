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

import nodomain.freeyourgadget.gadgetbridge.model.WeatherSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class Stratos4ProWeatherTest {
    private fun spec() = WeatherSpec().apply {
        location = "AB"
        currentTemp = 273 + 20
        todayMaxTemp = 273 + 25
        todayMinTemp = 273 - 5
        currentHumidity = 60
        uvIndex = 3f
        timestamp = 10 * 86400 + 3600
        currentConditionCode = 800
    }

    @Test
    fun current() {
        assertEquals(
            "ab050400410042" +
                    "ab06020014" + "ab07020019" + "ab0802fffb" + "ab0a0103" +
                    "ab0b04000d3d10" + "ab0f023c00",
            Stratos4ProWeather.currentBody(spec()).toHex()
        )
    }

    @Test
    fun forecastTodayOnly() {
        val body = Stratos4ProWeather.forecastBody(spec(), TimeZone.getTimeZone("UTC"))
        val hex = body.toHex()
        assertTrue(hex.startsWith("ab0b04000d3d10" + "ab0e32"))
        assertEquals(7 + 3 + 0x32, body.size)
        // Day start at midnight UTC, sunrise defaults to 06:00 when unknown.
        assertTrue(hex.contains("0904000d2f00"))
        assertTrue(hex.contains("0704" + be32Bytes(10 * 86400 + 6 * 3600).toHex()))
    }

    @Test
    fun forecastCapsDaysAndHours() {
        val w = spec()
        repeat(10) { w.forecasts.add(WeatherSpec.Daily()) }
        repeat(30) { w.hourly.add(WeatherSpec.Hourly()) }
        val body = Stratos4ProWeather.forecastBody(w, TimeZone.getTimeZone("UTC"))
        val fields = parseTlvList(body)
        assertEquals(24, fields.count { it == 0x0d })
        assertEquals(7, fields.count { it == 0x0e })
    }

    @Test
    fun conditions() {
        assertEquals(0, Stratos4ProWeather.conditionToWatch(800))
        assertEquals(7, Stratos4ProWeather.conditionToWatch(501))
        assertEquals(4, Stratos4ProWeather.conditionToWatch(200))
        assertEquals(4, Stratos4ProWeather.conditionToWatch(781))
        assertEquals(18, Stratos4ProWeather.conditionToWatch(741))
        assertEquals(2, Stratos4ProWeather.conditionToWatch(3200))
    }

    @Test
    fun languages() {
        assertEquals(5, Stratos4ProLanguage.id("de", null))
        assertEquals(23, Stratos4ProLanguage.id("zh", "TW"))
        assertEquals(23, Stratos4ProLanguage.id("zh", "MO"))
        assertEquals(1, Stratos4ProLanguage.id("zh", "SG"))
        assertEquals(1, Stratos4ProLanguage.id("zh", "CN"))
        assertNull(Stratos4ProLanguage.id("xx", null))
        assertNull(Stratos4ProLanguage.idForPref("auto"))
        assertNull(Stratos4ProLanguage.idForPref("bn"))
        assertEquals(9, Stratos4ProLanguage.idForPref("pt_BR"))
    }

    /** Field ids of an `ab <field> <len> <value>` run. */
    private fun parseTlvList(b: ByteArray): List<Int> {
        val out = ArrayList<Int>()
        var i = 0
        while (i + 3 <= b.size) {
            out.add(u8(b, i + 1))
            i += 3 + u8(b, i + 2)
        }
        return out
    }
}
