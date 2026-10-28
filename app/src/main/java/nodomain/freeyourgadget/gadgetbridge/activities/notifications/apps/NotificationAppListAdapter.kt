package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemNotificationAppBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemNotificationAppDefaultsBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemNotificationAppSectionBinding
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppConfig
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationDefaultAction
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils

class NotificationAppListAdapter(
    private val scope: CoroutineScope,
    private val onDefaultActionClick: (NotificationDefaultAction) -> Unit,
    private val onMediaIgnoresListChange: (Boolean) -> Unit,
    private val onAppClick: (packageName: String) -> Unit,
) : ListAdapter<NotificationAppListItem, RecyclerView.ViewHolder>(DIFF) {
    private val icons = LruCache<String, Drawable>(ICON_CACHE_SIZE)

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is NotificationAppListItem.Defaults -> TYPE_DEFAULTS
        is NotificationAppListItem.AllApps -> TYPE_ALL_APPS
        is NotificationAppListItem.Section -> TYPE_SECTION
        is NotificationAppListItem.App -> TYPE_APP
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_DEFAULTS -> DefaultsViewHolder(ItemNotificationAppDefaultsBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionViewHolder(ItemNotificationAppSectionBinding.inflate(inflater, parent, false))
            else -> AppViewHolder(ItemNotificationAppBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is NotificationAppListItem.Defaults -> (holder as DefaultsViewHolder).bind(item)
            is NotificationAppListItem.AllApps -> (holder as AppViewHolder).bindAllApps(item)
            is NotificationAppListItem.Section -> (holder as SectionViewHolder).binding.notificationAppSectionTitle.setText(
                item.title
            )

            is NotificationAppListItem.App -> (holder as AppViewHolder).bindApp(item)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        (holder as? AppViewHolder)?.cancelIconLoad()
    }

    private inner class DefaultsViewHolder(val binding: ItemNotificationAppDefaultsBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: NotificationAppListItem.Defaults) {
            binding.notificationAppDefaultActionValue.setText(item.defaultAction.label)
            binding.notificationAppDefaultAction.setOnClickListener { onDefaultActionClick(item.defaultAction) }
            binding.notificationAppMediaIgnoresList.isChecked = item.mediaIgnoresList
            binding.notificationAppMediaIgnoresListRow.setOnClickListener {
                onMediaIgnoresListChange(!binding.notificationAppMediaIgnoresList.isChecked)
            }
        }
    }

    private class SectionViewHolder(val binding: ItemNotificationAppSectionBinding) :
        RecyclerView.ViewHolder(binding.root)

    private inner class AppViewHolder(val binding: ItemNotificationAppBinding) : RecyclerView.ViewHolder(binding.root) {
        private var iconJob: Job? = null

        fun bindAllApps(item: NotificationAppListItem.AllApps) {
            val context = binding.root.context
            cancelIconLoad()
            binding.notificationAppIcon.setImageResource(R.drawable.ic_action_manage_apps)
            binding.notificationAppLabel.setText(R.string.notification_apps_all_apps)
            binding.notificationAppPackage.setText(R.string.notification_apps_all_apps_summary)
            bindChips(binding.notificationAppChips, notificationAppChips(context, item.config, true))
            binding.root.setOnClickListener { onAppClick(NotificationAppConfig.ALL_APPS) }
        }

        fun bindApp(item: NotificationAppListItem.App) {
            val context = binding.root.context
            bindIcon(item)
            binding.notificationAppLabel.text = item.label
            binding.notificationAppPackage.text = item.packageName
            bindChips(binding.notificationAppChips, notificationAppChips(context, item.config, item.installed))
            binding.root.setOnClickListener { onAppClick(item.packageName) }
        }

        fun cancelIconLoad() {
            iconJob?.cancel()
            iconJob = null
        }

        private fun bindIcon(item: NotificationAppListItem.App) {
            cancelIconLoad()
            val context = binding.root.context
            val cached = icons[item.packageName]
            binding.notificationAppIcon.setImageDrawable(cached ?: context.packageManager.defaultActivityIcon)
            if (cached != null || !item.installed) {
                return
            }
            iconJob = scope.launch {
                val icon = withContext(Dispatchers.IO) {
                    NotificationUtils.getAppIcon(context, item.packageName)?.also { icons.put(item.packageName, it) }
                } ?: return@launch
                binding.notificationAppIcon.setImageDrawable(icon)
            }
        }

        private fun bindChips(group: ChipGroup, texts: List<String>) {
            group.isVisible = texts.isNotEmpty()
            while (group.childCount > texts.size) {
                group.removeViewAt(group.childCount - 1)
            }
            texts.forEachIndexed { i, chipText ->
                val chip = group.getChildAt(i) as? Chip
                    ?: (LayoutInflater.from(group.context)
                        .inflate(R.layout.item_notification_app_chip, group, false) as Chip).also {
                        group.addView(it)
                    }
                chip.text = chipText
            }
        }
    }

    companion object {
        private const val TYPE_DEFAULTS = 0
        private const val TYPE_ALL_APPS = 1
        private const val TYPE_SECTION = 2
        private const val TYPE_APP = 3

        private const val ICON_CACHE_SIZE = 200

        private val DIFF = object : DiffUtil.ItemCallback<NotificationAppListItem>() {
            override fun areItemsTheSame(oldItem: NotificationAppListItem, newItem: NotificationAppListItem): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(
                oldItem: NotificationAppListItem,
                newItem: NotificationAppListItem,
            ): Boolean =
                oldItem == newItem
        }
    }
}
