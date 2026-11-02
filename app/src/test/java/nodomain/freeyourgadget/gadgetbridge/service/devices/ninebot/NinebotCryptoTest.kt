/*  Copyright (C) 2026 fds2610

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.ninebot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The expected values were generated with the Python reference implementation (miauth.nb.nbcrypto) using a
 * made-up device name and made-up keys.
 */
class NinebotCryptoTest {
    private val name = "NBTEST0000AB01".toByteArray()
    private val bleData = ByteArray(16) { (0x10 + it).toByte() }
    private val appData = ByteArray(16) { (0xA0 + it).toByte() }

    private lateinit var crypto: NinebotCrypto

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Before
    fun setUp() {
        crypto = NinebotCrypto()
        crypto.setName(name)
    }

    @Test
    fun init_request_uses_static_key_stream() {
        assertArrayEquals(hex("5aa50080dbbd7d000045ff0000"), crypto.encrypt(hex("5aa5003e215b00")))
        assertEquals(0, crypto.counter)
    }

    @Test
    fun ping_request_uses_ble_data_key() {
        crypto.setBleData(bleData)
        val plain = hex("5aa5203e215c00a0a1a2a3a4a5a6a7a8a9aaabacadaeaf00000000000000000000000000000000")
        val enc = "5aa520b255f8c9d68d3a8cfb1d4c9e39494f3d20d90a66762c982f5fb8ea3991e0e5968c74a4c90000ccf40000"
        assertArrayEquals(hex(enc), crypto.encrypt(plain))
        assertEquals(0, crypto.counter)
    }

    @Test
    fun init_response_can_be_decrypted() {
        val encrypted = crypto.encrypt(hex("5aa5003e215b00"))
        val decrypted = crypto.decrypt(encrypted)
        assertNotNull(decrypted)
        assertTrue(decrypted!!.authentic)
        assertArrayEquals(hex("5aa5003e215b00"), decrypted.frame)
    }

    @Test
    fun paired_session_decrypts_and_counts() {
        crypto.setBleData(bleData)
        crypto.setAppData(appData)

        // Frame from the scooter with counter 2, which the counter of the next requests continues from
        val rx0 = crypto.decrypt(hex("5aa5027b8bbda7b5bb08d3f03b0002"))!!
        assertTrue(rx0.authentic)
        assertArrayEquals(hex("5aa502203e041d0008"), rx0.frame)
        assertEquals(2, crypto.counter)

        val requests = listOf(
            "5aa5013e20011d02" to "5aa501ee52893b666fa1972a0003",
            "5aa5023e20027b0200" to "5aa50208e5d060b8795fb0c0bd0004",
            "5aa50e3e215d004e42544553543030303041423031" to
                "5aa50e5221b38c2e42ba40ebc22a1bc58c63b81fa9ae6c2f920005",
            "5aa5123e2003400102030405060708090a0b0c0d0e0f101112" to
                "5aa512ebc075a1f183b653a174f0d917e39e7048b0510837efa35b576b0006",
        )
        for ((plain, encrypted) in requests) {
            assertArrayEquals(hex(encrypted), crypto.encrypt(hex(plain)))
        }
        assertEquals(6, crypto.counter)

        val rx1 = crypto.decrypt(hex("5aa50e39c054b57b7b45066a2c1cfd7e376a1861e15a906c210007"))!!
        assertTrue(rx1.authentic)
        assertArrayEquals(hex("5aa50e203e04104e42544553543030303041423031"), rx1.frame)
        assertEquals(7, crypto.counter)
    }

    @Test
    fun tampered_frame_is_not_authentic() {
        crypto.setBleData(bleData)
        crypto.setAppData(appData)
        val frame = hex("5aa5027b8bbda7b5bb08d3f03b0002")
        frame[4] = (frame[4].toInt() xor 1).toByte()
        assertFalse(crypto.decrypt(frame)!!.authentic)
    }

    @Test
    fun tampered_frame_keeps_the_counter() {
        crypto.setBleData(bleData)
        crypto.setAppData(appData)
        val frame = hex("5aa5027b8bbda7b5bb08d3f03b0002")
        frame[4] = (frame[4].toInt() xor 1).toByte()
        crypto.decrypt(frame)
        assertEquals(0, crypto.counter)
    }

    @Test
    fun too_short_frame_is_rejected() {
        assertEquals(null, crypto.decrypt(hex("5aa50000")))
    }
}
