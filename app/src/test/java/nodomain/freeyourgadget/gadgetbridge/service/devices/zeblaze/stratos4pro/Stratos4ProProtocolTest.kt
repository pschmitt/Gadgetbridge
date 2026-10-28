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

import nodomain.freeyourgadget.gadgetbridge.model.Alarm
import nodomain.freeyourgadget.gadgetbridge.model.NotificationSpec
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale

/** Hex string (spaces allowed) -> bytes. */
internal fun h(s: String): ByteArray = s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

class Stratos4ProProtocolTest {
    private fun hexList(frames: List<ByteArray>) = frames.map { it.toHex() }

    @Test
    fun requests() {
        assertEquals("01c7aaaa", cmdGetAll(CMD_ALARM).toHex())
        assertEquals("01eaaa01", cmdGetField(CMD_DND, FIELD_DND).toHex())
        assertEquals("01ebab020101", cmdSetField(CMD_BT3, SET_BT3_SWITCH, 0x01).toHex())
        assertEquals("01a5ab0c0100", cmdSetField(CMD_DEVICE_CONFIG, SET_PASSWORD, 0x00).toHex())
        assertEquals("01b2ab050107", deleteNotificationFrame(7).toHex())
        assertEquals(
            "01c6aa0408010203040a0b0c0d",
            rangeCmd(CMD_HEALTH_STREAM, MODE_GET, FIELD_SLEEP_FILE, 0x01020304, 0x0a0b0c0d).toHex()
        )
    }

    @Test
    fun chunkedTlvSingleFrame() {
        assertEquals(
            listOf("01e0abab00ab0102aabb", "01e0ababfd58"),
            hexList(chunkedTlv(CMD_WEATHER, h("ab0102aabb")))
        )
    }

    @Test
    fun chunkedTlvSplitsAndXorsAllFrames() {
        val body = ByteArray(500) { it.toByte() }
        val frames = chunkedTlv(CMD_NOTIFICATION, body)
        assertEquals(4, frames.size)
        assertEquals(listOf(5 + 239, 5 + 239, 5 + 22, 6), frames.map { it.size })
        for (i in 0..2) {
            assertEquals("01b0abab%02x".format(i), frames[i].copyOfRange(0, 5).toHex())
        }
        val sent = frames.dropLast(1).flatMap { it.asList() }
        assertArrayEquals(body, frames.dropLast(1).flatMap { it.drop(5) }.toByteArray())
        var x = 0
        for (b in sent) x = x xor (b.toInt() and 0xff)
        assertEquals("01b0ababfd%02x".format(x), frames.last().toHex())
    }

    @Test
    fun chunkedEmptyBody() {
        assertEquals(listOf("01e0ababfd00"), hexList(chunkedTlv(CMD_WEATHER, ByteArray(0))))
        assertEquals(
            listOf("01edab0100", "01edab01fd46"),
            hexList(chunkedFrames(CMD_WORLD_CLOCK, MODE_SET, SET_WORLD_CLOCKS, ByteArray(0), emptyFrame = true))
        )
    }

    @Test
    fun chunkedWithOffsetIndex() {
        val frames = chunkedFrames(
            CMD_CONTACTS, MODE_SET, SET_CONTACTS_LIST, ByteArray(300), maxSlice = 230, emptyFrame = true
        ) { _, off, len -> byteArrayOf(hi(off), lo(off), len.toByte()) }
        assertEquals(3, frames.size)
        assertEquals("01c0ab070000e6", frames[0].copyOfRange(0, 7).toHex())
        assertEquals("01c0ab0700e646", frames[1].copyOfRange(0, 7).toHex())
        assertEquals(7 + 70, frames[1].size)
    }

    @Test
    fun tlvInt() {
        assertEquals(5, tlvInt(h("01e1aa0b0105")))
        assertEquals(258, tlvInt(h("01e1aa0b020102")))
        assertNull(tlvInt(h("01e1aa0b00")))
        assertNull(tlvInt(h("01e1aa0b0201")))
    }

    @Test
    fun parseTlvFields() {
        val f = parseTlv(h("01a4aaaa00 aa1503312e30 aa020141"), 5)
        assertEquals("1.0", String(f[FIELD_FIRMWARE]!!))
        assertEquals("41", f[FIELD_MODEL]!!.toHex())
    }

