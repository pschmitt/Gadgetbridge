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

import java.io.File
import java.io.Reader

/** A notification recorded from a real PM5, as stored in the test resources. */
data class PM5RecordedNotification(val millis: Long, val shortId: Int, val value: ByteArray)

/** A whole recording, with the name the monitor advertised when it is known. */
class PM5Capture(val name: String, val deviceName: String?, val records: List<PM5RawRecord>) {
    val messages: List<PM5Message> by lazy { records.mapNotNull { it.decode() } }

    /** Every erg machine type the monitor reported. */
    val machineTypes: Set<Int> by lazy {
        (messages.filterIsInstance<PM5Message.AdditionalStatus1>().mapNotNull { it.ergMachineType } +
            messages.filterIsInstance<PM5Message.AdditionalSplitData>().mapNotNull { it.ergMachineType } +
            messages.filterIsInstance<PM5Message.AdditionalSummary2>().map { it.ergMachineType }).toSet()
    }

    val namedErgKind: PM5ErgKind? get() = PM5ErgKind.fromDeviceName(deviceName)

    val ergKind: PM5ErgKind get() = PM5ErgKind.of(machineTypes.firstOrNull(), namedErgKind)
}

object PM5Recording {
    /** Names the directory [localCaptures] reads instead of `../rawDetails/concept2`. */
    const val RAW_DIR_ENV = "GB_CONCEPT2_RAW_DIR"

    /** The 100 m row in `concept2/pm5_row_100m.tsv`, recorded on the individual characteristics. */
    fun row100m(): List<PM5RecordedNotification> = load("/concept2/pm5_row_100m.tsv")

    fun row100mCapture(): PM5Capture = PM5Capture("pm5_row_100m", "PM5 430070439", row100m().map(::rawRecord))

    /**
     * Every `.tsv` capture in the directory named by [RAW_DIR_ENV], or `../rawDetails/concept2`
     * relative to the module; empty when there is none. Captures whose licence does not allow
     * committing them stay there. A `# Device PM5 ....` header line gives the monitor's name.
     */
    fun localCaptures(): List<PM5Capture> {
        val dir = System.getenv(RAW_DIR_ENV)?.let(::File) ?: File("../rawDetails/concept2")
        val files = dir.listFiles { f -> f.name.endsWith(".tsv") }?.sortedBy { it.name }.orEmpty()
        return files.map { file ->
            val deviceName = file.useLines { lines ->
                lines.takeWhile { it.startsWith("#") }
                    .firstNotNullOfOrNull { Regex("Device (PM5 [^.]*)\\.").find(it)?.groupValues?.get(1) }
            }
            PM5Capture(file.nameWithoutExtension, deviceName, file.reader().use(::parse).map(::rawRecord))
        }
    }

    fun load(resource: String): List<PM5RecordedNotification> {
        val stream = PM5Recording::class.java.getResourceAsStream(resource)
            ?: throw IllegalArgumentException("Missing test resource $resource")
        return parse(stream.bufferedReader())
    }

    /** Short id 0x80 marks a block of the multiplexed characteristic, id byte first. */
    fun parse(reader: Reader): List<PM5RecordedNotification> {
        return reader.buffered().useLines { lines ->
            lines.filter { it.isNotBlank() && !it.startsWith("#") }
                .map { line ->
                    val (millis, id, hex) = line.split('\t')
                    PM5RecordedNotification(millis.toLong(), id.removePrefix("0x").toInt(16), PM5MessageDecoderTest.hex(hex))
                }
                .toList()
        }
    }

    private fun rawRecord(notification: PM5RecordedNotification) =
        PM5RawRecord(notification.millis, notification.shortId, notification.value)
}
