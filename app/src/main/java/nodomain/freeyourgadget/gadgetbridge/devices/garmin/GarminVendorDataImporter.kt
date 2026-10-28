package nodomain.freeyourgadget.gadgetbridge.devices.garmin

import android.content.Context
import androidx.core.content.edit
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHydrationSampleDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.FileType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitImporter
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.FitDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitFileId
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.http.interceptors.HydrationInterceptor
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorDataCategory
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorDataImporter
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorExport
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorExportDevice
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorImportItemResult
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorImportListener
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorImportSelection
import org.slf4j.LoggerFactory
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.zip.ZipInputStream

class GarminVendorDataImporter : VendorDataImporter {
    override val instructions = listOf(
        R.string.vendor_data_import_garmin_step_1,
        R.string.vendor_data_import_garmin_step_2,
        R.string.vendor_data_import_garmin_step_3,
        R.string.vendor_data_import_garmin_step_4,
        R.string.vendor_data_import_garmin_step_5,
    )

    override val infoUrl = "https://gadgetbridge.org/basics/topics/garmin/import-garmin-connect/"

    override suspend fun scan(context: Context, input: InputStream): VendorExport {
        val serialNumbers = mutableMapOf<Long, String>()
        val devices = mutableMapOf<Long, ScannedDevice>()
        var appHydration = 0

        walk(input, object : ExportVisitor {
            override fun onDevices(json: JsonElement) {
                serialNumbers.putAll(parseSerialNumbers(json))
            }

            override fun onFit(bytes: ByteArray) {
                val fileId = parseFileId(bytes) ?: return
                val unitId = fileId.serialNumber ?: return
                val device = devices.getOrPut(unitId) { ScannedDevice() }
                if (device.product == null) {
                    device.manufacturer = fileId.manufacturer
                    device.product = fileId.product
                }
                device.add(fileId.type.toCategory(), fileId.timeCreated?.let { toLocalDate(it) })
            }

            override fun onHydration(entries: JsonArray) {
                for (element in entries) {
                    val entry = element.asJsonObject
                    when (entry.getString(KEY_SOURCE)) {
                        SOURCE_DEVICE -> {
                            val unitId = entry.getLong(KEY_DEVICE_ID) ?: continue
                            val date = entry.getString(KEY_CALENDAR_DATE)?.let { LocalDate.parse(it) }
                            devices.getOrPut(unitId) { ScannedDevice() }.add(VendorDataCategory.HYDRATION, date)
                        }

                        SOURCE_APP -> appHydration++
                    }
                }
            }
        })

        LOG.debug(
            "Found in the export: {} devices, {} app hydration entries",
            devices.size,
            appHydration,
        )

        val exportDevices = devices.map { (unitId, device) ->
            VendorExportDevice(
                id = unitId.toString(),
                name = device.name(context),
                serialNumber = serialNumbers[unitId],
                deviceType = device.product?.let { GarminProductNumbers.getDeviceType(it) },
                firstDate = device.firstDate,
                lastDate = device.lastDate,
                counts = device.counts,
            )
        }.sortedByDescending { it.lastDate }

        val appEntries = if (appHydration > 0) mapOf(VendorDataCategory.HYDRATION to appHydration) else emptyMap()
        return VendorExport(exportDevices, appEntries)
    }

    override suspend fun importData(
        context: Context,
        target: GBDevice,
        input: InputStream,
        selection: VendorImportSelection,
        listener: VendorImportListener,
    ) {
        val unitId = selection.deviceId.toLong()
        val fitImporter = FitImporter(context, target)
        val tempFile = File(context.cacheDir, "vendor-import.fit")
        var hydrationStored = 0

        try {
            walk(input, object : ExportVisitor {
                override fun onFit(bytes: ByteArray) {
                    val fileId = parseFileId(bytes) ?: return
                    if (fileId.serialNumber != unitId) {
                        return
                    }
                    val category = fileId.type.toCategory()
                    if (category !in selection.categories) {
                        return
                    }

                    try {
                        tempFile.writeBytes(bytes)
                        fileId.timeCreated?.let { tempFile.setLastModified(it * 1000L) }
                        fitImporter.importFile(tempFile, false)
                        listener.onItem(category, VendorImportItemResult.IMPORTED)
                    } catch (e: Exception) {
                        LOG.error("Failed to import FIT file {} of type {}", fileId.timeCreated, fileId.type, e)
                        listener.onItem(category, VendorImportItemResult.FAILED)
                    }
                }

                override fun onHydration(entries: JsonArray) {
                    hydrationStored += importHydration(target, entries, unitId, selection, listener)
                }
            })
        } finally {
            tempFile.delete()
        }

        if (hydrationStored > 0) {
            GBApplication.getDeviceSpecificSharedPrefs(target.address).edit {
                putBoolean(GarminPreferences.PREF_GARMIN_HYDRATION_SUPPORTED, true)
            }
        }
    }

