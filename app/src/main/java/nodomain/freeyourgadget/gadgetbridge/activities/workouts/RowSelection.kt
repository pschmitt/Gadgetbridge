package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R

private const val TINT_ALPHA = 36
private const val ANIMATION_MS = 120L

/**
 * Shows the selection of a list [row]: an accent tint behind it and the check badge on its [icon].
 */
class RowSelection(private val row: View, private val icon: ActivityIconView) {
    private val tint = ColorUtils.setAlphaComponent(MaterialColors.getColor(row, R.attr.accent_color), TINT_ALPHA)
    private var boundPosition = RecyclerView.NO_POSITION
    private var selected = false
    private var animator: ValueAnimator? = null

    fun bind(position: Int, selected: Boolean, inSelection: Boolean) {
        val animate = position == boundPosition && selected != this.selected
        boundPosition = position
        this.selected = selected
        icon.setChecked(selected, animate)

        animator?.cancel()
        animator = null
        val target = if (selected) tint else Color.TRANSPARENT
        if (animate) {
            val from = (row.background as? ColorDrawable)?.color ?: Color.TRANSPARENT
            animator = ValueAnimator.ofArgb(from, target).apply {
                duration = ANIMATION_MS
                addUpdateListener { row.setBackgroundColor(it.animatedValue as Int) }
                start()
            }
        } else {
            row.setBackgroundColor(target)
        }

        ViewCompat.setStateDescription(
            row,
            if (inSelection) row.context.getString(if (selected) R.string.list_item_selected else R.string.list_item_not_selected) else null
        )
    }
}
