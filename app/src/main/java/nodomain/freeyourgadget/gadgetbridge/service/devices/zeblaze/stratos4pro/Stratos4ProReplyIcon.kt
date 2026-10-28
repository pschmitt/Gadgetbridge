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

import org.slf4j.LoggerFactory

/**
 * Icon and name of type 7 notifications come from row 7 of the watch's type table (RAM, copied
 * from flash at boot). With [PREF_REPLY_ICON_APP] on, a reply notification first points row 7 at
 * the row of its own app, over the debug log service. The row is global: the watch's list shows
 * every type 7 entry with the last row written. All state is main thread.
 */
internal class Stratos4ProReplyIcon(
    support: Stratos4ProSupport,
    private val lights: Stratos4ProLights,
) : Stratos4ProFeature(support) {
    /** Value last written to row 7 on this link; null = unknown. */
    private var row7: Int? = null

    fun onConnect() {
        // The watch may have rebooted (row from flash) or not.
        handler.post { row7 = null }
    }

    /** Sends [send]; an [icon] 7 notification may first rewrite row 7, see the class. */
    fun sendNotification(icon: Int, appIcon: Int, send: () -> Unit) {
        val value = if (icon == 7 && switchOn()) TYPE_ROWS[typeRow(appIcon)] else null
        if (value == null || value == row7 || !writeRow7(value)) return send()
        handler.post(send) // after the shell write, which lights.shell posts first
    }

    fun onSendConfiguration(config: String): Boolean {
        if (config != PREF_REPLY_ICON_APP && config != Stratos4ProSupport.PREF_REPLY_ALL_APPS) return false
        // Off: back to the WhatsApp row; on: the next reply notification sets its own row.
        val on = switchOn()
        LOG.info("reply app icon switch: {}", on)
        if (!on && row7 != TYPE_ROWS[7]) writeRow7(TYPE_ROWS[7])
        return true
    }

    /** Counts only while the reply switch it depends on is on. */
    private fun switchOn(): Boolean =
        devicePrefs.getBoolean(Stratos4ProSupport.PREF_REPLY_ALL_APPS, false) &&
            devicePrefs.getBoolean(PREF_REPLY_ICON_APP, false)

    /** `380154a8` = row 7 name string id (low 16) + picture id (high 16); false = not sent. */
    private fun writeRow7(value: Int): Boolean {
        if (device.firmwareVersion != FIRMWARE) {
            LOG.warn("row 7 not written: firmware {}", device.firmwareVersion)
            return false
        }
        if (!lights.shell(listOf("dbg mww 380154a8 %x".format(value)))) {
            LOG.warn("row 7 not written: no log service")
            return false
        }
        LOG.info("row 7 -> {}", "%08x".format(value))
        row7 = value
        return true
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(Stratos4ProReplyIcon::class.java)

        const val PREF_REPLY_ICON_APP = "stratos4pro_reply_icon_app"

        /** The row 7 address and [TYPE_ROWS] hold for this firmware only; another build may move them. */
        private const val FIRMWARE = "ATS328SFIV000753"

        /** Watch type table by row: picture id shl 16 or name string id. Rows 24..26 are types 62, 63, 105. */
        private val TYPE_ROWS = intArrayOf(
            0x061c008c, 0x062b00e2, 0x063200cb, 0x062600c9, 0x062900e0, 0x061d00ca, 0x063400e1,
            0x063300cd, 0x062c00d0, 0x062700ce, 0x062100d1, 0x062400cf, 0x062500d2, 0x062200d3,
            0x063000d4, 0x062300d5, 0x063100d6, 0x062d00d7, 0x062000d8, 0x061f00d9, 0x061e00da,
            0x062e00db, 0x062a00dc, 0x063600dd, 0x062800de, 0x063500df, 0x097f0431,
        )

        /** Table row the firmware uses for [type]: 24..61 share the generic row 4. */
        private fun typeRow(type: Int): Int = when (type) {
            in 0..23 -> type
            62 -> 24
            63 -> 25
            105 -> 26
            else -> 4
        }
    }
}
