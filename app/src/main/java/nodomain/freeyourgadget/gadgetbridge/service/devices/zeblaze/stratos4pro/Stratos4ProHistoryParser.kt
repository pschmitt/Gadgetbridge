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

/** One per-minute record (history frame item or `c6 ac 01`). */
internal data class MotionValues(
    var steps: Int = 0,
    /** kcal. */
    var calories: Int = 0,
    /** Metres. */
    var distance: Int = 0,
    var restingHr: Int = 0,
    var hrMax: Int = 0,
    var hrMin: Int = 0,
    var dynamicHr: Int = 0,
    var hrAvg: Int = 0,
    var spo2: Int = 0,
    var systolic: Int = 0,
    var diastolic: Int = 0,
    var hrv: Int = 0,
    /** Bytes consumed, including the mask. */
    var length: Int = 0,
) {
    /** Per-minute HR: dynamic, else resting; 0 without an average. */
    val heartRate: Int
        get() = if (hrAvg == 0) 0 else if (dynamicHr != 0) dynamicHr else restingHr

    /** Distance in the activity sample's unit. */
    val distanceCm: Int
        get() = distance * 100

    /** Calories in the activity sample's unit (cal, not kcal). */
    val activeCalories: Int
        get() = calories * 1000
}

/** History frame: its index, start (unix s) and the minutes it holds. */
internal data class HistoryFrame(val index: Int, val start: Long, val minutes: List<Pair<Long, MotionValues>>)

/** Sleep file record: stage 1 deep, 2 light, 3 awake, 4 REM. */
internal data class SleepRecord(val start: Long, val duration: Int, val stage: Int)

/** Parsers for the history transfers; no state, no I/O. Times are unix seconds. */
internal object Stratos4ProHistoryParser {
    /**
     * `<mask varint><values>` from [off]. Mask 1..4 bytes, bit 7 = another follows (bits 8/16/24/32
     * carry no data). Values in bit order, fixed width per bit.
     */
    fun parseMotionValues(b: ByteArray, off: Int): MotionValues? {
        if (off >= b.size) return null
        var mask = 0L
        var i = off
        var n = 0
        while (i < b.size && n < 4) {
            val v = b[i].toInt() and 0xff
            mask = mask or (v.toLong() shl (8 * n))
            i++
            n++
            if (v < 0x80) break
        }
        if (n == 4 && (b[i - 1].toInt() and 0x80) != 0) return null // >4-byte masks unsupported
        val out = MotionValues()
        fun next8(): Int? = if (i < b.size) u8(b, i++) else null
        fun next16(): Int? = if (i + 1 < b.size) u16(b, i).also { i += 2 } else null
        for (bit in 1..32) {
            if (bit % 8 == 0) continue
            if ((mask shr (bit - 1)) and 1L == 0L) continue
            when (bit) {
                1 -> out.steps = next8() ?: return null
                2 -> out.calories = next8() ?: return null
                3 -> out.distance = next16() ?: return null
                4 -> out.restingHr = next8() ?: return null
                5 -> out.hrMax = next8() ?: return null
                6 -> out.hrMin = next8() ?: return null
                7 -> out.dynamicHr = next8() ?: return null
                9 -> out.spo2 = next8() ?: return null
                10, 13 -> next8() ?: return null            // resting HR variants, unused
                12 -> out.hrAvg = next8() ?: return null
                17 -> {
                    out.systolic = next8() ?: return null
                    out.diastolic = next8() ?: return null
                }
                26 -> out.hrv = next16() ?: return null
                else -> next16() ?: return null              // other and unknown fields: 2 bytes
            }
        }
        out.length = i - off
        return out
    }

    /** History frame: `<index:2><start:4>`, then items `<offsetMin:2><mask><values>`. */
    fun parseHistoryFrame(payload: ByteArray): HistoryFrame? {
        if (payload.size < 6) return null
        val start = be32(payload, 2)
        val minutes = ArrayList<Pair<Long, MotionValues>>()
        var i = 6
        while (i + 3 <= payload.size) {
            val offset = u16(payload, i)
            val item = parseMotionValues(payload, i + 2) ?: break
            if (offset <= 1440) minutes.add((start + offset * 60L) to item)
            i += 2 + item.length
        }
        return HistoryFrame(u16(payload, 0), start, minutes)
    }

    /** Live minute `c6 ac 01`: `<len:1> <unix:4> <mask varint> <values>`, time cut to the minute. */
    fun parseLiveMinute(payload: ByteArray): Pair<Long, MotionValues>? {
        if (payload.size < 6) return null
        val ts = be32(payload, 1)
        val item = parseMotionValues(payload, 5) ?: return null
        return (ts - ts % 60) to item
    }

    /**
     * Sleep file (`c6 aa 04`): `<hdr:2>` then `<start:4><durationMin:2><type:1>`. 1 deep, 2 light,
     * 3 awake, 4 REM; 7/8 are session start/end markers.
     */
    fun parseSleepFile(payload: ByteArray): List<SleepRecord> {
        val out = ArrayList<SleepRecord>()
        if (payload.size < 9) return out
        var i = 2
        while (i + 7 <= payload.size) {
            val start = be32(payload, i)
            val duration = u16(payload, i + 4)
            val type = u8(payload, i + 6)
            i += 7
            if (type !in 1..4 || duration <= 0 || start <= 0) continue
            out.add(SleepRecord(start, duration, type))
        }
        return out
    }

    /**
     * Stress file (`e1 aa 02`): blocks `<dayStart:4><intervalMin:2><values>`, 1440 / interval bytes
     * each, 0 = none. Returns (time, stress).
     */
    fun parseStressFile(payload: ByteArray): List<Pair<Long, Int>> {
        val samples = ArrayList<Pair<Long, Int>>()
        var i = 0
        while (i + 6 <= payload.size) {
            val dayStart = be32(payload, i)
            val interval = u16(payload, i + 4)
            if (interval <= 0 || interval > 1440) break
            val count = 1440 / interval
            if (i + 6 + count > payload.size) break
            for (k in 0 until count) {
                val stress = u8(payload, i + 6 + k)
                if (stress in 1..100) samples.add((dayStart + k * interval * 60L) to stress)
            }
            i += 6 + count
        }
        return samples
    }

    /** One stress reading `c6 ac 02 05 <unix:4><value:1>` as (time, stress), or null. */
    fun parseStressReading(frame: ByteArray): Pair<Long, Int>? {
        if (frame.size < 5) return null
        val len = u8(frame, 4)
        if (len < 5 || frame.size < 5 + len) return null
        val stress = u8(frame, 9)
        if (stress !in 1..100) return null
        return be32(frame, 5) to stress
    }

    /** Live HR during a measurement `e1 ac 03 <n*5> [<hr:1><unix:4>]*` as (bpm, time). */
    fun parseLiveHeartRates(frame: ByteArray): List<Pair<Int, Long>> {
        val out = ArrayList<Pair<Int, Long>>()
        if (frame.size < 5) return out
        val end = minOf(frame.size, 5 + u8(frame, 4))
        var i = 5
        while (i + 5 <= end) {
            val hr = u8(frame, i)
            val ts = be32(frame, i + 1)
            if (hr in MIN_PLAUSIBLE_BPM..MAX_PLAUSIBLE_BPM && ts > 0) out.add(hr to ts)
            i += 5
        }
        return out
    }
}
