package nodomain.freeyourgadget.gadgetbridge.activities.discovery

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.widget.SearchView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.BuildConfig
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityDeviceTypePickerBinding
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.ConnectionType
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator.DeviceKind
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.util.preferences.MacAddressInputFilter
import java.util.Random

private const val EXTRA_TITLE = "device_type_picker_title"
private const val EXTRA_ADDRESS = "device_type_picker_address"
private const val EXTRA_CONNECTION_TYPES = "device_type_picker_connection_types"
private const val EXTRA_PRESELECTED = "device_type_picker_preselected"
private const val EXTRA_RESULT_ADDRESS = "device_type_picker_result_address"
private const val EXTRA_RESULT_DEVICE_TYPE = "device_type_picker_result_device_type"

class DeviceTypePickerActivity : AbstractGBActivity() {
    private val viewModel: DeviceTypePickerViewModel by viewModels()
    private val adapter = EntryAdapter()
    private lateinit var binding: ActivityDeviceTypePickerBinding
    private var preselected: DeviceType? = null
    private var manufacturers: List<String> = emptyList()
    private var kinds: List<DeviceKind> = emptyList()
    private var scrollToPreselected = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityDeviceTypePickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setTitle(intent.getIntExtra(EXTRA_TITLE, R.string.widget_settings_select_device_title))
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val address = intent.getStringExtra(EXTRA_ADDRESS)
        preselected = intent.getStringExtra(EXTRA_PRESELECTED)?.let { DeviceType.valueOf(it) }
        val connectionTypes = intent.getStringArrayExtra(EXTRA_CONNECTION_TYPES)
            ?.map { ConnectionType.valueOf(it) }
            ?.toSet()
            ?: ConnectionType.entries.toSet()

        if (savedInstanceState == null) {
            binding.deviceTypePickerAddress.setText(address ?: randomMac())
            scrollToPreselected = preselected != null
        }
        if (address != null) {
            binding.deviceTypePickerAddress.isEnabled = false
        } else {
            binding.deviceTypePickerAddress.filters = arrayOf(MacAddressInputFilter())
            binding.deviceTypePickerAddress.doAfterTextChanged {
                binding.deviceTypePickerAddressLayout.error = null
            }
        }

        binding.deviceTypePickerManufacturer.setOnItemClickListener { _, _, position, _ ->
            viewModel.setManufacturer(if (position == 0) null else manufacturers[position - 1])
        }
        binding.deviceTypePickerKind.setOnItemClickListener { _, _, position, _ ->
            viewModel.setKind(if (position == 0) null else kinds[position - 1])
        }

        binding.deviceTypePickerList.apply {
            layoutManager = LinearLayoutManager(this@DeviceTypePickerActivity)
            adapter = this@DeviceTypePickerActivity.adapter
        }

        binding.deviceTypePickerConfirm.setOnClickListener {
            viewModel.state.value?.checked?.let { confirm(it) }
        }

        lifecycleScope.launch {
            viewModel.state.collect { state ->
                binding.deviceTypePickerProgress.isVisible = state == null
                state?.let { showState(it) }
            }
        }

