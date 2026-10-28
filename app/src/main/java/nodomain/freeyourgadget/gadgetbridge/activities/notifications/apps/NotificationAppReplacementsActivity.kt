package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityNotificationAppReplacementsBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemNotificationAppReplacementBinding
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppReplacement
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRepository

/**
 * The ordered text replacements of the app in [EXTRA_PACKAGE_NAME].
 */
class NotificationAppReplacementsActivity : AbstractGBActivity() {
    private lateinit var appPackageName: String
    private lateinit var binding: ActivityNotificationAppReplacementsBinding
    private val adapter = ReplacementAdapter()
    private var dragging = false

    /**
     * The order of the rules after the moves of the current drag, or null when no row moved.
     */
    private var dragged: List<NotificationAppReplacement>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val extraPackageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (extraPackageName == null) {
            finish()
            return
        }
        appPackageName = extraPackageName

        binding = ActivityNotificationAppReplacementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.subtitle = intent.getCharSequenceExtra(EXTRA_TITLE)

        val touchHelper =
            ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0) {
                override fun onMove(
                    rv: RecyclerView,
                    holder: RecyclerView.ViewHolder,
                    target: RecyclerView.ViewHolder
                ): Boolean {
                    val from = holder.bindingAdapterPosition
                    val to = target.bindingAdapterPosition
                    if (from < 0 || to < 0) {
                        return false
                    }
                    // From the displayed list, a second call for the same move can occur before the diff is applied
                    val ordered = adapter.currentList.toMutableList()
                    ordered.add(to, ordered.removeAt(from))
                    dragged = ordered
                    adapter.submitList(ordered)
                    return true
                }

                override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                    // Only the drag handle moves the rows
                }

                override fun isLongPressDragEnabled(): Boolean = false

                override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        dragging = true
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    dragging = false
                    val ordered = dragged ?: return
                    dragged = null
                    NotificationAppEditor.update(appPackageName, ::showStored) { it.copy(replacements = ordered) }
                }
            })
        adapter.touchHelper = touchHelper
        binding.notificationAppReplacementList.layoutManager = LinearLayoutManager(this)
        binding.notificationAppReplacementList.adapter = adapter
        touchHelper.attachToRecyclerView(binding.notificationAppReplacementList)

        binding.fab.setOnClickListener { openEditor(NotificationAppReplacementActivity.NEW) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                NotificationAppEditor.configs.collect { configs ->
                    if (!dragging) {
                        showReplacements(configs[appPackageName]?.replacements.orEmpty())
                    }
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

    private fun showReplacements(stored: List<NotificationAppReplacement>) {
        adapter.submitList(stored)
        binding.notificationAppReplacementListEmpty.isVisible = stored.isEmpty()
    }

    /**
     * Displays the stored order again, after a move that was not saved.
     */
    private fun showStored() {
        if (!isDestroyed) {
            showReplacements(NotificationAppRepository.configs.value[appPackageName]?.replacements.orEmpty())
        }
    }

    private fun openEditor(index: Int) {
        startActivity(
            Intent(this, NotificationAppReplacementActivity::class.java)
                .putExtra(NotificationAppReplacementActivity.EXTRA_PACKAGE_NAME, appPackageName)
                .putExtra(NotificationAppReplacementActivity.EXTRA_TITLE, supportActionBar?.subtitle)
                .putExtra(NotificationAppReplacementActivity.EXTRA_INDEX, index)
        )
    }

    private fun confirmDelete(index: Int) {
        val replacement = adapter.currentList.getOrNull(index) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete)
            .setMessage(replacement.pattern)
            .setPositiveButton(R.string.delete) { _, _ ->
                NotificationAppEditor.update(appPackageName) { config ->
                    config.copy(replacements = config.replacements.filterIndexed { i, _ -> i != index })
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class ReplacementAdapter :
        ListAdapter<NotificationAppReplacement, ReplacementAdapter.ViewHolder>(DIFF) {
        var touchHelper: ItemTouchHelper? = null

        inner class ViewHolder(val binding: ItemNotificationAppReplacementBinding) :
            RecyclerView.ViewHolder(binding.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
            ViewHolder(
                ItemNotificationAppReplacementBinding.inflate(
                    LayoutInflater.from(parent.context),
                    parent,
                    false
                )
            )

        @SuppressLint("ClickableViewAccessibility")
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val replacement = getItem(position)
            val context = holder.itemView.context
            holder.binding.notificationAppReplacementPattern.text = replacement.pattern
            holder.binding.notificationAppReplacementReplacement.text = if (replacement.replacement.isEmpty()) {
                context.getString(R.string.notification_apps_remove_regular_expression_match)
            } else {
                context.getString(R.string.notification_apps_replace_text_with, replacement.replacement)
            }
            val details = mutableListOf(context.getString(replacement.target.label))
            if (replacement.ignoreCase) {
                details.add(context.getString(R.string.notification_apps_ignore_case))
            }
            if (!replacement.enabled) {
                details.add(context.getString(R.string.pref_button_action_disabled))
            }
            holder.binding.notificationAppReplacementDetails.text = details.joinToString(" · ")

            holder.binding.root.setOnClickListener {
                val index = holder.bindingAdapterPosition
                if (index != RecyclerView.NO_POSITION) {
                    openEditor(index)
                }
            }
            holder.binding.notificationAppReplacementDelete.setOnClickListener { confirmDelete(holder.bindingAdapterPosition) }
            holder.binding.notificationAppReplacementDragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchHelper?.startDrag(holder)
                }
                false
            }
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "packageName"
        const val EXTRA_TITLE = "title"

        private val DIFF = object : DiffUtil.ItemCallback<NotificationAppReplacement>() {
            override fun areItemsTheSame(
                oldItem: NotificationAppReplacement,
                newItem: NotificationAppReplacement
            ): Boolean =
                oldItem == newItem

            override fun areContentsTheSame(
                oldItem: NotificationAppReplacement,
                newItem: NotificationAppReplacement
            ): Boolean =
                oldItem == newItem
        }
    }
}
