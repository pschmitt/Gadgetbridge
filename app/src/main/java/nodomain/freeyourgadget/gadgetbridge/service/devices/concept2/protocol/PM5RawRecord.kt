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

/**
 * One notification exactly as the monitor sent it. [source] is [SOURCE_MULTIPLEXED] for the
 * multiplexed characteristic, whose [value] then starts with the block id, or the short id of the
 * individual characteristic (0x31 for 0x0031).
 */
class PM5RawRecord(val timeMillis: Long, val source: Int, val value: ByteArray) {
    fun decode(): PM5Message? = if (source == SOURCE_MULTIPLEXED) {
        PM5MessageDecoder.decode(value)
    } else {
        PM5MessageDecoder.decodeCharacteristic(source, value)
    }

    companion object {
        const val SOURCE_MULTIPLEXED = 0x80
    }
}
