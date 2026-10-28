package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.doOnNextLayout
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardDefaults
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import java.text.Collator

/**
 * Lists all widgets in groups:
 * - new widgets of the device in [EXTRA_NEW_DEVICE]
 * - supported by a dashboard device
 * - supported only by a device that is not enabled for the dashboard
 * - supported by no device
 *
 * - A tap on a widget expands section with previews for all its variants.
 * - A tap on a preview returns the widget and the variant.
 *
 * With [EXTRA_LAUNCHER], the list has only the widgets that support the launcher, and the previews start with the
 * automatic variant. A tap on it returns the widget without a variant.
 */
class WidgetPickerActivity : AbstractGBActivity() {
    private lateinit var widgets: List<GBWidget<*>>
    private lateinit var deviceCounts: List<Int>
    private lateinit var groups: List<SupportGroup>
    private lateinit var recyclerView: RecyclerView
    private lateinit var layoutManager: LinearLayoutManager
    private var expanded = RecyclerView.NO_POSITION
    private var forLauncher = false
    private var newDevice: GBDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_picker)

        forLauncher = intent.getBooleanExtra(EXTRA_LAUNCHER, false)
        newDevice = intent.getStringExtra(EXTRA_NEW_DEVICE)?.let {
            GBApplication.app().deviceManager.getDeviceByAddress(it)
        }
        val newTypes = newDevice?.let { device -> DashboardDefaults.newWidgets(device).map { it.id }.toSet() }.orEmpty()
        val collator = Collator.getInstance()
        val dashboardDevices = WidgetRegistry.dashboardDevices()
        val allDevices = GBApplication.app().deviceManager.devices
        val entries = WidgetRegistry.all()
            .filter { !forLauncher || it.supportsLauncher }
            .map { widget ->
                val dashboardCount = dashboardDevices.count { widget.isSupportedBy(it) }
                val allCount = allDevices.count { widget.isSupportedBy(it) }
                when {
                    widget.id in newTypes -> Triple(widget, allCount, SupportGroup.NEW)
                    dashboardCount > 0 -> Triple(widget, dashboardCount, SupportGroup.DASHBOARD)
                    allCount > 0 -> Triple(widget, allCount, SupportGroup.OTHER_DEVICES)
                    else -> Triple(widget, 0, SupportGroup.NONE)
                }
            }
            .sortedWith(
                compareBy<Triple<GBWidget<*>, Int, SupportGroup>> { it.third }
                    .thenComparator { a, b -> collator.compare(a.first.displayName(this), b.first.displayName(this)) }
            )
        widgets = entries.map { it.first }
        deviceCounts = entries.map { it.second }
        groups = entries.map { it.third }

        recyclerView = findViewById(R.id.widget_picker_list)
        layoutManager = LinearLayoutManager(this)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = WidgetAdapter()
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recyclerView.addItemDecoration(DividerItemDecoration(this, LinearLayoutManager.VERTICAL))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun select(widget: GBWidget<*>, variant: WidgetVariant?) {
        val result = Intent()
        result.putExtra(EXTRA_TYPE_ID, widget.id)
        result.putExtra(EXTRA_VARIANT_ID, variant?.id)
        setResult(RESULT_OK, result)
        finish()
    }

    private inner class WidgetAdapter : RecyclerView.Adapter<WidgetAdapter.ViewHolder>() {
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val section: TextView = view.findViewById(R.id.widget_picker_section)
            val header: View = view.findViewById(R.id.widget_picker_header)
            val icon: ImageView = view.findViewById(R.id.widget_picker_icon)
            val name: TextView = view.findViewById(R.id.widget_picker_name)
            val variants: TextView = view.findViewById(R.id.widget_picker_variants)
            val previews: WidgetGridLayout = view.findViewById(R.id.widget_picker_previews)
        }

        override fun getItemCount(): Int = widgets.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_widget_picker, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val widget = widgets[position]
            val context = holder.itemView.context
            val deviceCount = deviceCounts[position]
            val group = groups[position]
            val firstInGroup = position == 0 || groups[position - 1] != group
            holder.section.isVisible = firstInGroup && (group != SupportGroup.DASHBOARD || newDevice != null)
            when (group) {
                SupportGroup.NEW -> holder.section.text =
                    context.getString(R.string.widgets_picker_new, newDevice?.aliasOrName)
                SupportGroup.DASHBOARD -> holder.section.setText(R.string.widgets_picker_dashboard_devices)
                SupportGroup.OTHER_DEVICES -> holder.section.setText(R.string.widgets_picker_other_devices)
                SupportGroup.NONE -> holder.section.setText(R.string.widgets_picker_unsupported)
            }
            holder.icon.setImageResource(widget.icon)
            holder.name.text = widget.displayName(context)
            holder.variants.text = context.getString(
                R.string.widgets_picker_summary,
                context.resources.getQuantityString(
                    R.plurals.widgets_variant_count,
                    widget.variants.size,
                    widget.variants.size
                ),
                context.resources.getQuantityString(R.plurals.widgets_device_count, deviceCount, deviceCount)
            )

            val isExpanded = position == expanded
            holder.previews.isVisible = isExpanded
            if (isExpanded) {
                showVariantPreviews(
                    holder.previews,
                    widget,
                    null,
                    onClick = { select(widget, it) },
                    onAutomatic = if (forLauncher) {
                        { select(widget, null) }
                    } else {
                        null
                    },
                )
            } else {
                holder.previews.removeAllViews()
            }

            holder.header.setOnClickListener {
                val previous = expanded
                val clicked = holder.bindingAdapterPosition
                val top = holder.itemView.top
                expanded = if (clicked == previous) RecyclerView.NO_POSITION else clicked
                if (previous != RecyclerView.NO_POSITION) notifyItemChanged(previous)
                if (expanded != RecyclerView.NO_POSITION) notifyItemChanged(expanded)
                // The row that collapses above would move the clicked row up
                layoutManager.scrollToPositionWithOffset(clicked, top)
                if (expanded != RecyclerView.NO_POSITION) {
                    recyclerView.doOnNextLayout { scrollIntoView(clicked) }
                }
            }
        }
    }

    /**
     * Scrolls up until the bottom of the row is visible, but not past the top of the row.
     */
    private fun scrollIntoView(position: Int) {
        val view = layoutManager.findViewByPosition(position) ?: return
        val overflow = view.bottom - (recyclerView.height - recyclerView.paddingBottom)
        val headroom = view.top - recyclerView.paddingTop
        val dy = minOf(overflow, headroom)
        if (dy > 0) {
            recyclerView.smoothScrollBy(0, dy)
        }
    }

    private enum class SupportGroup {
        NEW,
        DASHBOARD,
        OTHER_DEVICES,
        NONE,
    }

    companion object {
        const val EXTRA_TYPE_ID = "widget_type_id"
        const val EXTRA_VARIANT_ID = "widget_variant_id"
        const val EXTRA_LAUNCHER = "widget_launcher"
        const val EXTRA_NEW_DEVICE = "widget_new_device"
    }
}
