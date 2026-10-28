package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SleepWidgetTest : TestBase() {
    // light, deep, REM, awake
    private val stages = longArrayOf(240, 90, 60, 15)

    @Test
    fun legendHasAllStagesWhenSupported() {
        val names = SleepWidget.stageLegend(context, stages, SleepWidget.StageSupport.ALL).map { it.name }

        assertEquals(
            listOf(
                context.getString(R.string.sleep_colored_stats_deep),
                context.getString(R.string.sleep_colored_stats_light),
                context.getString(R.string.sleep_colored_stats_rem),
                context.getString(R.string.abstract_chart_fragment_kind_awake_sleep),
            ),
            names,
        )
    }

    @Test
    fun legendSkipsRemAndAwakeWhenUnsupported() {
        val names = SleepWidget.stageLegend(context, stages, SleepWidget.StageSupport(rem = false, awake = false)).map { it.name }

        assertEquals(
            listOf(context.getString(R.string.sleep_colored_stats_deep), context.getString(R.string.sleep_colored_stats_light)),
            names,
        )
    }

    @Test
    fun compositionSkipsUnsupportedStages() {
        val composition = SleepWidget.stageComposition(stages, SleepWidget.StageSupport(rem = true, awake = false))

        assertArrayEquals(floatArrayOf(90f, 240f, 60f), composition.values, 0f)
        assertEquals(3, composition.colors.size)
    }
}
