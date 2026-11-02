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

import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * Frame encryption for the Ninebot BLE-UART protocol (header `5A A5`).
 *
 * Port of the algorithm from https://github.com/scooterhacking/NinebotCrypto (via the Python port in
 * https://github.com/dnandha/miauth). A frame consists of a 3 byte header (`5A A5 <len>`) followed by a
 * body (`src dst cmd index data...`). Only the body is encrypted, the header stays in clear.
 *
 * The session passes through three key stages:
 * 1. `SHA1(name || FW_DATA)`, used for the INIT request
 * 2. `SHA1(name || bleData)`, used after INIT for the PING request (bleData comes from the INIT response)
 * 3. `SHA1(appData || bleData)`, used once paired (appData is the app key sent with PING)
 *
 * As long as [counter] is 0 (or [bleData] is not known yet), frames use a static key stream and a plain
 * checksum. Afterwards, the key stream is AES-CTR like with a 4 byte MAC and a 16 bit message counter.
 */
class NinebotCrypto {
    private var name = ByteArray(0)
    private var sha1Key = ByteArray(16)
    private var bleData: ByteArray? = null

    /** Message counter. Increased for every encrypted frame, taken from the frame for decrypted ones. */
    var counter = 0
        private set

    /** Result of [decrypt]: the clear frame (header + body) and whether its checksum / MAC is valid. */
    class Decrypted(val frame: ByteArray, val authentic: Boolean)

    fun setName(name: ByteArray) {
        this.name = name
        sha1Key = deriveKey(name, FW_DATA)
    }

    fun setBleData(bleData: ByteArray) {
        this.bleData = bleData.copyOf(16)
        sha1Key = deriveKey(name, this.bleData!!)
    }

    fun setAppData(appData: ByteArray) {
        sha1Key = deriveKey(appData, bleData!!)
    }

    /** Resets the counter, e.g. for a new connection. */
    fun reset() {
        counter = 0
        bleData = null
        sha1Key = deriveKey(name, FW_DATA)
    }

    /** Encrypts a frame given as `5A A5 <len> <body>`. */
    fun encrypt(frame: ByteArray): ByteArray {
        val header = frame.copyOfRange(0, 3)
        val body = frame.copyOfRange(3, frame.size)
        val ble = bleData

        if (counter == 0 || ble == null) {
            val enc = xorStaticKeyStream(body)
            val checksum = checksum(body)
            return header + enc + byteArrayOf(0, 0, checksum[0], checksum[1], 0, 0)
        }

        counter++
        val nonce = nonce(ble, counter)
        val enc = xorCtr(body, nonce)
        val mac = mac(frame, nonce)
        return header + enc + mac + byteArrayOf((counter shr 8).toByte(), counter.toByte())
    }

    /**
     * Decrypts a complete frame as received. The message counter is taken from the last two bytes of the frame, if the frame is authentic.
     * Returns null if the frame is too short.
     */
    fun decrypt(frame: ByteArray): Decrypted? {
        if (frame.size < 9) return null
        val header = frame.copyOfRange(0, 3)
        val enc = frame.copyOfRange(3, frame.size - 6)
        val frameCounter = ((frame[frame.size - 2].toInt() and 0xff) shl 8) or (frame[frame.size - 1].toInt() and 0xff)
        val ble = bleData

        if (frameCounter == 0 || ble == null) {
            val body = xorStaticKeyStream(enc)
            val expected = checksum(body)
            val authentic = frame[frame.size - 4] == expected[0] && frame[frame.size - 3] == expected[1]
            if (authentic) counter = frameCounter
            return Decrypted(header + body, authentic)
        }

        val nonce = nonce(ble, frameCounter)
        val body = xorCtr(enc, nonce)
        val clear = header + body
        val expected = mac(clear, nonce)
        val authentic = (0 until 4).all { frame[frame.size - 6 + it] == expected[it] }
        // A frame that fails the check must not change the counter used for the next requests
        if (authentic) counter = frameCounter
        return Decrypted(clear, authentic)
    }

