package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Shows the dashboard notices above the grid.
 */
class DashboardNoticesController(
    private val context: Context,
    private val container: LinearLayout,
    private val onRestoreClassicDesign: () -> Unit,
    private val onEdit: () -> Unit,
    private val onAddWidgets: (deviceAddress: String) -> Unit,
    private val onDismissMigration: () -> Unit,
    private val onDismissNewWidgets: () -> Unit,
) {
    private val inflater = LayoutInflater.from(context)
    private var notices: List<DashboardNotice> = emptyList()

    fun submit(notices: List<DashboardNotice>) {
        if (this.notices == notices) return
        this.notices = notices

        container.removeAllViews()
        for (notice in notices) {
            when (notice) {
                DashboardNotice.Migration -> addNotice(
                    context.getString(R.string.dashboard_notice_new_design_title),
                    context.getString(R.string.dashboard_notice_new_design_text),
                    R.string.dashboard_notice_restore_design to onRestoreClassicDesign,
                    R.string.dashboard_edit to onEdit,
                    onDismissMigration,
                )

                is DashboardNotice.NewWidgets -> addNotice(
                    null,
                    context.getString(R.string.dashboard_notice_new_widgets, notice.deviceName),
                    null,
                    R.string.dashboard_notice_add_widgets to { onAddWidgets(notice.deviceAddress) },
                    onDismissNewWidgets,
                )
            }
        }
    }

    private fun addNotice(
        title: String?,
        text: String,
        secondary: Pair<Int, () -> Unit>?,
        primary: Pair<Int, () -> Unit>,
        dismiss: () -> Unit,
    ) {
        val notice = inflater.inflate(R.layout.dashboard_notice, container, false)
        notice.findViewById<TextView>(R.id.dashboard_notice_title).apply {
            this.text = title
            isVisible = title != null
        }
        notice.findViewById<TextView>(R.id.dashboard_notice_text).text = text
        notice.findViewById<View>(R.id.dashboard_notice_dismiss).setOnClickListener {
            dismiss()
        }
        notice.findViewById<Button>(R.id.dashboard_notice_secondary).apply {
            isVisible = secondary != null
            secondary?.let { (label, action) ->
                setText(label)
                setOnClickListener { action() }
            }
        }
        notice.findViewById<Button>(R.id.dashboard_notice_primary).apply {
            setText(primary.first)
            setOnClickListener { primary.second() }
        }
        container.addView(notice)
    }
}
