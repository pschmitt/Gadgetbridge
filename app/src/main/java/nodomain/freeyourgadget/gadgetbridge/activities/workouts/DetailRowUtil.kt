package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Adds label / value rows separated by dividers.
 */
fun addDetailRows(container: LinearLayout, context: Context, rows: List<Pair<String, String>>) {
    for ((index, row) in rows.withIndex()) {
        container.addView(buildDetailRow(context, row.first, row.second))
        if (index < rows.size - 1) {
            container.addView(buildSeparator(context))
        }
    }
}

private fun buildDetailRow(context: Context, label: String, formattedValue: String): View {
    return LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        setPaddingRelative(dpToPx(context, 16), dpToPx(context, 10), dpToPx(context, 16), dpToPx(context, 10))

        addView(TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(GBApplication.getSecondaryTextColor(context))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(TextView(context).apply {
            text = formattedValue
            textSize = 16f
            setTextColor(GBApplication.getTextColor(context))
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        })
    }
}

private fun buildSeparator(context: Context): View {
    return View(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            (1 * context.resources.displayMetrics.density).toInt()
        )

        val typedValue = TypedValue()
        context.theme.resolveAttribute(R.attr.row_separator, typedValue, true)
        setBackgroundColor(ContextCompat.getColor(context, typedValue.resourceId))
    }
}

private fun dpToPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()
