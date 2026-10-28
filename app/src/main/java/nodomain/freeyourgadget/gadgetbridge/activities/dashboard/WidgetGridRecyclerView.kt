package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView

/**
 * A [RecyclerView] inside a scrolling parent. It only intercepts touch events while a widget is being dragged, so that
 * the parent keeps handling the scrolling.
 */
class WidgetGridRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : RecyclerView(context, attrs) {
    /**
     * Whether this view intercepts all touch events from its children (e.g. during a drag).
     */
    var interceptTouches: Boolean = false

    /**
     * The position of the last touch down, in the coordinates of this view.
     */
    var lastDownX = 0f
        private set
    var lastDownY = 0f
        private set

    /**
     * Whether a touch is in progress (between its down and its up, or cancel).
     */
    var isTouchDown = false
        private set

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        val heightMode = MeasureSpec.getMode(heightSpec)
        if (heightMode == MeasureSpec.EXACTLY) return
        val contentHeight = (layoutManager as? StackedGridLayoutManager)?.contentHeight ?: return
        setMeasuredDimension(
            measuredWidth,
            resolveSize(contentHeight + paddingTop + paddingBottom, heightSpec),
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastDownX = ev.x
                lastDownY = ev.y
                isTouchDown = true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> isTouchDown = false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean = interceptTouches
}
