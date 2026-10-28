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
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone

/** `e0` weather bodies (the TLVs after `01 e0 abab <idx>`); no state, no I/O. Temperatures in C. */
internal object Stratos4ProWeather {
    /** Current weather: `05 city | 06 temp:2 | 07 high:2 | 08 low:2 | 0a uv | 0b time:4 | 0f <humidity><condition>`. */
    fun currentBody(weather: WeatherSpec): ByteArray {
        val city = (weather.location ?: "").take(24).toByteArray(Charsets.UTF_16BE)
        val cur = weather.currentTemp - 273
        val high = weather.todayMaxTemp - 273
        val low = weather.todayMinTemp - 273
        val humidity = weather.currentHumidity.coerceIn(0, 100)
        val uv = weather.uvIndex.toInt().coerceIn(0, 15)
        val data = ByteArrayOutputStream()
        data.write(byteArrayOf(MODE_SET, 0x05, city.size.toByte())); data.write(city)
        data.write(byteArrayOf(MODE_SET, 0x06, 0x02, hi(cur), lo(cur)))
        data.write(byteArrayOf(MODE_SET, 0x07, 0x02, hi(high), lo(high)))
        data.write(byteArrayOf(MODE_SET, 0x08, 0x02, hi(low), lo(low)))
        data.write(byteArrayOf(MODE_SET, 0x0a, 0x01, uv.toByte()))
        data.write(byteArrayOf(MODE_SET, 0x0b, 0x04)); data.write(be32Bytes(weather.timestamp))
        // f0f = [humidity][condition].
        data.write(byteArrayOf(MODE_SET, 0x0f, 0x02, humidity.toByte(), conditionToWatch(weather.currentConditionCode).toByte()))
        return data.toByteArray()
    }