    @Test
    fun subTlv() {
        val list = subTlvList(h("010105 0200 030241"))
        assertEquals(listOf(1 to "05", 2 to ""), list.map { it.first to it.second.toHex() })
        assertEquals(setOf(1), parseSubTlv(h("010105 0200")).keys)
    }

    @Test
    fun ints() {
        val b = h("fffe01020304")
        assertEquals(0xfffe, u16(b, 0))
        assertEquals(0x01020304L, be32(b, 2))
        assertEquals(0xfffe, h("fffe").beInt())
        assertEquals("fffffffb", be32Bytes(-5).toHex())
        assertEquals(0xff.toByte(), hi(-5))
        assertEquals(0xfb.toByte(), lo(-5))
    }

    @Test
    fun chunkAssembler() {
        val a = Stratos4ProChunkAssembler()
        assertNull(a.collect(h("01edaa0100 0004aabb"), 5))
        assertNull(a.collect(h("01edaa0101 ccdd"), 5))
        assertEquals("0004aabbccdd", a.collect(h("01edaa01fd"), 5)!!.toHex())

        // A bare close: empty only when asked for.
        assertNull(a.collect(h("01e1aa02fd00"), 7))
        assertEquals(0, a.collect(h("01e1aa02fd00"), 7, bareEndIsEmpty = true)!!.size)
    }

    @Test
    fun chunkAssemblerKeepsModesApart() {
        val a = Stratos4ProChunkAssembler()
        assertNull(a.collect(h("01c6aa0100 1111"), 5))
        assertNull(a.collect(h("01c6ac0100 2222"), 5))
        assertEquals("2222", a.collect(h("01c6ac01fd"), 5)!!.toHex())
        assertEquals("1111", a.collect(h("01c6aa01fd"), 5)!!.toHex())
    }

    @Test
    fun chunkAssemblerFiles() {
        val a = Stratos4ProChunkAssembler()
        assertNull(a.collectFile(h("01e8aa090000 1122")))
        assertNull(a.collectFile(h("01e8aa090001 3344")))
        assertEquals("11223344", a.collectFile(h("01e8aa09fdfd"))!!.toHex())
        // No such file: a bare close is an empty file.
        assertEquals(0, a.collectFile(h("01e8aa09fdfd"))!!.size)

        assertNull(a.collectFile(h("01e8aa090000 1122")))
        a.cancelFile(CMD_WORKOUT, MODE_GET, FIELD_WORKOUT_GPS)
        assertNull(a.collectFile(h("01e8aa090001 3344")))
        assertEquals(0, a.collectFile(h("01e8aa09fdfd"))!!.size)
    }

