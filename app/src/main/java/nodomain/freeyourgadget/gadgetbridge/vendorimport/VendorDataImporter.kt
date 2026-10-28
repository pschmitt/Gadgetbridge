package nodomain.freeyourgadget.gadgetbridge.vendorimport

import android.content.Context
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import java.io.InputStream
import java.time.LocalDate

/**
 * Reads a data export of a vendor app, and imports the data of one device in it.
 */
interface VendorDataImporter {
    /**
     * The steps to get the export from the vendor, in order.
     */
    @get:StringRes
    val instructions: List<Int>

    /**
     * The web page with more information about the export.
     */
    val infoUrl: String

    /**
     * Lists the devices in the export, and the data that is available for each one.
     */
    suspend fun scan(context: Context, input: InputStream): VendorExport

    /**
     * Imports the selected data of the export into the target device.
     */
    suspend fun importData(
        context: Context,
        target: GBDevice,
        input: InputStream,
        selection: VendorImportSelection,
        listener: VendorImportListener,
    )
}

enum class VendorDataCategory(@field:StringRes val title: Int) {
    WORKOUTS(R.string.bottom_nav_workouts),
    ACTIVITY(R.string.activity_type_activity),
    SLEEP(R.string.menuitem_sleep),
    HRV(R.string.hrv),
    METRICS(R.string.generic_watch_measured_metrics),
    HYDRATION(R.string.pref_header_hydration),
    OTHER(R.string.other),
}

/**
 * The content of a vendor export. The app entries are data that the user typed in the vendor app,
 * and that have no device.
 */
data class VendorExport(
    val devices: List<VendorExportDevice>,
    val appEntries: Map<VendorDataCategory, Int>,
)

/**
 * A device in a vendor export, with the number of items in each category.
 */
data class VendorExportDevice(
    val id: String,
    val name: String,
    val serialNumber: String?,
    val deviceType: DeviceType?,
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val counts: Map<VendorDataCategory, Int>,
)

data class VendorImportSelection(
    val deviceId: String,
    val categories: Set<VendorDataCategory>,
    val appCategories: Set<VendorDataCategory>,
)

enum class VendorImportItemResult {
    IMPORTED,
    SKIPPED,
    FAILED,
}

fun interface VendorImportListener {
    fun onItem(category: VendorDataCategory, result: VendorImportItemResult)
}
