package nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus

import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartValueFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

/**
 * Consecutive epoch days, [startDay] to [endDay], with the same training status.
 */
data class StatusRun(val status: Int, val startDay: Long, val endDay: Long)

/**
 * The [days] with a [status] within a period, and their [percent] of all days with a status.
 */
data class StatusShare(val status: Int, val days: Long, val percent: Int)

object TrainingStatusChartData {
    private const val SPAN_Y = 0.5

    /**
     * The runs of days with a sample from [from] to [to], where the last of the time-ordered samples of a day wins.
     */
    @JvmStatic
    fun runs(days: LongArray, statuses: IntArray, from: Long, to: Long): List<StatusRun> {
        val runs = mutableListOf<StatusRun>()
        for ((day, status) in lastPerDay(days, statuses)) {
            if (day !in from..to) continue
            val last = runs.lastOrNull()
            if (last != null && last.status == status && last.endDay == day - 1) {
                runs[runs.lastIndex] = last.copy(endDay = day)
            } else {
                runs += StatusRun(status, day, day)
            }
        }
        return runs
    }

    /**
     * The first day of the run of [status] that ends the day before [start], or [start] if there is none.
     */
    @JvmStatic
    fun runStart(days: LongArray, statuses: IntArray, status: Int, start: Long): Long {
        val daily = lastPerDay(days, statuses)
        var day = start
        while (daily[day - 1] == status) day--
        return day
    }

    /**
     * The status of each of the [count] days up to [lastDay], oldest first, or null for a day without a sample.
     */
    @JvmStatic
    fun dailyStatuses(days: LongArray, statuses: IntArray, lastDay: Long, count: Int): Array<Int?> {
        val result = arrayOfNulls<Int>(count)
        for (i in days.indices) {
            val index = (days[i] - (lastDay - count + 1)).toInt()
            if (index in 0 until count) result[index] = statuses[i]
        }
        return result
    }

    private fun lastPerDay(days: LongArray, statuses: IntArray): Map<Long, Int> {
        val daily = linkedMapOf<Long, Int>()
        for (i in days.indices) daily[days[i]] = statuses[i]
        return daily
    }

    /**
     * The runs that overlap [firstDay] to [lastDay], newest first.
     */
    @JvmStatic
    fun visibleRuns(runs: List<StatusRun>, firstDay: Long, lastDay: Long): List<StatusRun> =
        runs.filter { it.endDay >= firstDay && it.startDay <= lastDay }.reversed()

    /**
     * The share of each status of the days from [firstDay] to [lastDay] that have one, largest first.
     */
    @JvmStatic
    fun shares(runs: List<StatusRun>, firstDay: Long, lastDay: Long): List<StatusShare> {
        val days = linkedMapOf<Int, Long>()
        for (run in runs) {
            val count = minOf(run.endDay, lastDay) - maxOf(run.startDay, firstDay) + 1
            if (count > 0) days[run.status] = (days[run.status] ?: 0L) + count
        }
        val total = days.values.sum()
        return days.map { (status, count) -> StatusShare(status, count, Math.round(count * 100.0 / total).toInt()) }
            .sortedByDescending { it.days }
    }

    @JvmStatic
    fun spec(
        runs: List<StatusRun>,
        firstDay: Long,
        lastDay: Long,
        label: (Int) -> String,
        color: (Int) -> Int,
    ): ChartSpec {
        val clipped = runs
            .filter { it.endDay >= firstDay && it.startDay <= lastDay }
            .map { it.copy(startDay = maxOf(it.startDay, firstDay), endDay = minOf(it.endDay, lastDay)) }
        if (clipped.isEmpty()) {
            return ChartSpec.EMPTY
        }
        val series = clipped.groupBy { it.status }.map { (status, statusRuns) ->
            ChartSeries(
                key = "status_$status",
                label = label(status),
                points = statusRuns.map { ChartPoint(it.startDay.toDouble(), SPAN_Y, xEnd = it.endDay.toDouble()) },
                style = SeriesStyle.Span(color(status)),
            )
        }
        return ChartSpec(
            series = series,
            xAxis = AxisSpec(format = ChartValueFormat.EPOCH_DAY, minimum = firstDay.toDouble(), maximum = lastDay.toDouble()),
            yAxis = AxisSpec(minimum = 0.0, maximum = 1.0, showLabels = false, showGrid = false),
        )
    }

    /**
     * "start – end", a single date for a one-day run, or "start – [todayText]" for the run that lasts until [today].
     */
    @JvmStatic
    fun rangeText(
        run: StatusRun,
        today: Long,
        date: (Long) -> String,
        todayText: String,
        range: (String, String) -> String,
    ): String = when {
        run.endDay >= today -> range(date(run.startDay), todayText)
        run.startDay == run.endDay -> date(run.startDay)
        else -> range(date(run.startDay), date(run.endDay))
    }

    /**
     * Tooltip for epoch day [x]: the status of its run and the run's [rangeText], or [emptyText].
     */
    @JvmStatic
    fun selection(
        runs: List<StatusRun>,
        x: Double,
        title: String,
        label: (Int) -> String,
        color: (Int) -> Int,
        range: (StatusRun) -> String,
        emptyText: String,
    ): ChartSelection {
        val day = Math.round(x)
        val run = runs.firstOrNull { day in it.startDay..it.endDay }
        val rows = if (run == null) {
            listOf(ChartSelection.Row(null, emptyText))
        } else {
            listOf(ChartSelection.Row(color(run.status), label(run.status)), ChartSelection.Row(null, range(run)))
        }
        return ChartSelection(
            title = title,
            rows = rows,
            description = (listOf(title) + rows.map { it.text }).joinToString(". ", postfix = "."),
        )
    }
}