    @Test
    fun hhMmIsAsciiInAnyLocale() {
        val saved = Locale.getDefault()
        try {
            // Myanmar formats %d with its own digits.
            Locale.setDefault(Locale.forLanguageTag("my-MM"))
            assertEquals("07:05", hhMm(7, 5))
            assertEquals("23:59", hhMm(23, 59))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun alarmSoundModes() {
        assertEquals(0, alarmSoundMode(Alarm.ALARM_SOUND.UNSET.ordinal).toInt())
        assertEquals(0, alarmSoundMode(Alarm.ALARM_SOUND.TONE_AND_VIBRATION.ordinal).toInt())
        assertEquals(1, alarmSoundMode(Alarm.ALARM_SOUND.TONE.ordinal).toInt())
        assertEquals(2, alarmSoundMode(Alarm.ALARM_SOUND.VIBRATION.ordinal).toInt())
        assertEquals(2, alarmSoundMode(Alarm.ALARM_SOUND.OFF.ordinal).toInt())
        assertEquals(0, alarmSoundMode(99).toInt())
    }

    @Test
    fun morningItemsWrite() {
        assertEquals(
            listOf("01caab020012060101010101020100030100040100050100", "01caab02fd77"),
            hexList(morningItemsFrames(listOf("event", "battery", "bogus")))
        )
    }

    @Test
    fun morningItemsReply() {
        assertEquals(
            listOf("battery", "sleep", "steps", "yesterdays_activity", "event"),
            parseMorningItems(h("01caaa020012010101020101030101040101050100060101"))
        )
        assertNull(parseMorningItems(h("01caaa02fd77")))
        assertNull(parseMorningItems(h("01caab020012010101")))
        assertNull(parseMorningItems(h("01caaa010101")))
    }

    @Test
    fun quickReplies() {
        assertEquals("000a 0400480069 04004f006b".replace(" ", ""), quickReplyBody(listOf("Hi", "Okay"), 2).toHex())
        assertEquals("0000", quickReplyBody(emptyList(), 40).toHex())
        assertEquals(42L to "OK", parseNotificationReply(h("000e 0000002a 004f004b000000000000")))
        assertEquals(0xfffffffeL to "", parseNotificationReply(h("0004 fffffffe")))
        assertNull(parseNotificationReply(h("000e 0000002a 004f")))
        // GB ids start near the epoch second, so (id shl 4) + n overflows 32 bits.
        val reply = (1_790_895_735L shl 4) + 2
        val spec = NotificationSpec(attachedActions = arrayListOf(
            NotificationSpec.Action(NotificationSpec.Action.TYPE_WEARABLE_SIMPLE, reply - 1),
            NotificationSpec.Action(NotificationSpec.Action.TYPE_WEARABLE_REPLY, reply),
        ))
        assertEquals(reply.toInt(), replyHandle(spec))
        assertEquals(0, replyHandle(NotificationSpec()))
        // Echoed back by the watch, then cut to int by GB's lookup: same key.
        assertEquals(reply.toInt(), parseNotificationReply(h("0004") + be32Bytes(replyHandle(spec)))!!.first.toInt())
        // Custom reply (Slack, Discord): same handle path.
        val custom = (1_790_895_735L shl 4) + 3
        val specCustom = NotificationSpec(attachedActions = arrayListOf(
            NotificationSpec.Action(NotificationSpec.Action.TYPE_CUSTOM_REPLY, custom),
        ))
        assertEquals(custom.toInt(), replyHandle(specCustom))
        assertNull(smsReplyNumber(specCustom))
        // GB's own SMS reply action has no handle: the GB id stands in, GB sends the SMS.
        val sms = NotificationSpec.Action(NotificationSpec.Action.TYPE_SYNTHETIC_REPLY_PHONENR, 0)
        val specSms = NotificationSpec(phoneNumber = "+389 70 123-456", attachedActions = arrayListOf(sms))
        assertEquals("+389 70 123-456", smsReplyNumber(specSms))
        assertEquals(specSms.id, replyHandle(specSms))
        assertEquals(specSms.id, parseNotificationReply(h("0004") + be32Bytes(replyHandle(specSms)))!!.first.toInt())
        // No number, or a sender that is not a number: no reply.
        for (number in listOf(null, "DEBUG PhoneNumber")) {
            val spec = NotificationSpec(phoneNumber = number, attachedActions = arrayListOf(sms))
            assertNull(smsReplyNumber(spec))
            assertEquals(0, replyHandle(spec))
        }
    }

    /** Live `b2 ac 06` (built-in reply 8): text is 3 byte code points, here U+1F44D. */
    @Test
    fun builtInReply() {
        val p = h("000e 6ac81987 01f44d00000000000000")
        assertEquals(0x6ac81987L to "\uD83D\uDC4D", parseNotificationReply(p, builtIn = true))
    }

    @Test
    fun brightnessSchedule() {
        val max = LocalTime.of(9, 0)
        val min = LocalTime.of(20, 0)
        val d = LocalDate.of(2026, 10, 1)
        assertEquals(d.minusDays(1).atTime(min) to false, Stratos4ProSettings.lastBrightnessSwitch(d.atTime(8, 59), max, min))
        assertEquals(d.atTime(max) to true, Stratos4ProSettings.lastBrightnessSwitch(d.atTime(9, 0), max, min))
        assertEquals(d.atTime(min), Stratos4ProSettings.nextBrightnessSwitch(d.atTime(9, 0), max, min))
        assertEquals(d.plusDays(1).atTime(max), Stratos4ProSettings.nextBrightnessSwitch(d.atTime(20, 0), max, min))
    }

    /** Body of a real `ec aa 04` read. */
    @Test
    fun sportMenu() {
        val ids = listOf(0x24, 0x08, 0x02, 0x23, 0x01, 0x86, 0x04, 0x8d, 0xb3)
        assertEquals(ids, parseSportIds(h("0a09240802230186048db3")))
        assertNull(parseSportIds(h("0a")))
        assertNull(parseSportIds(h("0a092408")))
        assertEquals(
            listOf("01ecab04000a09240802230186048db3", "01ecab04fdf1"),
            hexList(sportMenuFrames(ids))
        )
    }
}
