package nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class TrainingStatusChartDataTest {
    private fun runs(vararg samples: Pair<Long, Int>, from: Long, to: Long) = TrainingStatusChartData.runs(
        samples.map { it.first }.toLongArray(), samples.map { it.second }.toIntArray(), from, to,
    )

    @Test
    fun sameStatusDays_mergeIntoOneRun() {
        assertEquals(
            listOf(StatusRun(5, 100, 102), StatusRun(1, 103, 103)),
            runs(100L to 5, 101L to 5, 102L to 5, 103L to 1, from = 100, to = 105),
        )
    }

    @Test
    fun lastSampleOfADay_wins() {
        assertEquals(listOf(StatusRun(5, 100, 100)), runs(100L to 1, 100L to 5, from = 100, to = 101))
    }

    @Test
    fun daysWithoutASample_splitRuns() {
        assertEquals(listOf(StatusRun(4, 100, 100), StatusRun(4, 102, 102)), runs(100L to 4, 102L to 4, from = 100, to = 110))
    }

    @Test
    fun samplesOutsideTheRange_areIgnored() {
        assertEquals(listOf(StatusRun(8, 100, 100)), runs(97L to 8, 100L to 8, 103L to 8, from = 100, to = 102))
    }

    @Test
    fun runStart_followsConsecutiveDaysOfTheSameStatus() {
        val days = longArrayOf(94, 96, 97, 98, 99, 99)
        val statuses = intArrayOf(5, 4, 5, 5, 1, 5)

        assertEquals(97L, TrainingStatusChartData.runStart(days, statuses, 5, 100))
        assertEquals(100L, TrainingStatusChartData.runStart(days, statuses, 4, 100))
    }

    @Test
    fun dailyStatuses_takeTheLastSampleOfEachDay_andLeaveEmptyDaysNull() {
        val statuses = TrainingStatusChartData.dailyStatuses(
            longArrayOf(90, 95, 97, 97, 101), intArrayOf(4, 5, 7, 8, 1), lastDay = 100, count = 7,
        )

        assertEquals(listOf(null, 5, null, 8, null, null, null), statuses.toList())
    }

    @Test
    fun pausedAndUnknownValues_formRunsLikeAnyOther() {
        assertEquals(
            listOf(StatusRun(-1, 100, 100), StatusRun(3, 101, 101)),
            runs(100L to -1, 101L to 3, from = 100, to = 101),
        )
    }

    @Test
    fun visibleRuns_areNewestFirstAndKeepTheirRealStart() {
        val all = listOf(StatusRun(5, 80, 90), StatusRun(4, 91, 95), StatusRun(7, 96, 130))

        assertEquals(
            listOf(StatusRun(7, 96, 130), StatusRun(4, 91, 95), StatusRun(5, 80, 90)),
            TrainingStatusChartData.visibleRuns(all, 85, 120),
        )
        assertEquals(listOf(StatusRun(7, 96, 130)), TrainingStatusChartData.visibleRuns(all, 96, 120))
    }

    @Test
    fun spec_hasOneSpanSeriesPerStatus_clippedToTheWindow() {
        val all = listOf(StatusRun(5, 80, 90), StatusRun(4, 91, 95), StatusRun(5, 96, 130))

        val spec = TrainingStatusChartData.spec(all, 85, 120, { "s$it" }, { it * 10 })

        assertEquals(listOf("s5", "s4"), spec.series.map { it.label })
        assertEquals(SeriesStyle.Span(50), spec.series[0].style)
        assertEquals(listOf(85.0 to 90.0, 96.0 to 120.0), spec.series[0].points.map { it.x to it.xEnd })
        assertEquals(85.0, spec.xAxis.minimum)
        assertEquals(120.0, spec.xAxis.maximum)
    }

    @Test
    fun shares_countDaysInTheWindowOnly_largestFirst() {
        val all = listOf(StatusRun(5, 80, 90), StatusRun(4, 91, 93), StatusRun(5, 94, 95), StatusRun(7, 96, 130))

        assertEquals(
            listOf(StatusShare(7, 5, 50), StatusShare(4, 3, 30), StatusShare(5, 2, 20)),
            TrainingStatusChartData.shares(all, 91, 100),
        )
        assertEquals(emptyList<StatusShare>(), TrainingStatusChartData.shares(all, 200, 210))
    }

    @Test
    fun spec_withoutRunsInTheWindow_isEmpty() {
        assertSame(ChartSpec.EMPTY, TrainingStatusChartData.spec(listOf(StatusRun(5, 10, 20)), 30, 40, { "" }, { 0 }))
    }

    @Test
    fun rangeText_coversOngoingSingleDayAndClosedRuns() {
        val text = { run: StatusRun ->
            TrainingStatusChartData.rangeText(run, 50, { "d$it" }, "today", { a, b -> "$a-$b" })
        }

        assertEquals("d45-today", text(StatusRun(1, 45, 50)))
        assertEquals("d40", text(StatusRun(1, 40, 40)))
        assertEquals("d40-d44", text(StatusRun(1, 40, 44)))
    }

    @Test
    fun selection_showsTheRunOfTheDayOrEmpty() {
        val all = listOf(StatusRun(5, 10, 12))

        val selection = TrainingStatusChartData.selection(all, 11.0, "t", { "s$it" }, { it }, { "r" }, "none")
        assertEquals(listOf("s5", "r"), selection.rows.map { it.text })
        assertEquals(5, selection.rows[0].color)

        val empty = TrainingStatusChartData.selection(all, 14.0, "t", { "s$it" }, { it }, { "r" }, "none")
        assertEquals(listOf("none"), empty.rows.map { it.text })
    }
}