    private fun importHydration(
        target: GBDevice,
        entries: JsonArray,
        unitId: Long,
        selection: VendorImportSelection,
        listener: VendorImportListener,
    ): Int {
        var stored = 0
        GBApplication.acquireDB().use { db ->
            val session = db.daoSession
            val provider = target.deviceCoordinator.getHydrationSampleProvider(target, session) ?: return 0
            val deviceId = requireNotNull(DBHelper.getDevice(target, session).id)

            for (element in entries) {
                val entry = element.asJsonObject
                val selected = when (entry.getString(KEY_SOURCE)) {
                    SOURCE_DEVICE -> VendorDataCategory.HYDRATION in selection.categories &&
                        entry.getLong(KEY_DEVICE_ID) == unitId

                    SOURCE_APP -> VendorDataCategory.HYDRATION in selection.appCategories
                    else -> false
                }
                if (!selected) {
                    continue
                }

                try {
                    val day = LocalDate.parse(entry.getString(KEY_CALENDAR_DATE))
                    val timestamp = LocalDateTime.parse(
                        entry.getString(KEY_TIMESTAMP_LOCAL),
                        HydrationInterceptor.TIMESTAMP_LOCAL_PARSER
                    ).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val volumeMl = entry.get(KEY_VALUE).asDouble

                    if (hydrationEntryExists(session, deviceId, timestamp, volumeMl)) {
                        listener.onItem(VendorDataCategory.HYDRATION, VendorImportItemResult.SKIPPED)
                        continue
                    }

                    LOG.debug("Storing hydration entry at {} for {}: {}ml", timestamp, day, volumeMl)
                    provider.addEntry(HydrationSampleProvider.toDay(day), timestamp, volumeMl)
                    stored++
                    listener.onItem(VendorDataCategory.HYDRATION, VendorImportItemResult.IMPORTED)
                } catch (e: Exception) {
                    LOG.error("Failed to import hydration entry {}", entry, e)
                    listener.onItem(VendorDataCategory.HYDRATION, VendorImportItemResult.FAILED)
                }
            }
        }
        return stored
    }

    private fun hydrationEntryExists(session: DaoSession, deviceId: Long, timestamp: Long, volumeMl: Double): Boolean {
        return session.genericHydrationSampleDao.queryBuilder()
            .where(
                GenericHydrationSampleDao.Properties.DeviceId.eq(deviceId),
                GenericHydrationSampleDao.Properties.Timestamp.eq(timestamp)
            )
            .list()
            .any { it.volumeMl == volumeMl }
    }

