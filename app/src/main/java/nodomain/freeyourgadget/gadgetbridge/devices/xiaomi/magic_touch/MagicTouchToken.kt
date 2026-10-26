/*  Copyright (C) 2026 Francesco Colasuonno

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch

import nodomain.freeyourgadget.gadgetbridge.util.GB

/** Mi Home cloud token for `xiaomi.magic_touch.jj02`: 24 hex chars = 12
 * bytes, optionally prefixed with `0x`. This is NOT the 16-byte BLE bind
 * key. Shared by the coordinator (discovery validation) and the support
 * (session key derivation) so the format rule only lives here. */
fun parseCloudToken(raw: String): ByteArray? {
    val trimmed = raw.trim()
    val hex = if (trimmed.startsWith("0x") || trimmed.startsWith("0X")) {
        trimmed.substring(2)
    } else {
        trimmed
    }
    if (hex.length != 24 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
        return null
    }
    val bytes = runCatching { GB.hexStringToByteArray(hex) }.getOrNull()
    return bytes?.takeIf { it.size == 12 }
}
