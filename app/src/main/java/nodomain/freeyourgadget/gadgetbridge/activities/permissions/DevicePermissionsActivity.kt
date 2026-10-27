package nodomain.freeyourgadget.gadgetbridge.activities.permissions

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityDevicePermissionsBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentWelcomePermissionRowBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemPermissionSectionBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.util.kotlin.getParcelableCompat
import nodomain.freeyourgadget.gadgetbridge.util.permissions.DevicePermission
import nodomain.freeyourgadget.gadgetbridge.util.permissions.GBPermission
import nodomain.freeyourgadget.gadgetbridge.util.permissions.MissingPermissions
import org.slf4j.LoggerFactory

class DevicePermissionsActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityDevicePermissionsBinding
    private lateinit var device: GBDevice
    private val adapter = PermissionsAdapter { it.request(this) }

    private val pendingRequests = ArrayDeque<GBPermission>()
    private var missingRecommended: List<GBPermission> = emptyList()

    private val deviceChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val changedDevice = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE) ?: return
            if (changedDevice.address == device.address) {
                device = changedDevice
                refresh()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        device = intent.getParcelableCompat<GBDevice>(GBDevice.EXTRA_DEVICE) ?: run {
            LOG.error("No device provided")
            finish()
            return
        }

        binding = ActivityDevicePermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.subtitle = device.aliasOrName

        binding.devicePermissionsList.layoutManager = LinearLayoutManager(this)
        binding.devicePermissionsList.adapter = adapter
        binding.devicePermissionsGrantRecommended.setOnClickListener {
            pendingRequests.clear()
            pendingRequests.addAll(missingRecommended)
            requestPending()
        }
        binding.devicePermissionsDone.setOnClickListener { close() }
        onBackPressedDispatcher.addCallback(this) { close() }

        LocalBroadcastManager.getInstance(this)
            .registerReceiver(deviceChangedReceiver, IntentFilter(GBDevice.ACTION_DEVICE_CHANGED))
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(deviceChangedReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (pendingRequests.isNotEmpty()) {
            requestPending()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        close()
        return true
    }

    private fun refresh() {
        val permissions = device.deviceCoordinator.getPermissions(device)
        val granted = permissions.filter { it.permission.isGranted(this) }
        val missing = permissions - granted.toSet()
        val saved = MissingPermissions.getSaved(device)
        val new = if (saved != null) missing.filter { it.permission !in saved } else emptyList()
        val notNew = missing - new.toSet()

        missingRecommended = missing.filter { it.section == DevicePermission.Section.RECOMMENDED }.map { it.permission }
        binding.devicePermissionsGrantRecommended.visibility =
            if (missingRecommended.isNotEmpty()) View.VISIBLE else View.GONE

        val items = buildList {
            addGroup(R.string.battery_status_new, new, granted = false)
            addGroup(
                R.string.permissions_section_recommended,
                notNew.filter { it.section == DevicePermission.Section.RECOMMENDED },
                granted = false
            )
            addGroup(
                R.string.permissions_section_optional,
                notNew.filter { it.section == DevicePermission.Section.OPTIONAL },
                granted = false
            )
            addGroup(R.string.permissions_section_granted, granted, granted = true)
        }

        val layoutManager = binding.devicePermissionsList.layoutManager
        val scrollState = layoutManager?.onSaveInstanceState()
        adapter.submitList(items) {
            if (scrollState != null) {
                layoutManager.onRestoreInstanceState(scrollState)
            }
        }
    }

    private fun MutableList<Item>.addGroup(
        @StringRes title: Int,
        permissions: List<DevicePermission>,
        granted: Boolean
    ) {
        if (permissions.isNotEmpty()) {
            add(Item.Header(title))
            permissions.forEach { add(Item.Row(it, granted)) }
        }
    }

    /**
     * Requests the next pending permissions. A permission that must be requested alone opens its own dialog or
     * settings page, and [onResume] continues with the next one. All remaining permissions are requested in a
     * single request at the end.
     */
    private fun requestPending() {
        pendingRequests.removeAll { it.isGranted(this) }
        val alone = pendingRequests.firstOrNull { it.isRequestedAlone }
        if (alone != null) {
            pendingRequests.remove(alone)
            alone.request(this)
            return
        }
        val together = pendingRequests.toList()
        pendingRequests.clear()
        GBPermission.requestTogether(this, together)
    }

    private fun close() {
        MissingPermissions.save(this, device)
        finish()
    }

    private sealed interface Item {
        data class Header(@StringRes val title: Int) : Item
        data class Row(val permission: DevicePermission, val granted: Boolean) : Item
    }

    private class PermissionsAdapter(
        private val onRequest: (GBPermission) -> Unit
    ) : ListAdapter<Item, RecyclerView.ViewHolder>(DIFF) {
        override fun getItemViewType(position: Int): Int {
            return when (getItem(position)) {
                is Item.Header -> VIEW_TYPE_HEADER
                is Item.Row -> VIEW_TYPE_ROW
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_HEADER -> HeaderHolder(ItemPermissionSectionBinding.inflate(inflater, parent, false))
                else -> RowHolder(FragmentWelcomePermissionRowBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = getItem(position)) {
                is Item.Header -> (holder as HeaderHolder).binding.permissionSectionTitle.setText(item.title)
                is Item.Row -> (holder as RowHolder).bind(item, onRequest)
            }
        }

        private class HeaderHolder(val binding: ItemPermissionSectionBinding) : RecyclerView.ViewHolder(binding.root)

        private class RowHolder(val binding: FragmentWelcomePermissionRowBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(row: Item.Row, onRequest: (GBPermission) -> Unit) {
                binding.permissionTitle.setText(row.permission.permission.title)
                binding.permissionSummary.setText(row.permission.rationale)
                binding.permissionCheck.visibility = if (row.granted) View.VISIBLE else View.GONE
                binding.permissionRequest.visibility = if (row.granted) View.INVISIBLE else View.VISIBLE
                binding.permissionRequest.isEnabled = !row.granted
                binding.permissionRequest.setOnClickListener { onRequest(row.permission.permission) }
            }
        }

        companion object {
            private const val VIEW_TYPE_HEADER = 0
            private const val VIEW_TYPE_ROW = 1

            private val DIFF = object : DiffUtil.ItemCallback<Item>() {
                override fun areItemsTheSame(oldItem: Item, newItem: Item): Boolean {
                    return when {
                        oldItem is Item.Header && newItem is Item.Header -> oldItem.title == newItem.title
                        oldItem is Item.Row && newItem is Item.Row ->
                            oldItem.permission.permission == newItem.permission.permission

                        else -> false
                    }
                }

                override fun areContentsTheSame(oldItem: Item, newItem: Item): Boolean = oldItem == newItem
            }
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(DevicePermissionsActivity::class.java)

        /**
         * Opens the screen after a device was paired, if the device is initialized and a permission is missing.
         */
        @JvmStatic
        fun startAfterPairing(context: Context, address: String) {
            val device = GBApplication.app().deviceManager.getDeviceByAddress(address)
            if (device == null || !device.isInitialized) {
                LOG.debug("Not showing permissions after pairing, {} is not initialized", address)
                return
            }
            if (MissingPermissions.of(context, device).isEmpty()) {
                return
            }
            context.startActivity(
                Intent(context, DevicePermissionsActivity::class.java).putExtra(GBDevice.EXTRA_DEVICE, device)
            )
        }
    }
}