    private suspend fun walk(input: InputStream, visitor: ExportVisitor) {
        val zip = ZipInputStream(BufferedInputStream(input))
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = zip.nextEntry ?: break
            if (entry.isDirectory) {
                continue
            }
            val name = entry.name
            when {
                name.endsWith(DEVICES_FILE) -> parseJson(zip, name)?.let { visitor.onDevices(it) }

                UPLOADED_FILES_ZIP.containsMatchIn(name) -> {
                    LOG.debug("Reading uploaded files from {}", name)
                    walkFitZip(ZipInputStream(zip), visitor)
                }

                HYDRATION_FILE.containsMatchIn(name) -> {
                    LOG.debug("Reading hydration log {}", name)
                    val json = parseJson(zip, name)
                    if (json != null && json.isJsonArray) {
                        visitor.onHydration(json.asJsonArray)
                    }
                }
            }
        }
    }

    private suspend fun walkFitZip(zip: ZipInputStream, visitor: ExportVisitor) {
        while (true) {
            currentCoroutineContext().ensureActive()
            val entry = zip.nextEntry ?: break
            if (!entry.isDirectory && entry.name.endsWith(".fit", ignoreCase = true)) {
                visitor.onFit(zip.readBytes())
            }
        }
    }

    private fun parseJson(input: InputStream, name: String): JsonElement? {
        return try {
            JsonParser.parseReader(InputStreamReader(input, Charsets.UTF_8))
        } catch (e: Exception) {
            LOG.warn("Failed to parse {}", name, e)
            null
        }
    }

    private fun parseFileId(bytes: ByteArray): FitFileId? {
        return try {
            // FIXME this reads the entire file just to get the ID... we need a streaming FIT parser
            FitFile.parseIncoming(bytes).records.firstNotNullOfOrNull { it as? FitFileId }
        } catch (e: Exception) {
            LOG.warn("Failed to parse FIT file", e)
            null
        }
    }

    private fun parseSerialNumbers(json: JsonElement): Map<Long, String> {
        val serialNumbers = mutableMapOf<Long, String>()
        try {
            for (info in json.asJsonObject.getAsJsonArray("deviceAndContentInfo")) {
                val devices = info.asJsonObject.getAsJsonArray("Devices") ?: continue
                for (device in devices) {
                    val obj = device.asJsonObject
                    val unitId = obj.getString("unitId")?.toLongOrNull() ?: continue
                    val serialNumber = obj.getString("serialNumber") ?: continue
                    serialNumbers[unitId] = serialNumber
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse the device list", e)
        }
        return serialNumbers
    }

    private fun toLocalDate(epochSeconds: Long): LocalDate {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun FileType.FILETYPE?.toCategory(): VendorDataCategory = when (this) {
        FileType.FILETYPE.ACTIVITY -> VendorDataCategory.WORKOUTS

        FileType.FILETYPE.METRICS -> VendorDataCategory.METRICS

        FileType.FILETYPE.MONITOR -> VendorDataCategory.ACTIVITY

        FileType.FILETYPE.SLEEP,
        FileType.FILETYPE.SLP_DISR -> VendorDataCategory.SLEEP

        FileType.FILETYPE.HRV_STATUS -> VendorDataCategory.HRV

        else -> VendorDataCategory.OTHER
    }

    private fun JsonObject.getString(key: String): String? {
        val value = get(key)
        return if (value != null && value.isJsonPrimitive) value.asString else null
    }

    private fun JsonObject.getLong(key: String): Long? {
        val value = get(key)
        return if (value != null && value.isJsonPrimitive) value.asLong else null
    }

    private interface ExportVisitor {
        fun onDevices(json: JsonElement) {}
        fun onFit(bytes: ByteArray)
        fun onHydration(entries: JsonArray)
    }

    private class ScannedDevice {
        var manufacturer: Int? = null
        var product: Int? = null
        var firstDate: LocalDate? = null
        var lastDate: LocalDate? = null
        val counts = mutableMapOf<VendorDataCategory, Int>()

        fun add(category: VendorDataCategory, date: LocalDate?) {
            counts[category] = (counts[category] ?: 0) + 1
            if (date != null) {
                if (firstDate?.isAfter(date) != false) {
                    firstDate = date
                }
                if (lastDate?.isBefore(date) != false) {
                    lastDate = date
                }
            }
        }

        fun name(context: Context): String {
            val product = product ?: return context.getString(R.string.unknown)
            return FitDevice.entries.find { it.manufacturer == (manufacturer ?: 1) && it.product == product }?.name
                ?: context.getString(R.string.vendor_data_import_garmin_unknown_product, product)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GarminVendorDataImporter::class.java)

        private const val DEVICES_FILE = "devicesandcontent.json"

        private val UPLOADED_FILES_ZIP = Regex("(^|/)DI-Connect-Uploaded-Files/[^/]+\\.zip$", RegexOption.IGNORE_CASE)

        private val HYDRATION_FILE =
            Regex("(^|/)DI-Connect-Aggregator/HydrationLogFile_[^/]*\\.json$", RegexOption.IGNORE_CASE)

        private const val KEY_SOURCE = "hydrationSource"
        private const val KEY_DEVICE_ID = "deviceId"
        private const val KEY_CALENDAR_DATE = "calendarDate"
        private const val KEY_TIMESTAMP_LOCAL = "timestampLocal"
        private const val KEY_VALUE = "valueInML"

        private const val SOURCE_DEVICE = "GARMIN_DEVICE"
        private const val SOURCE_APP = "GARMIN_GCM"
    }
}
