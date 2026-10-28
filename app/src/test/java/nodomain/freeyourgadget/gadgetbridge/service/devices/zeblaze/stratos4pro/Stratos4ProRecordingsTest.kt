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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.GregorianCalendar
import java.util.zip.CRC32

/** Frames from a real `f9` exchange. */
class Stratos4ProRecordingsTest {
    @Test
    fun listRequest() {
        assertArrayEquals(
            h("01f9aa020c443a2f5265636f7264696e67"),
            fileRequest(MODE_GET, FILE_LIST, Stratos4ProRecordings.RECORDING_DIR)
        )
    }

    @Test
    fun listOfRecordings() {
        val list = FileListCollector()
        assertNull(list.add(h("01f9aa021b000018010000575c32303236313030395f3034353635352e6d7033")))
        assertEquals(listOf("20261009_045655.mp3"), list.add(h("01f9aa0206fdfd78e2716e")))
    }

    @Test
    fun listSkipsFolders() {
        val list = FileListCollector()
        list.add(
            h(
                "01f9aa025e00001201005203b04432323235353030312e617669120100443ab44432323235363030312e617669" +
                    "120100553c324432323235373030312e6176690800000000004d41500e00000000005265636f7264696e67" +
                    "0a00000000006d75736963"
            )
        )
        assertEquals(
            listOf("D22255001.avi", "D22256001.avi", "D22257001.avi"),
            list.add(h("01f9aa0206fdfd8fb1d3f9"))
        )
    }

    @Test
    fun listWithBadCrcIsEmpty() {
        val list = FileListCollector()
        list.add(h("01f9aa021b000018010000575c32303236313030395f3034353635352e6d7033"))
        assertEquals(emptyList<String>(), list.add(h("01f9aa0206fdfd78e2716f")))
    }

    @Test
    fun readStream() {
        val data = ByteArray(500) { it.toByte() }
        val out = ByteArrayOutputStream()
        val read = FileReadStream(data.size.toLong(), crc(data), out)
        assertFalse(read.add(dataFrame(0, data.copyOfRange(0, 235))))
        assertFalse(read.add(dataFrame(1, data.copyOfRange(235, 470))))
        assertFalse(read.add(dataFrame(2, data.copyOfRange(470, 500))))
        assertTrue(read.add(h("01f9ac0204fffffffd")))
        assertTrue(read.complete())
        assertArrayEquals(data, out.toByteArray())
    }

    @Test
    fun readStreamWithGapFails() {
        val data = ByteArray(470) { it.toByte() }
        val read = FileReadStream(data.size.toLong(), crc(data), ByteArrayOutputStream())
        read.add(dataFrame(0, data.copyOfRange(0, 235)))
        read.add(dataFrame(2, data.copyOfRange(235, 470)))
        assertTrue(read.add(h("01f9ac0204fffffffd")))
        assertFalse(read.complete())
    }

    @Test
    fun readStreamShortFails() {
        val data = ByteArray(470) { it.toByte() }
        val read = FileReadStream(data.size.toLong(), crc(data), ByteArrayOutputStream())
        read.add(dataFrame(0, data.copyOfRange(0, 235)))
        assertTrue(read.add(h("01f9ac0204fffffffd")))
        assertFalse(read.complete())
    }

    @Test
    fun recordingNames() {
        assertEquals(GregorianCalendar(2026, 9, 9, 4, 56, 55).timeInMillis, recordingTime("20261009_045655.mp3"))
        assertNull(recordingTime("D22255001.avi"))
        assertNull(recordingTime("20261309_045655.mp3"))
    }

    private fun dataFrame(idx: Int, data: ByteArray) =
        byteArrayOf(PKT_HEADER, CMD_FILE, MODE_REPORT, PUSH_FILE_DATA, (data.size + 4).toByte()) + be32Bytes(idx) + data

    private fun crc(data: ByteArray) = CRC32().apply { update(data) }.value

    private fun h(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
