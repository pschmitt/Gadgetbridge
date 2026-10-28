/*  Copyright (C) 2026 Manuel Marcatili

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
package nodomain.freeyourgadget.gadgetbridge.util.elevation

import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/** One imported SRTM tile. The file name is the key and the size is the resolution. */
data class ElevationTile(
    val key: HgtTileKey,
    val resolution: HgtResolution,
    val sizeBytes: Long,
    val importedAtMillis: Long,
    val path: String,
) {
    val file: File get() = File(path)

    /** `N45E007`: the cell, without the extension. */
    val displayName: String get() = key.fileName.removeSuffix(HgtTileKey.FILE_EXTENSION)
}

/**
 * The SRTM tiles stored in one directory, plus a small cache of open
 * mappings. Pure JVM, so it can be tested without Android.
 *
 * There is no metadata file. The name is the key, the size is the
 * resolution and the modification time is the import time, so a directory
 * scan is the whole truth and a half-written `.tmp` is simply not listed.
 */
class HgtTileLibrary @JvmOverloads constructor(
    private val directory: File,
    private val maxOpenTiles: Int = DEFAULT_MAX_OPEN_TILES,
) {
    private val lock = Any()
    private val open = object : LinkedHashMap<HgtTileKey, HgtTile>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HgtTileKey, HgtTile>): Boolean =
            size > maxOpenTiles
    }

    /** The last tile hit, so a track of thousands of points does one lookup per tile crossing. */
    @Volatile
    private var last: HgtTile? = null

    /** Every valid tile in the directory, newest import first. */
    fun scan(): List<ElevationTile> =
        directory.listFiles()
            .orEmpty()
            .mapNotNull { file -> file.toElevationTile() }
            .sortedByDescending { it.importedAtMillis }

    fun fileFor(key: HgtTileKey): File = File(directory, key.fileName)

    /** Bilinear elevation in meters, or null when no imported tile covers the position. */
    fun elevationAt(latitude: Double, longitude: Double): Double? {
        val key = HgtTileKey.forPosition(latitude, longitude)
        val tile = last?.takeIf { it.key == key } ?: tileFor(key) ?: return null
        last = tile
        return tile.elevationAt(latitude, longitude)
    }

    /** The open tile for a cell, or null when its file is absent or malformed. */
    fun tileFor(key: HgtTileKey): HgtTile? = synchronized(lock) {
        open[key]?.let { return it }
        val file = fileFor(key)
        if (!file.isFile) return null
        val tile = runCatching { HgtTile.open(key, file) }.getOrNull() ?: return null
        open[key] = tile
        tile
    }

    /** Drops a cached mapping so a replaced or deleted file is not read through the old buffer. */
    fun evict(key: HgtTileKey) {
        synchronized(lock) {
            open.remove(key)
            if (last?.key == key) last = null
        }
    }

    /**
     * Copies the tiles of one file into the directory: a raw `.hgt`, or a zip
     * holding any number of them, like the regional archives of
     * viewfinderpanoramas. A raw tile's key comes from [displayName], a zipped
     * one's from its entry. Replaces existing tiles of the same keys. A file
     * with one bad tile imports none of them.
     */
    @Throws(IOException::class)
    fun importTiles(displayName: String, input: InputStream): List<ElevationTile> {
        directory.mkdirs()
        val stream = BufferedInputStream(input, PEEK_BYTES)
        val nameKey = HgtTileKey.parse(displayName)
        val isZip = displayName.endsWith(".zip", ignoreCase = true) || stream.looksLikeZip()

        // A zip is sequential, so every tile is staged before any replaces a stored one.
        val staged = LinkedHashMap<HgtTileKey, File>()
        try {
            if (isZip) {
                val zip = ZipInputStream(stream)
                while (true) {
                    val entry = zip.nextHgtEntryName() ?: break
                    val entryKey = HgtTileKey.parse(File(entry).name)
                    require(entryKey != null) { "The zip entry $entry is not named like an SRTM tile (N45E007.hgt)." }
                    require(nameKey == null || nameKey == entryKey) {
                        "The zip is named $displayName but holds $entry."
                    }
                    require(entryKey !in staged) { "The zip holds ${entryKey.fileName} twice." }
                    staged[entryKey] = stage(entryKey, zip)
                }
                require(staged.isNotEmpty()) { "The zip holds no .hgt tile." }
            } else {
                require(nameKey != null) { "$displayName is not named like an SRTM tile (N45E007.hgt)." }
                staged[nameKey] = stage(nameKey, stream)
            }

            for ((key, tempFile) in staged) {
                val finalFile = fileFor(key)
                evict(key)
                if (!tempFile.renameTo(finalFile)) {
                    tempFile.copyTo(finalFile, overwrite = true)
                    tempFile.delete()
                }
            }
        } catch (error: Throwable) {
            staged.values.forEach { it.delete() }
            throw error
        }
        return staged.keys.map { key ->
            fileFor(key).toElevationTile()
                ?: throw IOException("${key.fileName} could not be read back after import.")
        }
    }

    /** Copies one tile to a `.tmp` beside its final file, which a scan does not list. */
    private fun stage(key: HgtTileKey, source: InputStream): File {
        val tempFile = File(directory, "${key.fileName}.tmp")
        try {
            tempFile.delete()
            val copied = copyBounded(source, tempFile)
            require(HgtResolution.forByteSize(copied) != null) {
                "This is not a 1 or 3 arc-second SRTM .hgt tile."
            }
        } catch (error: Throwable) {
            tempFile.delete()
            throw error
        }
        return tempFile
    }

    fun deleteTile(key: HgtTileKey): Boolean {
        evict(key)
        return fileFor(key).delete()
    }

    private fun File.toElevationTile(): ElevationTile? {
        if (!isFile) return null
        val key = HgtTileKey.parse(name) ?: return null
        if (name != key.fileName) return null
        val resolution = HgtResolution.forByteSize(length()) ?: return null
        return ElevationTile(
            key = key,
            resolution = resolution,
            sizeBytes = length(),
            importedAtMillis = lastModified(),
            path = absolutePath,
        )
    }

    /** Positions the stream on the next `.hgt` entry, skipping directories and other files. Null at the end of the archive. */
    private fun ZipInputStream.nextHgtEntryName(): String? {
        while (true) {
            val entry = nextEntry ?: return null
            if (entry.isDirectory) continue
            if (entry.name.endsWith(HgtTileKey.FILE_EXTENSION, ignoreCase = true)) return entry.name
        }
    }

    private fun copyBounded(input: InputStream, destination: File): Long {
        val limit = HgtResolution.ONE_ARC_SECOND.byteSize
        var total = 0L
        destination.outputStream().use { output ->
            val buffer = ByteArray(COPY_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "This is not a 1 or 3 arc-second SRTM .hgt tile." }
                output.write(buffer, 0, read)
            }
        }
        return total
    }

    private fun BufferedInputStream.looksLikeZip(): Boolean {
        mark(PEEK_BYTES)
        val first = read()
        val second = read()
        reset()
        return first == 'P'.code && second == 'K'.code
    }

    private companion object {
        const val DEFAULT_MAX_OPEN_TILES = 4
        const val COPY_BUFFER_SIZE = 128 * 1024
        const val PEEK_BYTES = 16
    }
}
