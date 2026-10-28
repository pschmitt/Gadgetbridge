package nodomain.freeyourgadget.gadgetbridge.util.elevation

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Import, replace, delete and lookup over a directory of tiles. */
class HgtTileLibraryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val key = HgtTileKey(45, 7)
    private lateinit var directory: File
    private lateinit var library: HgtTileLibrary

    @org.junit.Before
    fun setUp() {
        directory = temporaryFolder.newFolder("elevation_tiles")
        library = HgtTileLibrary(directory)
    }

    @Test fun aRawTileIsStoredUnderItsCanonicalName() {
        val tile = library.importTiles("n45e007.hgt", flatTile(100).stream()).single()

        assertEquals(key, tile.key)
        assertEquals(HgtResolution.THREE_ARC_SECOND, tile.resolution)
        assertEquals("N45E007", tile.displayName)
        assertTrue(File(directory, "N45E007.hgt").isFile)
        assertEquals(listOf(key), library.scan().map { it.key })
        assertEquals(100.0, library.elevationAt(45.5, 7.5)!!, 1e-6)
    }

    @Test fun aWrongSizeIsRefusedAndLeavesNothingBehind() {
        val failure = runCatching {
            library.importTiles("N45E007.hgt", ByteArray(1000).stream())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test fun anOversizedStreamIsCutOffBeforeTheDiskFills() {
        val limit = HgtResolution.ONE_ARC_SECOND.byteSize
        // Twice the largest tile: the size check after a full copy would refuse it too, but only once it is on disk
        val oversized = CountingZeros(2 * limit)

        val failure = runCatching { library.importTiles("N45E007.hgt", oversized) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue("read ${oversized.served} bytes", oversized.served < limit + 1024 * 1024)
        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test fun aBadNameIsRefusedBeforeAnythingIsCopied() {
        val failure = runCatching {
            library.importTiles("srtm_38_03.hgt", flatTile(100).stream())
        }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test fun aZipWithOneTileIsStoredAsTheRawTile() {
        val zip = zip("folder/N45E007.hgt" to flatTile(200))

        val tile = library.importTiles("N45E007.SRTMGL1.hgt.zip", zip.stream()).single()

        assertEquals(key, tile.key)
        assertEquals(HgtResolution.THREE_ARC_SECOND.byteSize, File(directory, "N45E007.hgt").length())
        assertEquals(200.0, library.elevationAt(45.5, 7.5)!!, 1e-6)
    }

    @Test fun aZipNamedByRegionTakesItsKeyFromTheEntry() {
        val zip = zip("readme.txt" to "hello".toByteArray(), "N45E007.hgt" to flatTile(5))

        val tile = library.importTiles("alps.zip", zip.stream()).single()

        assertEquals(key, tile.key)
    }

    @Test fun aRegionZipStoresEveryTileItHolds() {
        // Laid out like the regional archives of viewfinderpanoramas.org
        val zip = zip("L32/N45E007.hgt" to flatTile(1), "L32/N45E008.hgt" to flatTile(2), "L32/N46E007.hgt" to flatTile(3))

        val tiles = library.importTiles("L32.zip", zip.stream())

        assertEquals(listOf(key, HgtTileKey(45, 8), HgtTileKey(46, 7)), tiles.map { it.key })
        assertEquals(3, library.scan().size)
        assertEquals(1.0, library.elevationAt(45.5, 7.5)!!, 1e-6)
        assertEquals(2.0, library.elevationAt(45.5, 8.5)!!, 1e-6)
        assertEquals(3.0, library.elevationAt(46.5, 7.5)!!, 1e-6)
    }

    @Test fun aZipWithNoTileIsRefused() {
        val none = zip("readme.txt" to "hello".toByteArray())

        assertTrue(runCatching { library.importTiles("none.zip", none.stream()) }.isFailure)
        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test fun aBadTileLateInAZipImportsNoneAndKeepsTheStoredOnes() {
        library.importTiles("N45E007.hgt", flatTile(100).stream())
        val zip = zip("N45E007.hgt" to flatTile(200), "N45E008.hgt" to ByteArray(1000))

        val failure = runCatching { library.importTiles("L32.zip", zip.stream()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(listOf("N45E007.hgt"), directory.listFiles().orEmpty().map { it.name })
        assertEquals(100.0, library.elevationAt(45.5, 7.5)!!, 1e-6)
    }

    @Test fun aZipHoldingACellTwiceIsRefused() {
        val zip = zip("a/N45E007.hgt" to flatTile(1), "b/N45E007.hgt" to flatTile(2))

        val failure = runCatching { library.importTiles("L32.zip", zip.stream()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertEquals(emptyList<File>(), directory.listFiles().orEmpty().toList())
    }

    @Test fun aZipWhoseNameDisagreesWithItsEntryIsRefused() {
        val zip = zip("N45E008.hgt" to flatTile(1))

        val failure = runCatching { library.importTiles("N45E007.hgt.zip", zip.stream()) }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
    }

    @Test fun reImportingACellReplacesTheFileAndTheCachedMapping() {
        library.importTiles("N45E007.hgt", flatTile(100).stream())
        assertEquals(100.0, library.elevationAt(45.5, 7.5)!!, 1e-6)

        library.importTiles("N45E007.hgt", flatTile(300).stream())

        assertEquals(300.0, library.elevationAt(45.5, 7.5)!!, 1e-6)
        assertEquals(1, library.scan().size)
    }

    @Test fun scanIgnoresStrayFiles() {
        File(directory, "notes.txt").writeText("x")
        File(directory, "N45E007.hgt.tmp").writeBytes(flatTile(1))
        File(directory, "N45E008.hgt").writeBytes(ByteArray(10))
        File(directory, "N45E007.hgt").writeBytes(flatTile(1))

        assertEquals(listOf(key), library.scan().map { it.key })
    }

    @Test fun deleteRemovesTheFileAndForgetsTheMapping() {
        library.importTiles("N45E007.hgt", flatTile(100).stream())
        // Read once, so the tile is mapped and cached
        assertEquals(100.0, library.elevationAt(45.5, 7.5)!!, 1e-6)

        assertTrue(library.deleteTile(key))

        assertFalse(File(directory, "N45E007.hgt").exists())
        assertEquals(emptyList<ElevationTile>(), library.scan())
        assertNull(library.elevationAt(45.5, 7.5))
    }

    @Test fun positionsWithoutATileAreNull() {
        library.importTiles("N45E007.hgt", flatTile(100).stream())

        assertNull(library.elevationAt(46.5, 7.5))
    }

    @Test fun aOneTileCacheStillServesTwoTilesCorrectly() {
        val small = HgtTileLibrary(directory, maxOpenTiles = 1)
        small.importTiles("N45E007.hgt", flatTile(100).stream())
        small.importTiles("N45E008.hgt", flatTile(200).stream())

        assertEquals(100.0, small.elevationAt(45.5, 7.5)!!, 1e-6)
        assertEquals(200.0, small.elevationAt(45.5, 8.5)!!, 1e-6)
        assertEquals(100.0, small.elevationAt(45.5, 7.5)!!, 1e-6)
    }

    private fun flatTile(value: Int, resolution: HgtResolution = HgtResolution.THREE_ARC_SECOND): ByteArray {
        val n = resolution.samplesPerSide
        val buffer = ByteBuffer.allocate(resolution.byteSize.toInt()).order(ByteOrder.BIG_ENDIAN)
        repeat(n * n) { buffer.putShort(value.toShort()) }
        return buffer.array()
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun ByteArray.stream() = ByteArrayInputStream(this)

    /** [size] zero bytes, counting how many were read. */
    private class CountingZeros(private val size: Long) : InputStream() {
        var served = 0L
            private set

        override fun read(): Int = if (served < size) 0.also { served++ } else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (served >= size) return -1
            val count = minOf(length.toLong(), size - served).toInt()
            buffer.fill(0, offset, offset + count)
            served += count
            return count
        }
    }
}
