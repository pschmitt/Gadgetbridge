package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class PaiWidgetTest : TestBase() {
    private val garmin = GBDevice("00:11:22:33:44:01", "Fenix", null, null, DeviceType.GARMIN_FENIX_7)
    private val amazfit = GBDevice("00:11:22:33:44:02", "Bip 3", null, null, DeviceType.AMAZFITBIP3)

    private fun data(hasTime: Boolean, hasLow: Boolean) = PaiWidget.Data(
        total = 120, today = 20, target = 150,
        low = 1, moderate = 5, high = 14,
        minutesLow = 10, minutesModerate = 25, minutesHigh = 40,
        hasTime = hasTime, hasLow = hasLow,
    )

    @Test
    fun nameFollowsTheDevice() {
        assertEquals(R.string.garmin_intensity_minutes, PaiWidget.nameOf(listOf(garmin)))
        assertEquals(R.string.menuitem_pai, PaiWidget.nameOf(listOf(amazfit)))
    }

    @Test
    fun nameFallsBackToPaiForMixedDevices() {
        assertEquals(R.string.menuitem_pai, PaiWidget.nameOf(listOf(garmin, amazfit)))
        assertEquals(R.string.menuitem_pai, PaiWidget.nameOf(emptyList()))
    }

    @Test
    fun legendShowsMinutesWhenTracked() {
        val rows = PaiWidget.intensityLegend(context, data(hasTime = true, hasLow = false))

        assertEquals(listOf(context.getString(R.string.stress_moderate), context.getString(R.string.stress_high)), rows.map { it.name })
        assertEquals(listOf(context.getString(R.string.num_min, 25), context.getString(R.string.num_min, 40)), rows.map { it.value })
    }

    @Test
    fun legendShowsEarnedPaiWithoutMinutes() {
        val rows = PaiWidget.intensityLegend(context, data(hasTime = false, hasLow = true))

        assertEquals(3, rows.size)
        assertEquals(listOf("+1", "+5", "+14"), rows.map { it.value })
    }

    @Test
    fun barsStackTheDayIncreaseOnTopOfTheEarlierTotal() {
        val bars = PaiWidget.dayBars(floatArrayOf(80f, Float.NaN, 150f), floatArrayOf(30f, Float.NaN, 70f), 150, EARLIER, INCREASE, GOAL)

        assertEquals(listOf(50f, 80f), listOf(bars.bottom[0], bars.bottom[2]))
        assertEquals(listOf(30f, 70f), listOf(bars.top[0], bars.top[2]))
        assertEquals(EARLIER, bars.bottomColor)
        assertEquals(INCREASE, bars.topColor)
        assertEquals(150f, bars.reference!!, 0f)
        assertEquals(165f, bars.max, 1e-3f)
    }

    @Test
    fun scaleCoversTheTallestBar() {
        val bars = PaiWidget.dayBars(floatArrayOf(200f), floatArrayOf(20f), 150, EARLIER, INCREASE, GOAL)

        assertEquals(220f, bars.max, 1e-3f)
    }

    @Test
    fun daysAfterTheSelectedOneAreStillToCome() {
        val wednesday = 3
        val none = FloatArray(7) { Float.NaN }
        val bars = PaiWidget.dayBars(none, none, 150, EARLIER, INCREASE, GOAL, wednesday)

        assertEquals(3, bars.dataUntil)
    }

    private companion object {
        const val EARLIER = 1
        const val INCREASE = 2
        const val GOAL = 3
    }
}