    /**
     * Forecast, a separate `e0` message: `ab 0b <update ts>`, then hourly entries (`ab 0d`) and
     * daily entries (`ab 0e`).
     * Hourly ab0d = {01: ts(4), 02: flag(1), 03: temp C(2)}.
     * Daily  ab0e = {01: high C(2), 02: low C(2), 03:(1)=0, 04: moonrise(4), 05: moonset(4),
     *               06:(1)=0, 07: sunrise(4), 08: sunset(4), 09: day midnight(4), 0a: humidity(1),
     *               0b: condition(1)}.
     */
    fun forecastBody(weather: WeatherSpec, timeZone: TimeZone = TimeZone.getDefault()): ByteArray {
        val body = ByteArrayOutputStream()
        body.write(byteArrayOf(MODE_SET, 0x0b, 0x04)); body.write(be32Bytes(weather.timestamp))

        // Hourly entries (may be empty depending on the weather source).
        for (h in weather.hourly.take(24)) {
            val temp = h.temp - 273
            val entry = ByteArrayOutputStream()
            entry.write(byteArrayOf(0x01, 0x04)); entry.write(be32Bytes(h.timestamp))
            entry.write(byteArrayOf(0x02, 0x01, conditionToWatch(h.conditionCode).toByte()))
            entry.write(byteArrayOf(0x03, 0x02, hi(temp), lo(temp)))
            val e = entry.toByteArray()
            body.write(byteArrayOf(MODE_SET, 0x0d, e.size.toByte())); body.write(e)
        }

        // Daily entries: today first, then the per-day forecasts.
        val days = ArrayList<WeatherSpec.Daily>()
        days.add(weather.todayAsDaily())
        days.addAll(weather.forecasts)
        val midnight = Calendar.getInstance(timeZone)
        midnight.timeInMillis = weather.timestamp * 1000L
        midnight.set(Calendar.HOUR_OF_DAY, 0)
        midnight.set(Calendar.MINUTE, 0)
        midnight.set(Calendar.SECOND, 0)
        midnight.set(Calendar.MILLISECOND, 0)
        for ((i, d) in days.take(7).withIndex()) {
            val dayCal = midnight.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_MONTH, i)
            val dayStart = (dayCal.timeInMillis / 1000L).toInt()
            val high = d.maxTemp - 273
            val low = d.minTemp - 273
            val humidity = (if (d.humidity in 1..100) d.humidity else weather.currentHumidity).coerceIn(0, 100)
            val sunrise = if (d.sunRise > 0) d.sunRise else dayStart + 6 * 3600
            val sunset = if (d.sunSet > 0) d.sunSet else dayStart + 21 * 3600
            val moonrise = if (d.moonRise > 0) d.moonRise else dayStart + 8 * 3600
            val moonset = if (d.moonSet > 0) d.moonSet else dayStart + 23 * 3600
            val cond = conditionToWatch(d.conditionCode)
            val entry = ByteArrayOutputStream()
            entry.write(byteArrayOf(0x01, 0x02, hi(high), lo(high)))
            entry.write(byteArrayOf(0x02, 0x02, hi(low), lo(low)))
            entry.write(byteArrayOf(0x03, 0x01, 0x00))
            entry.write(byteArrayOf(0x04, 0x04)); entry.write(be32Bytes(moonrise))
            entry.write(byteArrayOf(0x05, 0x04)); entry.write(be32Bytes(moonset))
            entry.write(byteArrayOf(0x06, 0x01, cond.toByte()))
            entry.write(byteArrayOf(0x07, 0x04)); entry.write(be32Bytes(sunrise))
            entry.write(byteArrayOf(0x08, 0x04)); entry.write(be32Bytes(sunset))
            entry.write(byteArrayOf(0x09, 0x04)); entry.write(be32Bytes(dayStart))
            entry.write(byteArrayOf(0x0a, 0x01, humidity.toByte()))
            entry.write(byteArrayOf(0x0b, 0x01, cond.toByte()))
            val e = entry.toByteArray()
            body.write(byteArrayOf(MODE_SET, 0x0e, e.size.toByte())); body.write(e)
        }
        return body.toByteArray()
    }

    /**
     * OpenWeatherMap condition -> watch icon (0..31 catalogued on hardware): 0 clear, 1 partly
     * cloudy, 2 cloudy, 3 light rain, 4 thunderstorm, 5 thunderstorm+hail, 6 hail/ice, 7 rain, 8
     * heavy rain, 9 extreme rain, 10 showers, 11 heavy showers, 12 violent showers, 13 snow shower,
     * 14 light snow, 15 snow, 16 heavy snow, 17 snowstorm, 18 fog/mist, 19 sleet, 20 blizzard.
     */
    fun conditionToWatch(owm: Int): Int = when (owm) {
        in 200..232 -> 4                 // thunderstorm
        in 300..399 -> 3                 // drizzle -> light rain (cloud+sun+rain, visually lighter)
        500 -> 3                         // light rain -> cloud+sun+rain
        501 -> 7                         // rain
        502 -> 8                         // heavy rain
        503, 504 -> 9                    // very heavy / extreme rain
        511 -> 6                         // freezing rain -> hail/ice
        520 -> 3                         // light shower -> changeable (cloud+sun+rain)
        521, 531 -> 10                   // shower
        522 -> 11                        // heavy shower
        in 505..599 -> 7                 // other rain
        600 -> 14                        // light snow
        601, 621 -> 15                   // snow
        602 -> 16                        // heavy snow
        611, 612, 613, 615, 616 -> 19    // sleet (rain + snow)
        620 -> 13                        // light shower snow
        622 -> 17                        // heavy shower snow
        in 600..699 -> 15                // other snow
        771 -> 12                        // squall
        781 -> 4                         // tornado -> storm
        in 700..799 -> 18                // fog / mist / haze / dust
        800 -> 0                         // clear
        801, 802 -> 1                    // few / scattered clouds
        803, 804 -> 2                    // broken / overcast
        906 -> 6                         // hail
        in 900..999 -> 4                 // extreme
        else -> 2
    }
}
