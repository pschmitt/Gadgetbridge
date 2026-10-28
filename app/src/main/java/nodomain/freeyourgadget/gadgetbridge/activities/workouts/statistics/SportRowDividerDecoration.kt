package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.roundToInt

/**
 * Separator between rows.
 */
class SportRowDividerDecoration(context: Context) : RecyclerView.ItemDecoration() {
    private val thickness = context.resources.displayMetrics.density.coerceAtLeast(1f)
    private val paint = Paint().apply {
        color = MaterialColors.getColor(context, R.attr.row_separator, "SportRowDividerDecoration")
        style = Paint.Style.FILL
    }

    override fun onDraw(canvas: Canvas, parent: RecyclerView, state: RecyclerView.State) {
        val lastPosition = state.itemCount - 1
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (parent.getChildAdapterPosition(child) == lastPosition) {
                continue
            }
            val bottom = child.bottom + child.translationY.roundToInt()
            canvas.drawRect(
                child.left.toFloat(),
                bottom - thickness,
                child.right.toFloat(),
                bottom.toFloat(),
                paint,
            )
        }
    }
}
