/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.concept2.protocol

import org.junit.Assert.assertTrue
import org.junit.Assume
import org.junit.Test
import kotlin.math.abs

/** Decodes whole recordings and checks the blocks against each other. */
class PM5CaptureDecodeAuditTest {
    @Test
    fun committedRecording() {
        audit(listOf(PM5Recording.row100mCapture()))
    }

    /** Skipped when there is no local capture; see [PM5Recording.localCaptures]. */
    @Test
    fun localCaptures() {
        val captures = PM5Recording.localCaptures()
        Assume.assumeTrue("No local PM5 captures", captures.isNotEmpty())
        audit(captures)
    }

    private fun audit(captures: List<PM5Capture>) {
        val problems = captures.flatMap { capture -> problemsOf(capture).map { "${capture.name}: $it" } }
        assertTrue(problems.joinToString("\n", "Capture audit found:\n"), problems.isEmpty())
    }

    private fun problemsOf(capture: PM5Capture): List<String> {
        val problems = ArrayList<String>()
        val unknown = capture.messages.filterIsInstance<PM5Message.Unknown>()
            .filter { it.id !in NOT_DECODED }
            .groupingBy { "0x%02x/%d".format(it.id, it.payload.size) }
            .eachCount()
        if (unknown.isNotEmpty()) {
            problems.add("blocks the decoder does not know: $unknown")
        }
        if (capture.machineTypes.size > 1) {
            problems.add("several erg machine types: ${capture.machineTypes}")
        }

        // the monitor derives both from the same flywheel speed, over the erg's pace distance
        val ergKind = capture.ergKind
        val paceTimesSpeed = capture.messages.filterIsInstance<PM5Message.AdditionalStatus1>()
            .filter { it.speedMetersPerSecond > 0.5 && it.currentPaceSeconds > 0 }
            .map { it.currentPaceSeconds * it.speedMetersPerSecond }
            .sorted()
        if (paceTimesSpeed.isNotEmpty()) {
            val median = paceTimesSpeed[paceTimesSpeed.size / 2]
            if (abs(median - ergKind.paceMeters) > ergKind.paceMeters * 0.02) {
                problems.add("pace x speed is %.1f m, expected %d m for %s".format(median, ergKind.paceMeters, ergKind))
            }
        }
        return problems
    }

    companion object {
        /**
         * Blocks recognised but left undecoded: additional status 3 (on the BikeErg its multiplexed
         * layout does not match the interface definition), the logged workout block, the split
         * watt-minutes, and the force curve, which is put together from several notifications.
         */
        private val NOT_DECODED = setOf(0x3E, 0x3F, 0x42, PM5MessageDecoder.ID_FORCE_CURVE)
    }
}
