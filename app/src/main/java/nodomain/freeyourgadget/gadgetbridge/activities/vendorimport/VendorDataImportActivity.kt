package nodomain.freeyourgadget.gadgetbridge.activities.vendorimport

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityVendorDataImportBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemVendorImportCategoryBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemVendorImportDeviceBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.AndroidUtils
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getDevice
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorDataCategory
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorDataImportWorker
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorDataImportWorker.ImportState
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorExport
import nodomain.freeyourgadget.gadgetbridge.vendorimport.VendorExportDevice
import org.slf4j.LoggerFactory
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class VendorDataImportActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityVendorDataImportBinding
    private val dateFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    private var importState: ImportState = ImportState.None
    private var renderedExport: VendorExport? = null
    private var renderedCategoriesFor: VendorExportDevice? = null

    private val viewModel: VendorDataImportViewModel by viewModels {
        viewModelFactory {
            initializer {
                val device = intent.getDevice()!!
                VendorDataImportViewModel(
                    application,
                    device,
                    device.deviceCoordinator.getVendorDataImporter(device)!!
                )
            }
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.scan(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVendorDataImportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val device = intent.getDevice()
        if (device == null || device.deviceCoordinator.getVendorDataImporter(device) == null) {
            LOG.error("Devices does not has a data importer: {}", device)
            finish()
            return
        }

        if (intent.getBooleanExtra(EXTRA_SHOW_IMPORT, false)) {
            viewModel.importDisplayed = true
        }

        binding.vendorImportInstructions.text = viewModel.importer.instructions
            .mapIndexed { i, step -> "${i + 1}. ${getString(step)}" }
            .joinToString("\n\n")
        binding.vendorImportMoreInfo.setOnClickListener { AndroidUtils.openWebsite(viewModel.importer.infoUrl) }
        binding.vendorImportClose.setOnClickListener { finish() }
        binding.vendorImportPick.setOnClickListener { launchPicker() }
        binding.vendorImportSelectFile.setOnClickListener { launchPicker() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(
                    viewModel.scanState,
                    VendorDataImportWorker.observe(this@VendorDataImportActivity, device)
                ) { scan, job ->
                    scan to job
                }.collect { (scan, job) ->
                    importState = job
                    render(scan, job)
                }
            }
        }
    }

    private fun launchPicker() {
        pickFile.launch(
            arrayOf(
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream",
            )
        )
    }

    private fun render(scan: VendorDataImportViewModel.ScanState, job: ImportState) {
        if (job is ImportState.Running) {
            viewModel.importDisplayed = true
        }
        val importToDisplay = job.takeIf {
            it is ImportState.Running || (it is ImportState.Finished && viewModel.importDisplayed)
        }

        val scanned = (scan as? VendorDataImportViewModel.ScanState.Scanned)?.takeIf { importToDisplay == null }
        val export = scanned?.export?.takeIf { it.devices.isNotEmpty() }

        val scanning = scan is VendorDataImportViewModel.ScanState.Scanning && importToDisplay == null
        binding.vendorImportInstructions.visibility = visibleIf(export == null && importToDisplay == null)
        binding.vendorImportInstructionButtons.visibility = binding.vendorImportInstructions.visibility
        binding.vendorImportSelectFile.isEnabled = !scanning
        binding.vendorImportBusy.visibility = visibleIf(scanning)

        binding.vendorImportInfo.setTextOrHide(
            when {
                importToDisplay != null -> null
                job is ImportState.OtherDevice -> getString(
                    R.string.vendor_data_import_running_other,
                    GBApplication.app().deviceManager.getDeviceByAddress(job.address)?.aliasOrName ?: job.address
                )

                scan is VendorDataImportViewModel.ScanState.Scanning -> getString(R.string.vendor_data_import_scanning)
                scan is VendorDataImportViewModel.ScanState.Failed -> getString(R.string.vendor_data_import_scan_failed)
                scanned != null && export == null -> getString(R.string.vendor_data_import_no_devices)
                export != null -> getString(
                    R.string.vendor_data_import_select_device,
                    export.devices.size,
                    viewModel.device.aliasOrName
                )
                else -> null
            }
        )

        renderDevices(export)
        renderCategories(export)
        renderImport(importToDisplay)

        binding.vendorImportPick.visibility = visibleIf(export != null)
        binding.vendorImportClose.setText(if (importToDisplay is ImportState.Running) R.string.dialog_hide else R.string.dialog_close)

        val button = binding.vendorImportButton
        when {
            importToDisplay is ImportState.Running -> {
                button.visibility = View.VISIBLE
                button.isEnabled = true
                button.setText(R.string.cancel)
                button.setOnClickListener { VendorDataImportWorker.cancel(this) }
            }

            importToDisplay == null && export != null -> {
                button.visibility = View.VISIBLE
                button.isEnabled = job !is ImportState.OtherDevice && viewModel.selection() != null
                button.setText(R.string.file_import)
                button.setOnClickListener { startImport(scanned) }
            }

            else -> button.visibility = View.GONE
        }
    }

    private fun renderDevices(export: VendorExport?) {
        binding.vendorImportDevices.visibility = visibleIf(export != null)
        if (export === renderedExport) {
            return
        }
        renderedExport = export
        renderedCategoriesFor = null

        val container = binding.vendorImportDevices
        container.removeAllViews()
        val rows = export?.devices?.map { exportDevice ->
            ItemVendorImportDeviceBinding.inflate(layoutInflater, container, false).also {
                it.vendorImportDeviceText.text = describe(exportDevice)
                it.vendorImportDeviceButton.isChecked = exportDevice == viewModel.selectedDevice
                container.addView(it.root)
            }
        }.orEmpty()
        rows.forEachIndexed { i, row ->
            row.root.setOnClickListener {
                viewModel.selectDevice(export!!.devices[i])
                rows.forEachIndexed { j, other -> other.vendorImportDeviceButton.isChecked = i == j }
                rerender()
            }
        }
    }

    private fun renderCategories(export: VendorExport?) {
        val exportDevice = viewModel.selectedDevice.takeIf { export != null }
        binding.vendorImportCategoriesTitle.visibility = visibleIf(exportDevice != null)
        binding.vendorImportCategories.visibility = visibleIf(exportDevice != null)

        binding.vendorImportWarning.setTextOrHide(
            exportDevice?.deviceType?.takeIf { it != viewModel.device.type }?.let {
                getString(R.string.vendor_data_import_model_mismatch, exportDevice.name, viewModel.device.aliasOrName)
            }
        )

        if (exportDevice == null || exportDevice === renderedCategoriesFor) {
            return
        }
        renderedCategoriesFor = exportDevice

        val container = binding.vendorImportCategories
        container.removeAllViews()
        for (category in exportDevice.counts.keys.sorted()) {
            addCategoryRow(
                container,
                getString(
                    R.string.vendor_data_import_category_count,
                    getString(category.title),
                    exportDevice.counts[category]
                ),
                viewModel.selectedCategories,
                category
            )
        }
        for (category in export!!.appEntries.keys.sorted()) {
            addCategoryRow(
                container,
                getString(
                    R.string.vendor_data_import_app_category_count,
                    getString(category.title),
                    export.appEntries[category]
                ),
                viewModel.selectedAppCategories,
                category
            )
        }
    }

    private fun addCategoryRow(
        container: LinearLayout,
        text: String,
        selected: MutableSet<VendorDataCategory>,
        category: VendorDataCategory,
    ) {
        val row = ItemVendorImportCategoryBinding.inflate(layoutInflater, container, false)
        row.vendorImportCategoryText.text = text
        row.vendorImportCategoryButton.isChecked = category in selected
        row.root.setOnClickListener {
            val checked = !row.vendorImportCategoryButton.isChecked
            row.vendorImportCategoryButton.isChecked = checked
            if (checked) selected.add(category) else selected.remove(category)
            rerender()
        }
        container.addView(row.root)
    }

    private fun renderImport(job: ImportState?) {
        val running = job as? ImportState.Running
        binding.vendorImportProgress.visibility = visibleIf(running != null)
        binding.vendorImportProgressText.visibility = visibleIf(running != null)
        if (running != null) {
            val percent = if (running.total > 0) running.done * 100 / running.total else 0
            binding.vendorImportProgress.isIndeterminate = running.total == 0
            binding.vendorImportProgress.setProgressCompat(percent, true)
            binding.vendorImportProgressText.text =
                getString(R.string.install_progress, running.done, running.total, percent)
        }

        val finished = job as? ImportState.Finished
        binding.vendorImportResult.setTextOrHide(finished?.let { result ->
            val title = getString(
                when (result.state) {
                    WorkInfo.State.SUCCEEDED -> R.string.vendor_data_import_finished
                    WorkInfo.State.CANCELLED -> R.string.vendor_data_import_cancelled
                    else -> R.string.vendor_data_import_failed
                }
            )
            val lines = result.counts.toSortedMap().map { (category, counts) ->
                getString(
                    R.string.vendor_data_import_result,
                    getString(category.title),
                    counts.imported,
                    counts.skipped,
                    counts.failed
                )
            }
            (listOf(title) + lines).joinToString("\n")
        })
    }

    private fun startImport(scanned: VendorDataImportViewModel.ScanState.Scanned?) {
        val selection = viewModel.selection() ?: return
        if (scanned == null) {
            return
        }
        VendorDataImportWorker.takeUriPermission(this, scanned.uri)
        VendorDataImportWorker.enqueue(
            this,
            viewModel.device,
            scanned.uri,
            selection,
            viewModel.selectedCount(scanned.export)
        )
        viewModel.importDisplayed = true
    }

    private fun describe(exportDevice: VendorExportDevice): String {
        val lines = mutableListOf(exportDevice.name)
        exportDevice.serialNumber?.let { lines.add(getString(R.string.vendor_data_import_serial_number, it)) }
        if (exportDevice.firstDate != null && exportDevice.lastDate != null) {
            lines.add(
                getString(
                    R.string.date_placeholders__start_time__end_time,
                    dateFormatter.format(exportDevice.firstDate),
                    dateFormatter.format(exportDevice.lastDate)
                )
            )
        }
        lines.add(exportDevice.counts.toSortedMap().map { (category, count) ->
            getString(R.string.vendor_data_import_category_count, getString(category.title), count)
        }.joinToString(", "))
        return lines.joinToString("\n")
    }

    private fun rerender() {
        render(viewModel.scanState.value, importState)
    }

    private fun visibleIf(visible: Boolean) = if (visible) View.VISIBLE else View.GONE

    private fun TextView.setTextOrHide(value: String?) {
        text = value
        visibility = if (value.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(VendorDataImportActivity::class.java)

        private const val EXTRA_SHOW_IMPORT = "show_import"

        fun createImportIntent(context: Context, device: GBDevice): Intent =
            Intent(context, VendorDataImportActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
                .putExtra(EXTRA_SHOW_IMPORT, true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
