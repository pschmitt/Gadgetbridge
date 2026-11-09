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
import nodomain.freeyourgadget.gadgetbridge.util.permissions.PermissionRequestQueue
import org.slf4j.LoggerFactory

class DevicePermissionsActivity : AbstractGBActivity() {
    private lateinit var binding: ActivityDevicePermissionsBinding
    private lateinit var device: GBDevice
    private val adapter = PermissionsAdapter { it.request(this) }

    private val requestQueue = PermissionRequestQueue()
    private var missingRecommended: List<GBPermission> = emptyList()

    /**
     * The title of the setting that opened the screen, and the permissions that the setting needs.
     */
    private var neededFor: String? = null
    private var needed: List<GBPermission> = emptyList()

    /**
     * The permissions that were granted at the last refresh. Null before the first refresh.
     */
    private var lastGranted: Set<GBPermission>? = null

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
        neededFor = intent.getStringExtra(EXTRA_NEEDED_FOR)
        val neededNames = intent.getStringArrayListExtra(EXTRA_NEEDED).orEmpty()
        needed = GBPermission.entries.filter { it.name in neededNames }

        binding = ActivityDevicePermissionsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.subtitle = device.aliasOrName

        binding.devicePermissionsList.layoutManager = LinearLayoutManager(this)
        binding.devicePermissionsList.adapter = adapter
        binding.devicePermissionsGrantRecommended.setOnClickListener {
            requestQueue.start(this, missingRecommended)
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
        requestQueue.resume(this)
    }

    override fun onSupportNavigateUp(): Boolean {
        close()
        return true
    }

    private fun refresh() {
        val listed = device.deviceCoordinator.getPermissions(device)
        // A setting might need a permission that is not in the list of the device (but that should never happen?)
        val unlisted = needed
            .filter { permission -> permission.isAvailable(this) && listed.none { it.permission == permission } }
            .map { DevicePermission(it, DevicePermission.Section.OPTIONAL) }
        val permissions = listed + unlisted
        val granted = permissions.filter { it.permission.isGranted(this) }
        sendNewGrants(granted.map { it.permission }.toSet())
        val missing = permissions - granted.toSet()
        val neededMissing = missing.filter { it.permission in needed }
        val saved = MissingPermissions.getSaved(device)
        val new = if (saved != null) {
            (missing - neededMissing.toSet()).filter { it.permission !in saved }
        } else {
            emptyList()
        }

        val notNew = missing - neededMissing.toSet() - new.toSet()

        missingRecommended = missing.filter { it.section == DevicePermission.Section.RECOMMENDED }.map { it.permission }
        binding.devicePermissionsGrantRecommended.visibility =
            if (missingRecommended.isNotEmpty()) View.VISIBLE else View.GONE

        val items = buildList {
            neededFor?.let {
                addGroup(getString(R.string.permissions_section_needed_for, it), neededMissing, granted = false)
            }
            addGroup(getString(R.string.battery_status_new), new, granted = false)
            addGroup(
                getString(R.string.permissions_section_recommended),
                notNew.filter { it.section == DevicePermission.Section.RECOMMENDED },
                granted = false
            )
            addGroup(
                getString(R.string.permissions_section_optional),
                notNew.filter { it.section == DevicePermission.Section.OPTIONAL },
                granted = false
            )
            addGroup(getString(R.string.permissions_section_granted), granted, granted = true)
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
        title: String,
        permissions: List<DevicePermission>,
        granted: Boolean
    ) {
        if (permissions.isNotEmpty()) {
            add(Item.Header(title))
            permissions.forEach { add(Item.Row(it, granted)) }
        }
    }

    /**
     * Broadcasts the new permissions granted by the user to the DeviceCommunicationService.
     */
    private fun sendNewGrants(granted: Set<GBPermission>) {
        val newGrants = granted - (lastGranted ?: granted)
        lastGranted = granted
        if (newGrants.isEmpty()) {
            return
        }
        LOG.info("Permissions granted for {}: {}", device.address, newGrants)
        LocalBroadcastManager.getInstance(this).sendBroadcast(
            Intent(GBPermission.ACTION_PERMISSIONS_GRANTED)
                .putStringArrayListExtra(GBPermission.EXTRA_PERMISSIONS, ArrayList(newGrants.map { it.name }))
        )
    }

    private fun close() {
        MissingPermissions.save(this, device)
        finish()
    }

    private sealed interface Item {
        data class Header(val title: String) : Item
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

        private const val EXTRA_NEEDED_FOR = "needed_for"
        private const val EXTRA_NEEDED = "needed"

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
            context.startActivity(newIntent(context, device))
        }

        /**
         * Open the screen with the [needed] permissions of the setting [neededFor] at the top of the list.
         */
        @JvmStatic
        @JvmOverloads
        fun newIntent(
            context: Context,
            device: GBDevice,
            neededFor: String? = null,
            needed: List<GBPermission> = emptyList(),
        ): Intent {
            return Intent(context, DevicePermissionsActivity::class.java)
                .putExtra(GBDevice.EXTRA_DEVICE, device)
                .putExtra(EXTRA_NEEDED_FOR, neededFor)
                .putStringArrayListExtra(EXTRA_NEEDED, ArrayList(needed.map { it.name }))
        }
    }
}