        viewModel.load(this, connectionTypes, preselected)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.device_type_picker_menu, menu)
        val searchView = menu.findItem(R.id.device_type_picker_action_search).actionView as SearchView
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean = false
            override fun onQueryTextChange(newText: String?): Boolean {
                viewModel.setQuery(newText.orEmpty())
                return true
            }
        })
        return super.onCreateOptionsMenu(menu)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun showState(state: DeviceTypePickerViewModel.State) {
        manufacturers = state.manufacturers
        kinds = state.kinds

        val allManufacturers = getString(R.string.all_manufacturers)
        binding.deviceTypePickerManufacturer.setSimpleItems((listOf(allManufacturers) + state.manufacturers).toTypedArray())
        binding.deviceTypePickerManufacturer.setText(state.manufacturer ?: allManufacturers, false)

        val allKinds = getString(R.string.all_categories)
        val kindLabels = state.kinds.map { getString(it.labelRes()) }
        binding.deviceTypePickerKind.setSimpleItems((listOf(allKinds) + kindLabels).toTypedArray())
        binding.deviceTypePickerKind.setText(state.kind?.let { getString(it.labelRes()) } ?: allKinds, false)

        adapter.submit(state.entries, state.checked)
        binding.deviceTypePickerEmpty.isVisible = state.entries.isEmpty()

        if (state.checked != null && state.checked != preselected) {
            binding.deviceTypePickerConfirm.show()
        } else {
            binding.deviceTypePickerConfirm.hide()
        }

        if (scrollToPreselected) {
            scrollToPreselected = false
            val position = state.entries.indexOfFirst { it.deviceType == preselected }
            if (position >= 0) {
                (binding.deviceTypePickerList.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(position, 0)
            }
        }
    }

    private fun confirm(deviceType: DeviceType) {
        val address = binding.deviceTypePickerAddress.text.toString()
        if (!isValidMacAddress(address)) {
            binding.deviceTypePickerAddressLayout.error = getString(R.string.invalid_mac_address)
            binding.deviceTypePickerAppBar.setExpanded(true)
            return
        }
        setResult(
            RESULT_OK,
            Intent()
                .putExtra(EXTRA_RESULT_ADDRESS, address)
                .putExtra(EXTRA_RESULT_DEVICE_TYPE, deviceType.name)
        )
        finish()
    }

    private inner class EntryAdapter : RecyclerView.Adapter<EntryAdapter.ViewHolder>() {
        private var items: List<DeviceTypePickerViewModel.Entry> = emptyList()
        private var checked: DeviceType? = null

        @SuppressLint("NotifyDataSetChanged")
        fun submit(newItems: List<DeviceTypePickerViewModel.Entry>, newChecked: DeviceType?) {
            items = newItems
            checked = newChecked
            notifyDataSetChanged()
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.device_type_picker_item_icon)
            val name: TextView = view.findViewById(R.id.device_type_picker_item_name)
            val summary: TextView = view.findViewById(R.id.device_type_picker_item_summary)
            val experimental: View = view.findViewById(R.id.device_type_picker_item_experimental)
            val radio: RadioButton = view.findViewById(R.id.device_type_picker_item_radio)
        }

        override fun getItemCount(): Int = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_device_type_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = items[position]
            holder.icon.setImageResource(entry.icon)
            holder.name.text = entry.name
            holder.summary.text = getString(
                R.string.activity_list_row_stats_pair,
                entry.manufacturer,
                getString(entry.kind.labelRes())
            )
            holder.experimental.isVisible = entry.experimental
            holder.radio.isChecked = entry.deviceType == checked
            holder.itemView.setOnClickListener { viewModel.setChecked(entry.deviceType) }
        }
    }

    companion object {
        private fun isValidMacAddress(mac: String): Boolean {
            if (BuildConfig.INTERNET_ACCESS) {
                // For builds with internet access (Bangle.js), allow more flexible formats
                return mac.isNotEmpty()
            }
            if (mac.startsWith("usb:")) {
                return true
            }
            return "^([0-9A-F]{2}:){5}[0-9A-F]{2}$".toRegex().matches(mac)
        }

        private fun randomMac(): String {
            val random = Random()
            return String.format(
                "%02X:%02X:%02X:%02X:%02X:%02X",
                random.nextInt(0xff),
                random.nextInt(0xff),
                random.nextInt(0xff),
                random.nextInt(0xff),
                random.nextInt(0xff),
                random.nextInt(0xff)
            )
        }
    }
}

/**
 * Starts [DeviceTypePickerActivity]. The result is null when the user goes back.
 */
class PickDeviceType : ActivityResultContract<PickDeviceType.Input, PickDeviceType.Selection?>() {
    /**
     * @param address the device address, or null to let the user enter one
     * @param connectionTypes the connection types of the device types to show
     * @param preselected the preselected (current) type for the device
     */
    data class Input @JvmOverloads constructor(
        @StringRes val title: Int,
        val address: String?,
        val connectionTypes: Set<ConnectionType> = ConnectionType.entries.toSet(),
        val preselected: DeviceType? = null
    )

    data class Selection(val address: String, val deviceType: DeviceType)

    override fun createIntent(context: Context, input: Input): Intent =
        Intent(context, DeviceTypePickerActivity::class.java)
            .putExtra(EXTRA_TITLE, input.title)
            .putExtra(EXTRA_ADDRESS, input.address)
            .putExtra(EXTRA_CONNECTION_TYPES, input.connectionTypes.map { it.name }.toTypedArray())
            .putExtra(EXTRA_PRESELECTED, input.preselected?.name)

    override fun parseResult(resultCode: Int, intent: Intent?): Selection? {
        if (resultCode != Activity.RESULT_OK || intent == null) {
            return null
        }
        val address = intent.getStringExtra(EXTRA_RESULT_ADDRESS) ?: return null
        val deviceType = intent.getStringExtra(EXTRA_RESULT_DEVICE_TYPE) ?: return null
        return Selection(address, DeviceType.valueOf(deviceType))
    }
}
