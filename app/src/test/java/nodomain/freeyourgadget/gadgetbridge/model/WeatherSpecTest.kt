package nodomain.freeyourgadget.gadgetbridge.model

import android.location.Location
import android.os.Parcel
import net.e175.klaus.solarpositioning.DeltaT
import net.e175.klaus.solarpositioning.SolarEvents
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert
import org.junit.Test
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.GregorianCalendar

class WeatherSpecTest : TestBase() {
    @Test
    fun testSolarEventsMatchReferenceAcrossSeasonsAndTimeZones() {
        val locations = listOf(
            Triple(-37.8, 145.0, "Australia/Melbourne"),
            Triple(51.5, -0.1, "Europe/London"),
            Triple(1.3, 103.8, "Asia/Singapore"),
            Triple(69.6, 19.0, "Europe/Oslo"),
            Triple(90.0, 0.0, "UTC"),
        )
        val dates = listOf(
            "2011-01-01", "2026-03-20", "2026-06-21", "2026-09-22",
            "2026-12-21", "2026-10-04", "2109-12-31"
        )
        for ((latitude, longitude, zone) in locations) {
            val location = Location("test").apply {
                this.latitude = latitude
                this.longitude = longitude
            }
            for (dateString in dates) {
                val date = LocalDate.parse(dateString)
                val calendar = GregorianCalendar.from(date.atStartOfDay(ZoneId.of(zone)))
                for (horizon in listOf(SolarEvents.Horizon.SUNRISE_SUNSET, SolarEvents.Horizon.CIVIL_TWILIGHT)) {
                    val reference = SolarEvents.spa().forDate(
                        date, calendar.timeZone.toZoneId(), latitude, longitude, DeltaT.estimate(date), horizon
                    )
                    val actual = WeatherSpec.solarDay(calendar, location, horizon)
                    val label = "$dateString $zone $horizon"
                    Assert.assertEquals(label, reference.start(), actual.start())
                    Assert.assertEquals(label, reference.end(), actual.end())
                    Assert.assertEquals(label, reference.stateAtStart(), actual.stateAtStart())
                    Assert.assertEquals(label, reference.alwaysAbove(), actual.alwaysAbove())
                    Assert.assertEquals(label, reference.alwaysBelow(), actual.alwaysBelow())
                    for ((expectedEvents, actualEvents) in listOf(
                        reference.rises() to actual.rises(),
                        reference.sets() to actual.sets(),
                    )) {
                        Assert.assertEquals(label, expectedEvents.size, actualEvents.size)
                        for (i in expectedEvents.indices) {
                            Assert.assertTrue(
                                label,
                                Duration.between(expectedEvents[i], actualEvents[i]).abs() <= Duration.ofSeconds(5)
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testSolarEventsOutsideGrenaRangeUseReferenceModel() {
        val location = Location("test").apply {
            latitude = -37.8
            longitude = 145.0
        }
        for (dateString in listOf("2000-06-21", "2010-01-01", "2110-12-31", "2200-06-21")) {
            val date = LocalDate.parse(dateString)
            val zone = ZoneId.of("Australia/Melbourne")
            val calendar = GregorianCalendar.from(date.atStartOfDay(zone))
            val reference = SolarEvents.spa().forDate(
                date, zone, location.latitude, location.longitude, DeltaT.estimate(date)
            )
            Assert.assertEquals(reference, WeatherSpec.solarDay(calendar, location))
        }
    }

    @Test
    fun testParcelUnmarshallV4() {
        val weatherSpec = WeatherSpec()
        weatherSpec.timestamp = 1753693938
        weatherSpec.location = "Green Hill"
        weatherSpec.currentTemp = 15 + 273
        weatherSpec.currentConditionCode = 601 // snow
        weatherSpec.currentCondition = "Snow"
        weatherSpec.currentHumidity = 30
        weatherSpec.todayMaxTemp = 25 + 273
        weatherSpec.todayMinTemp = 10 + 273
        weatherSpec.windSpeed = 10f
        weatherSpec.windDirection = 12

        weatherSpec.forecasts = ArrayList()

        for (i in 0..4) {
            val gbForecast = WeatherSpec.Daily()
            gbForecast.minTemp = 10 + i + 273
            gbForecast.maxTemp = 25 + i + 273
            gbForecast.conditionCode = 800 // clear
            gbForecast.precipProbability = 0
            weatherSpec.forecasts.add(gbForecast)
        }

        val rawBytes =
            ("aced000577080000007200000004737200116a6176612e6c616e672e496e746567657212e2a0a4" +
                    "f781873802000149000576616c7565787200106a6176612e6c616e672e4e756d62657286ac951d" +
                    "0b94e08b0200007870000000047704000000047371007e000068873ef277040000001c74000a47" +
                    "7265656e2048696c6c7704000000047371007e0000000001207704000000047371007e00000000" +
                    "0259770400000010740004536e6f777704000000047371007e00000000001e7704000000047371" +
                    "007e00000000012a7704000000047371007e00000000011b7704000000047372000f6a6176612e" +
                    "6c616e672e466c6f6174daedc9a2db3cf0ec02000146000576616c75657871007e000141200000" +
                    "7704000000047371007e00000000000c7704000000047371007e00000000000577040000000471" +
                    "007e000277040000008074003c6e6f646f6d61696e2e66726565796f75726761646765742e6761" +
                    "646765746272696467652e6d6f64656c2e5765617468657253706563244461696c797704000000" +
                    "047371007e0000000000017704000000047371007e00000000011b7704000000047371007e0000" +
                    "0000012a7704000000047371007e0000000003207704000000047371007e000000000000770400" +
                    "0000047371007e000b0000000077040000000471007e00147704000000047371007e000b000000" +
                    "0077040000000471007e001477040000000471007e001477040000000471007e00147704000000" +
                    "0471007e001477040000000471007e001477040000000471007e00147704000000047077040000" +
                    "000471007e000277040000008071007e000f77040000000471007e00107704000000047371007e" +
                    "00000000011c7704000000047371007e00000000012b7704000000047371007e00000000032077" +
                    "040000000471007e00147704000000047371007e000b0000000077040000000471007e00147704" +
                    "000000047371007e000b0000000077040000000471007e001477040000000471007e0014770400" +
                    "00000471007e001477040000000471007e001477040000000471007e001477040000000471007e" +
                    "00147704000000047077040000000471007e000277040000008071007e000f7704000000047100" +
                    "7e00107704000000047371007e00000000011d7704000000047371007e00000000012c77040000" +
                    "00047371007e00000000032077040000000471007e00147704000000047371007e000b00000000" +
                    "77040000000471007e00147704000000047371007e000b0000000077040000000471007e001477" +
                    "040000000471007e001477040000000471007e001477040000000471007e001477040000000471" +
                    "007e001477040000000471007e00147704000000047077040000000471007e0002770400000080" +
                    "71007e000f77040000000471007e00107704000000047371007e00000000011e77040000000473" +
                    "71007e00000000012d7704000000047371007e00000000032077040000000471007e0014770400" +
                    "0000047371007e000b0000000077040000000471007e00147704000000047371007e000b000000" +
                    "0077040000000471007e001477040000000471007e001477040000000471007e00147704000000" +
                    "0471007e001477040000000471007e001477040000000471007e00147704000000047077040000" +
                    "000471007e000277040000008071007e000f77040000000471007e00107704000000047371007e" +
                    "00000000011f7704000000047371007e00000000012e7704000000047371007e00000000032077" +
                    "040000000471007e00147704000000047371007e000b0000000077040000000471007e00147704" +
                    "000000047371007e000b0000000077040000000471007e001477040000000471007e0014770400" +
                    "00000471007e001477040000000471007e001477040000000471007e001477040000000471007e" +
                    "0014770400000004707704000000047371007e000b0000000077040000000471007e0014770400" +
                    "00000471007e00147704000000047371007e000b0000000077040000000471007e001477040000" +
                    "00047371007e000b0000000077040000000471007e001477040000000471007e00147704000000" +
                    "0471007e001477040000000471007e001477040000000471007e00147704000000047371007e00" +
                    "0b000000007704000000047371007e000b0000000077040000000471007e001477040000000473" +
                    "71007e0000ffffffff7704000000047077040000000471007e0014").hexToByteArray()

        val parcel = Parcel.obtain()
        parcel.unmarshall(
            rawBytes,
            0,
            rawBytes.size
        )
        parcel.setDataPosition(0)

        val specFromParcel = WeatherSpec.CREATOR.createFromParcel(parcel)
        parcel.recycle()

        Assert.assertEquals(weatherSpec, specFromParcel)
    }
}
