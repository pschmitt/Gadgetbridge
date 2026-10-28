package nodomain.freeyourgadget.gadgetbridge.activities.install

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemInstallSessionBinding
import nodomain.freeyourgadget.gadgetbridge.install.InstallItem
import nodomain.freeyourgadget.gadgetbridge.install.ItemStatus

class InstallItemAdapter : ListAdapter<InstallItemAdapter.Row, InstallItemAdapter.ViewHolder>(DIFF) {

    /**
     * An item and its status. The status is null before the upload process starts.
     */
    data class Row(val item: InstallItem, val status: ItemStatus?)

    class ViewHolder(val binding: ItemInstallSessionBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemInstallSessionBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val row = getItem(position)
        val binding = holder.binding
        val context = binding.root.context

        val picture = row.item.picture
        if (picture != null) {
            binding.installItemIcon.setImageBitmap(picture)
        } else {
            binding.installItemIcon.setImageResource(row.item.icon)
        }
        binding.installItemName.text = row.item.displayName
        binding.installItemDetails.text = row.item.details
        binding.installItemDetails.visibility = if (row.item.details == null) View.GONE else View.VISIBLE

        val statusText = when (val status = row.status) {
            null -> null
            ItemStatus.Pending -> context.getString(R.string.agps_status_pending)
            is ItemStatus.Transferring -> context.getString(R.string.battery_percentage_str, status.percent.toString())
            ItemStatus.Installed -> context.getString(R.string.installation_successful)
            is ItemStatus.Failed -> status.reason ?: context.getString(R.string.work_info_status_failed)
            ItemStatus.Skipped -> context.getString(R.string.install_item_skipped)
        }
        binding.installItemStatus.text = statusText
        binding.installItemStatus.visibility = if (statusText == null) View.GONE else View.VISIBLE

        val transferring = row.status as? ItemStatus.Transferring
        binding.installItemProgress.visibility = if (transferring == null) View.GONE else View.VISIBLE
        if (transferring != null) {
            binding.installItemProgress.setProgressCompat(transferring.percent, true)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Row>() {
            override fun areItemsTheSame(oldItem: Row, newItem: Row) = oldItem.item === newItem.item

            override fun areContentsTheSame(oldItem: Row, newItem: Row) = oldItem == newItem
        }
    }
}
