package nodomain.freeyourgadget.gadgetbridge.install

import android.net.Uri
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice

interface InstallHandlerV2 {
    /**
     * Reads and parses a list of files. Called once, on a background thread.
     */
    fun prepare(files: List<Uri>): ParsedFiles

    /**
     * Checks if the items in the parsed files can be installed on the device. Called on the main thread,
     * main thread, only when the device is initialized and not already busy.
     */
    fun checkCompatibility(parsedFiles: ParsedFiles.Supported, device: GBDevice): Compatibility

    fun createOptionsFragment(): InstallOptionsFragment? = null
}

sealed interface ParsedFiles {
    data class Supported(
        val items: List<InstallItem>,
        val info: String?,
        val confirmation: String? = null,
    ) : ParsedFiles

    data class Unsupported(val reason: String) : ParsedFiles
}

sealed interface Compatibility {
    data class Compatible(val warnings: List<String> = emptyList()) : Compatibility

    data class Incompatible(val reason: String) : Compatibility
}
