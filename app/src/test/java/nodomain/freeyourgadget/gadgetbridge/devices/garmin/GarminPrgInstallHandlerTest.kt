package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import android.net.Uri
import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.fenix.GarminFenix3Coordinator
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.watches.venu.GarminVenu3Coordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.install.ParsedFiles
import nodomain.freeyourgadget.gadgetbridge.install.Compatibility
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

@Suppress("SameParameterValue")
class GarminPrgInstallHandlerTest : TestBase() {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val coordinator = GarminVenu3Coordinator()

    private val prgBytes = byteArrayOf(0xd0.toByte(), 0x00, 0xd0.toByte()) + ByteArray(61) { it.toByte() }

    private fun file(name: String, bytes: ByteArray): Uri {
        val file = tempFolder.newFile(name)
        file.writeBytes(bytes)
        return Uri.fromFile(file)
    }

    private fun prg(name: String): Uri = file(name, prgBytes)

    private fun text(name: String): Uri = file(name, "not a prg".toByteArray())

    private fun device(): GBDevice {
        val device = mock(GBDevice::class.java)
        `when`(device.address).thenReturn(ADDRESS)
        return device
    }

    private fun supported(): ParsedFiles.Supported =
        GarminPrgInstallHandler(context, coordinator).prepare(listOf(prg("app.prg"))) as ParsedFiles.Supported

    @Test
    fun handlerFoundWhenOneFileIsPrg() {
        assertNotNull(coordinator.findInstallHandlerV2(listOf(text("notes.txt"), prg("app.prg")), context))
    }

    @Test
    fun noHandlerWithoutPrg() {
        assertNull(coordinator.findInstallHandlerV2(listOf(text("notes.txt")), context))
    }

    @Test
    fun prepareAcceptsEachPrgFile() {
        val result = GarminPrgInstallHandler(context, coordinator).prepare(listOf(prg("first.prg"), prg("second.prg")))

        val supported = result as ParsedFiles.Supported
        assertEquals(listOf("first.prg", "second.prg"), supported.items.map { it.displayName })
        assertEquals(listOf(64L, 64L), supported.items.map { it.sizeBytes })
        assertEquals(context.getString(R.string.fw_upgrade_notice, "PRG"), supported.info)
    }

    @Test
    fun prepareRejectsWhenAFileIsNotPrg() {
        val result = GarminPrgInstallHandler(context, coordinator).prepare(listOf(prg("app.prg"), text("notes.txt")))

        assertEquals(
            ParsedFiles.Unsupported(context.getString(R.string.install_files_not_supported, "notes.txt")),
            result
        )
    }

    @Test
    fun itemReadsTheFileBytes() {
        val item = supported().items.single() as GarminPrgInstallItem
        assertArrayEquals(prgBytes, item.file.readBytes(context))
    }

    @Test
    fun compatibleWithCapability() {
        GBApplication.getDeviceSpecificSharedPrefs(ADDRESS).edit {
            putStringSet(
                GarminPreferences.PREF_GARMIN_CAPABILITIES,
                setOf(GarminCapability.CONNECTIQ_APP_MANAGEMENT.name)
            )
        }

        val compatibility = GarminPrgInstallHandler(context, coordinator).checkCompatibility(supported(), device())

        assertEquals(Compatibility.Compatible(), compatibility)
    }

    @Test
    fun incompatibleWithoutCapability() {
        val compatibility = GarminPrgInstallHandler(context, coordinator).checkCompatibility(supported(), device())

        assertEquals(
            Compatibility.Incompatible(context.getString(R.string.fwapp_install_device_not_supported)),
            compatibility
        )
    }

    @Test
    fun warningWithoutCapabilityWhenUnsupportedFilesAreAllowed() {
        GBApplication.getDeviceSpecificSharedPrefs(ADDRESS).edit {
            putBoolean("install_unsupported_files", true)
        }

        val compatibility = GarminPrgInstallHandler(context, coordinator).checkCompatibility(supported(), device())

        assertEquals(
            Compatibility.Compatible(listOf(context.getString(R.string.install_unsupported_files_warning))),
            compatibility
        )
    }

    companion object {
        private const val ADDRESS = "AA:BB:CC:DD:EE:FF"
    }
}
