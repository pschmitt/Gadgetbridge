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

import java.util.UUID

/**
 * GATT layout of the Concept2 PM5, from Concept2's public "PM Bluetooth Smart Communications
 * Interface Definition" (Rev 1.30). Every service and characteristic shares the base UUID
 * CE06xxxx-43E5-11E4-916C-0800200C9A66 with a 16-bit id in place of xxxx.
 */
object PM5Uuids {
    @JvmStatic
    fun of(shortId: Int): UUID = UUID.fromString(String.format("ce06%04x-43e5-11e4-916c-0800200c9a66", shortId))

    @JvmField val SERVICE_DEVICE_INFO: UUID = of(0x0010)
    /** ASCII serial number, the one the monitor also advertises in its name. */
    @JvmField val SERIAL_NUMBER: UUID = of(0x0012)
    @JvmField val HARDWARE_REVISION: UUID = of(0x0013)
    @JvmField val FIRMWARE_REVISION: UUID = of(0x0014)

    @JvmField val SERVICE_CONTROL: UUID = of(0x0020)
    @JvmField val CONTROL_RECEIVE: UUID = of(0x0021)
    @JvmField val CONTROL_TRANSMIT: UUID = of(0x0022)

    @JvmField val SERVICE_ROWING: UUID = of(0x0030)

    /** 1 byte: 0 = 1 s, 1 = 500 ms (default), 2 = 250 ms, 3 = 100 ms between status notifications. */
    @JvmField val SAMPLE_RATE: UUID = of(0x0034)

    /** Force curve, split over several notifications. Not on PM5v1. */
    @JvmField val FORCE_CURVE: UUID = of(0x003D)

    /**
     * Carries every rowing data block behind a one-byte id (0x31 to 0x3F), as long as the
     * characteristic of the same id has notifications off.
     */
    @JvmField val MULTIPLEXED: UUID = of(0x0080)

    const val SAMPLE_RATE_1_S: Byte = 0
    const val SAMPLE_RATE_500_MS: Byte = 1
    const val SAMPLE_RATE_250_MS: Byte = 2
    const val SAMPLE_RATE_100_MS: Byte = 3
}
