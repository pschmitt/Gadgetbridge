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

/**
 * Battery history from the `d1 aa 04` event log: `<total:4>`, then 10-byte records
 * `fd <unix:4> <module> <n> <key> <value:2>`. Level: module 16 key 01, else module 5 key 02.
 * Voltage: module 5 key 04, mV.
 */
internal object Stratos4ProBatteryLog {
    class Result(val records: Int, val levels: Map<Long, Int>, val millivolts: Map<Long, Int>)

    private const val MARK = 0xfd
    private const val RECORD = 10
    private const val HEADER = 4
    private const val MIN_TS = 1_577_836_800L
    private const val MOD_BATTERY = 5
    private const val MOD_BATTERY_LEVEL = 16

    fun parse(payload: ByteArray, nowSec: Long = System.currentTimeMillis() / 1000L): Result {
        val levels = sortedMapOf<Long, Int>()
        val millivolts = sortedMapOf<Long, Int>()
        var records = 0
        var i = HEADER
        while (i + RECORD <= payload.size) {
            // Resync on the next marker.
            if (u8(payload, i) != MARK) {
                i++
                continue
            }
            val ts = be32(payload, i + 1)
            val module = u8(payload, i + 5)
            val key = u8(payload, i + 7)
            val value = u16(payload, i + 8)
            i += RECORD
            if (ts < MIN_TS || ts > nowSec + 86_400L) continue
            records++
            when {
                module == MOD_BATTERY_LEVEL && key == 0x01 && value <= 100 -> levels[ts] = value
                module == MOD_BATTERY && key == 0x02 && value <= 100 -> levels.putIfAbsent(ts, value)
                module == MOD_BATTERY && key == 0x04 && value in 3000..4500 -> millivolts[ts] = value
            }
        }
        return Result(records, levels, millivolts)
    }
}
