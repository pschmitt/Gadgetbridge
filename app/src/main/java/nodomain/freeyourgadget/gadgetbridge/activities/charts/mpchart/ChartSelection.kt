package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.text.format.DateFormat
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.fixedLabelValues
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Tooltip content for a selected x. [description] is for accessibility.
 */
data class ChartSelection(val title: String, val rows: List<Row>, val description: String) {
    /**
     * A row without a [color] is drawn without a swatch, e.g. for a total.
     */
    data class Row(val color: Int?, val text: String)
}

internal object ChartSlots {
    /**
     * Slots of a period axis, or null for other axes.
     */
    fun of(xAxis: AxisSpec): IntRange? {
        if (fixedLabelValues(xAxis) == null) return null
        return xAxis.minimum!!.roundToInt()..xAxis.maximum!!.roundToInt()
    }

    /**
     * Sorted x values a selection snaps to: the slots, or every point's x.
     */
    fun targets(spec: ChartSpec): DoubleArray {
        of(spec.xAxis)?.let { slots -> return DoubleArray(slots.count()) { (slots.first + it).toDouble() } }
        return spec.series.filter { it.selectable }.flatMap { series -> series.points.map { it.x } }.distinct().sorted().toDoubleArray()
    }

    fun nearest(x: Double, targets: DoubleArray): Double? {
        if (targets.isEmpty()) return null
        val index = targets.binarySearch(x)
        if (index >= 0) return targets[index]
        val after = -index - 1
        val before = after - 1
        return when {
            after >= targets.size -> targets[before]
            before < 0 -> targets[after]
            abs(targets[after] - x) < abs(x - targets[before]) -> targets[after]
            else -> targets[before]
        }
    }

    fun toggle(selected: Double?, tapped: Double): Double? = if (selected == tapped) null else tapped

    /**
     * Left edge of the tooltip: right of the guide in the left half, left of it in the right half.
     */
    fun tooltipLeft(guideX: Float, centerX: Float, width: Float, gap: Float, minLeft: Float, maxRight: Float): Float {
        val left = if (guideX < centerX) guideX + gap else guideX - gap - width
        return left.coerceIn(minLeft, maxOf(minLeft, maxRight - width))
    }
}

object DaySelections {
    /**
     * Tooltip for epoch day [x]: its date, then a row per label with a value. [texts] format the value at a day's
     * index, or give [emptyText] for none.
     */
    @JvmStatic
    fun of(
        epochDays: LongArray,
        x: Double,
        labels: List<String>,
        colors: List<Int?>,
        texts: List<(Int) -> String>,
        emptyText: String,
    ): ChartSelection {
        val locale = Locale.getDefault()
        val day = Math.round(x)
        val title = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)
            .format(LocalDate.ofEpochDay(day))
        val index = epochDays.indexOf(day)
        val shown = labels.indices
            .map { Triple(labels[it], colors[it], if (index >= 0) texts[it](index) else emptyText) }
            .filter { it.third != emptyText }
        return ChartSelection(
            title = title,
            rows = shown.map { ChartSelection.Row(it.second, it.third) },
            description = (listOf(title) + shown.map { "${it.first} ${it.third}".trim() }).joinToString(". ", postfix = "."),
        )
    }
}
