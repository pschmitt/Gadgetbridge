package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityNotificationAppFilterEntriesBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemNotificationAppFilterEntryBinding
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppConfig
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppFilterSyntax

/**
 * The filter entries of the app in [EXTRA_PACKAGE_NAME].
 */
class NotificationAppFilterEntriesActivity : AbstractGBActivity() {
    private lateinit var appPackageName: String
    private lateinit var binding: ActivityNotificationAppFilterEntriesBinding
    private val adapter = EntryAdapter()
    private var syntax = NotificationAppFilterSyntax.WORDS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extraPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (extraPackageName == null) {
            finish()
            return
        }
        appPackageName = extraPackageName

        binding = ActivityNotificationAppFilterEntriesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.subtitle = intent.getCharSequenceExtra(EXTRA_TITLE)

        binding.notificationAppFilterEntryList.layoutManager = LinearLayoutManager(this)
        binding.notificationAppFilterEntryList.adapter = adapter
        binding.fab.setOnClickListener { openEditor(NotificationAppFilterEntryDialog.NEW, "") }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationAppEditor.configs.collect { configs ->
                    show(configs[appPackageName] ?: NotificationAppConfig(appPackageName))
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun show(config: NotificationAppConfig) {
        if (syntax != config.filter.syntax) {
            syntax = config.filter.syntax
            adapter.notifyItemRangeChanged(0, adapter.itemCount)
        }
        setTitle(syntax.entriesTitle)
        binding.notificationAppFilterEntryListEmpty.setText(syntax.entriesEmpty)
        adapter.submitList(config.filter.entries)
        binding.notificationAppFilterEntryListEmpty.isVisible = config.filter.entries.isEmpty()
    }

    private fun openEditor(index: Int, entry: String) {
        NotificationAppFilterEntryDialog.newInstance(appPackageName, syntax, index, entry)
            .show(supportFragmentManager, NotificationAppFilterEntryDialog.TAG)
    }

    private fun confirmDelete(index: Int) {
        val entry = adapter.currentList.getOrNull(index) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(entry)
            .setPositiveButton(R.string.delete) { _, _ ->
                NotificationAppEditor.update(appPackageName) { config ->
                    config.copy(filter = config.filter.copy(entries = config.filter.entries.filterIndexed { i, _ -> i != index }))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class EntryAdapter : ListAdapter<String, EntryAdapter.ViewHolder>(DIFF) {
        inner class ViewHolder(val binding: ItemNotificationAppFilterEntryBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                ItemNotificationAppFilterEntryBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val entry = getItem(position)
            holder.binding.notificationAppFilterEntry.text = entry
            holder.binding.notificationAppFilterEntry.typeface =
                if (syntax == NotificationAppFilterSyntax.REGEX) Typeface.MONOSPACE else Typeface.DEFAULT
            holder.binding.root.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) {
                    openEditor(index, getItem(index))
                }
            }
            holder.binding.notificationAppFilterEntryDelete.setOnClickListener { confirmDelete(holder.bindingAdapterPosition) }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_TITLE = "title"

        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String): Boolean = oldItem == newItem
        }
    }
}