    /** 16 byte block `01 <counter:4> <bleData[0..8]> 00 00 00 00`, the last byte is the block index. */
    private fun nonce(ble: ByteArray, counter: Int): ByteArray {
        val aes = ByteArray(16)
        aes[0] = 1
        aes[1] = (counter ushr 24).toByte()
        aes[2] = (counter ushr 16).toByte()
        aes[3] = (counter ushr 8).toByte()
        aes[4] = counter.toByte()
        System.arraycopy(ble, 0, aes, 5, 8)
        return aes
    }

    private fun aesEcb(block: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(sha1Key, "AES"))
        return cipher.doFinal(block)
    }

    /** Static key stream `AES(FW_DATA)`, used while the counter is 0. */
    private fun xorStaticKeyStream(data: ByteArray): ByteArray {
        val stream = aesEcb(FW_DATA)
        return ByteArray(data.size) { (data[it].toInt() xor stream[it % 16].toInt()).toByte() }
    }

    private fun xorCtr(data: ByteArray, nonce: ByteArray): ByteArray {
        val out = ByteArray(data.size)
        val block = nonce.copyOf()
        var pos = 0
        var index = 0
        while (pos < data.size) {
            index++
            block[15] = index.toByte()
            val stream = aesEcb(block)
            val n = minOf(16, data.size - pos)
            for (i in 0 until n) {
                out[pos + i] = (data[pos + i].toInt() xor stream[i].toInt()).toByte()
            }
            pos += n
        }
        return out
    }

    /** CBC-MAC over the clear frame (header and body), first 4 bytes, encrypted with the block 0 key stream. */
    private fun mac(clear: ByteArray, nonce: ByteArray): ByteArray {
        val bodyLen = clear.size - 3
        val b0 = nonce.copyOf()
        b0[0] = 0x59
        b0[15] = bodyLen.toByte()

        val aad = ByteArray(16)
        System.arraycopy(clear, 0, aad, 0, 3)
        var x = aesEcb(b0)
        x = aesEcb(xor16(aad, x))

        var pos = 3
        while (pos < clear.size) {
            val chunk = ByteArray(16)
            System.arraycopy(clear, pos, chunk, 0, minOf(16, clear.size - pos))
            x = aesEcb(xor16(chunk, x))
            pos += 16
        }

        val a0 = nonce.copyOf()
        a0[15] = 0
        val tagStream = aesEcb(a0)
        return ByteArray(4) { (tagStream[it].toInt() xor x[it].toInt()).toByte() }
    }

    private fun xor16(a: ByteArray, b: ByteArray) = ByteArray(16) { (a[it].toInt() xor b[it].toInt()).toByte() }

    /** Inverted sum of the body bytes, little endian, as used by the plain frames. */
    private fun checksum(body: ByteArray): ByteArray {
        val sum = body.sumOf { it.toInt() and 0xff }.inv()
        return byteArrayOf(sum.toByte(), (sum shr 8).toByte())
    }

    companion object {
        val FW_DATA = byteArrayOf(
            0x97.toByte(), 0xCF.toByte(), 0xB8.toByte(), 0x02, 0x84.toByte(), 0x41, 0x43, 0xDE.toByte(),
            0x56, 0x00, 0x2B, 0x3B, 0x34, 0x78, 0x0A, 0x5D,
        )

        /** `SHA1(a[0..16] || b[0..16])[0..16]`, zero-padded to 16 bytes each. */
        fun deriveKey(a: ByteArray, b: ByteArray): ByteArray {
            val data = ByteArray(32)
            System.arraycopy(a, 0, data, 0, minOf(16, a.size))
            System.arraycopy(b, 0, data, 16, minOf(16, b.size))
            return MessageDigest.getInstance("SHA-1").digest(data).copyOf(16)
        }
    }
}
