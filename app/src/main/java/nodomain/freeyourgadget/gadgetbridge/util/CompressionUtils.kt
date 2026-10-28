/*  Copyright (C) 2025 José Rebelo, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.util

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.GZIPInputStream
import java.util.zip.Inflater

object CompressionUtils {
    private val LOG = LoggerFactory.getLogger(GarminSupport::class.java)

    fun inflate(bytes: ByteArray): ByteArray? {
        val inflater = Inflater()
        inflater.setInput(bytes)
        val baosInflated = ByteArrayOutputStream(bytes.size)
        val buf = ByteArray(8096)
        while (!inflater.finished()) {
            try {
                val count = inflater.inflate(buf)
                baosInflated.write(buf, 0, count)
            } catch (e: DataFormatException) {
                LOG.error("Failed to inflate", e)
                return null
            }
        }
        return baosInflated.toByteArray()
    }

    /**
     * Compress to a zlib stream of stored (not compressed) blocks, with [windowSize].
     *
     * [windowSize] must be a power of 2, and between 256 and 32768 (inclusive).
     */
    fun deflateStored(bytes: ByteArray, windowSize: Int): ByteArray {
        require(windowSize in 256..32768 && windowSize.countOneBits() == 1) {
            "Invalid window size $windowSize"
        }
        val windowBits = windowSize.countTrailingZeroBits()

        val deflater = Deflater(Deflater.NO_COMPRESSION)
        deflater.setInput(bytes)
        deflater.finish()
        val baosDeflated = ByteArrayOutputStream(bytes.size + 64)
        val buf = ByteArray(8096)
        while (!deflater.finished()) {
            val count = deflater.deflate(buf)
            baosDeflated.write(buf, 0, count)
        }
        deflater.end()
        val deflated = baosDeflated.toByteArray()

        // Stored blocks have no back references, so the stream is valid for any window size.
        // Java's deflater only allows the default 32KB window, so we must set it directly.
        val cmf = (windowBits - 8) shl 4 or 8
        val flg = (31 - (cmf shl 8) % 31) % 31
        deflated[0] = cmf.toByte()
        deflated[1] = flg.toByte()
        return deflated
    }

    fun gunzipUtf8String(compressed: ByteArray?): String {
        val byteInput = ByteArrayInputStream(compressed)
        return GZIPInputStream(byteInput).bufferedReader(Charsets.UTF_8).use { it.readText() }
    }
}
