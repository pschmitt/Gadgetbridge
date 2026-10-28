package nodomain.freeyourgadget.gadgetbridge.activities.vendorimport

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorDataCategory
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorDataImporter
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorExport
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorExportDevice
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorImportSelection
import org.slf4j.LoggerFactory

class VendorDataImportViewModel(
    application: Application,
    val device: GBDevice,
    val importer: VendorDataImporter,
) : AndroidViewModel(application) {
    sealed class ScanState {
        data object Instructions : ScanState()
        data object Scanning : ScanState()
        data object Failed : ScanState()
        data class Scanned(val uri: Uri, val export: VendorExport) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Instructions)
    val scanState: StateFlow<ScanState> = _scanState

    var selectedDevice: VendorExportDevice? = null
        private set
    val selectedCategories = mutableSetOf<VendorDataCategory>()
    val selectedAppCategories = mutableSetOf<VendorDataCategory>()

    /**
     * True when this screen started an import, or displayed a running import.
     */
    var importDisplayed = false

    fun scan(uri: Uri) {
        _scanState.value = ScanState.Scanning
        viewModelScope.launch {
            _scanState.value = try {
                val export = withContext(Dispatchers.IO) {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Failed to open $uri")
                    input.use { importer.scan(getApplication(), it) }
                }
                selectDevice(
                    export.devices.find { it.id == device.firmwareVersion2 } ?: export.devices.singleOrNull()
                )
                selectedAppCategories.clear()
                selectedAppCategories.addAll(export.appEntries.keys)
                ScanState.Scanned(uri, export)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to scan {}", uri, e)
                ScanState.Failed
            }
        }
    }

    fun selectDevice(exportDevice: VendorExportDevice?) {
        selectedDevice = exportDevice
        selectedCategories.clear()
        exportDevice?.let { selectedCategories.addAll(it.counts.keys) }
    }

    fun selection(): VendorImportSelection? {
        val exportDevice = selectedDevice ?: return null
        if (selectedCategories.isEmpty() && selectedAppCategories.isEmpty()) {
            return null
        }
        return VendorImportSelection(exportDevice.id, selectedCategories.toSet(), selectedAppCategories.toSet())
    }

    fun selectedCount(export: VendorExport): Int {
        val exportDevice = selectedDevice ?: return 0
        return selectedCategories.sumOf { exportDevice.counts[it] ?: 0 } +
            selectedAppCategories.sumOf { export.appEntries[it] ?: 0 }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(VendorDataImportViewModel::class.java)
    }
}
