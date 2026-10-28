/*  Copyright (C) 2026 Manuel Marcatili

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
package nodomain.freeyourgadget.gadgetbridge.util.sleep

/** The per-minute sleep/wake labels the estimator works on, and runs over them. */
internal object SleepLabels {
    const val SLEEP = 'S'
    const val WAKE = 'W'
    const val GAP = 'G'

    class LabelRun(val label: Char, val start: Int, val length: Int)

    fun runsOf(labels: CharArray, from: Int = 0, to: Int = labels.size): List<LabelRun> {
        val runs = mutableListOf<LabelRun>()
        var start = from
        for (index in from + 1..to) {
            if (index == to || labels[index] != labels[start]) {
                runs.add(LabelRun(labels[start], start, index - start))
                start = index
            }
        }
        return runs
    }
}

/**
 * Webster's rescoring rules (Webster 1982, as used with Cole-Kripke): the
 * first sleep minutes after a wake run are still wake, and a short sleep
 * run between long wake runs is wake. Passes repeat until stable: a wake
 * run that grew carries over again.
 */
internal object WebsterRescoring {

    /** The estimator's constants were fitted with three passes, so it is not a single pass. */
    const val DEFAULT_PASSES = 3

    fun apply(labels: CharArray, passes: Int = DEFAULT_PASSES) {
        repeat(passes) {
            val carried = carryOver(labels)
            val bridged = bridge(labels)
            if (!carried && !bridged) return
        }
    }

    /** Rule 1: the first sleep minutes after a wake run are still wake. Returns whether anything changed. */
    fun carryOver(labels: CharArray): Boolean {
        var changed = false
        val runs = SleepLabels.runsOf(labels)
        for ((position, run) in runs.withIndex()) {
            if (run.label != SleepLabels.WAKE) continue
            val next = runs.getOrNull(position + 1) ?: continue
            if (next.label != SleepLabels.SLEEP) continue
            val toWake = when {
                run.length >= 15 -> 4
                run.length >= 10 -> 3
                run.length >= 4 -> 1
                else -> 0
            }
            for (index in next.start until next.start + minOf(toWake, next.length)) {
                labels[index] = SleepLabels.WAKE
                changed = true
            }
        }
        return changed
    }

    /** Rules 2 and 3: a short sleep run between long wake runs is wake. Returns whether anything changed. */
    fun bridge(labels: CharArray): Boolean {
        var changed = false
        val runs = SleepLabels.runsOf(labels)
        for ((position, run) in runs.withIndex()) {
            if (run.label != SleepLabels.SLEEP) continue
            val before = runs.getOrNull(position - 1) ?: continue
            val after = runs.getOrNull(position + 1) ?: continue
            if (before.label != SleepLabels.WAKE || after.label != SleepLabels.WAKE) continue
            val bounded = minOf(before.length, after.length)
            if ((run.length <= 6 && bounded >= 10) || (run.length <= 10 && bounded >= 20)) {
                for (index in run.start until run.start + run.length) labels[index] = SleepLabels.WAKE
                changed = true
            }
        }
        return changed
    }
}
