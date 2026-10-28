package nodomain.freeyourgadget.gadgetbridge.activities.gallery

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs

/**
 * An ImageView that displays the drawable at fit-center, with pinch zoom, double-tap zoom and pan.
 * A single tap is a click.
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : AppCompatImageView(context, attrs, defStyleAttr) {
    // The fit-center transformation of the drawable
    private val baseMatrix = Matrix()

    // The zoom and the pan, in view coordinates
    private val zoomMatrix = Matrix()

    private val drawMatrix = Matrix()
    private val imageRect = RectF()
    private val viewRect = RectF()
    private val values = FloatArray(9)
    private var animator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                if (!hasImage()) {
                    return false
                }
                val scale = currentScale()
                val target = (scale * detector.scaleFactor).coerceIn(MIN_GESTURE_SCALE, MAX_GESTURE_SCALE)
                zoomMatrix.postScale(target / scale, target / scale, detector.focusX, detector.focusY)
                applyMatrix()
                return true
            }

            override fun onScaleEnd(detector: ScaleGestureDetector) {
                val scale = currentScale()
                val target = scale.coerceIn(1f, MAX_SCALE)
                if (target != scale) {
                    animateScale(target, detector.focusX, detector.focusY)
                }
            }
        }
    ).apply {
        // A double tap zooms, so a double tap and drag must not zoom too
        isQuickScaleEnabled = false
    }

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean = performClick()

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (!hasImage()) {
                    return false
                }
                animateScale(if (currentScale() > 1f + SCALE_EPSILON) 1f else DOUBLE_TAP_SCALE, e.x, e.y)
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                // A one-finger horizontal drag goes to the pager when the image cannot pan further in its direction
                if (e2.pointerCount == 1 && abs(distanceX) > abs(distanceY)) {
                    parent?.requestDisallowInterceptTouchEvent(canScrollHorizontally(if (distanceX > 0) 1 else -1))
                }
                if (!hasImage()) {
                    return false
                }
                zoomMatrix.postTranslate(-distanceX, -distanceY)
                applyMatrix()
                return true
            }
        }
    )

    // ImageView can call setImageDrawable from its constructor, before the fields of this class are set
    private var initialized = false

    init {
        scaleType = ScaleType.MATRIX
        initialized = true
    }

    /**
     * Displays the drawable at fit-center again.
     */
    fun resetZoom() {
        animator?.cancel()
        zoomMatrix.reset()
        applyMatrix()
    }

    override fun setImageDrawable(drawable: Drawable?) {
        val previous = this.drawable
        super.setImageDrawable(drawable)
        if (!initialized) {
            return
        }
        // A new drawable with the same aspect ratio keeps the zoom, because the fit-center rectangle does not change
        if (!hasImage() || !sameAspectRatio(previous, this.drawable)) {
            animator?.cancel()
            zoomMatrix.reset()
        }
        updateBaseMatrix()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // The image point at the old center, read before updateBaseMatrix replaces the old base matrix
        val focus = floatArrayOf(oldw / 2f, oldh / 2f)
        val inverse = Matrix()
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(zoomMatrix)
        val keepFocus = oldw > 0 && oldh > 0 && hasImage() && drawMatrix.invert(inverse)
        inverse.mapPoints(focus)
        updateBaseMatrix()
        if (keepFocus) {
            baseMatrix.mapPoints(focus)
            val scale = currentScale()
            zoomMatrix.setScale(scale, scale)
            zoomMatrix.postTranslate(w / 2f - scale * focus[0], h / 2f - scale * focus[1])
            applyMatrix()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // end() of an animator that is not running applies its last value again
                animator?.takeIf { it.isRunning }?.end()
                // The pager gets the drag only when onScroll allows it
                parent?.requestDisallowInterceptTouchEvent(true)
            }

            MotionEvent.ACTION_POINTER_DOWN -> parent?.requestDisallowInterceptTouchEvent(true)
        }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun canScrollHorizontally(direction: Int): Boolean {
        val rect = displayRect() ?: return false
        return if (direction < 0) rect.left < -EDGE_TOLERANCE else rect.right > width + EDGE_TOLERANCE
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }

    private fun hasImage(): Boolean {
        val drawable = drawable ?: return false
        return drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0 && width > 0 && height > 0
    }

    private fun sameAspectRatio(a: Drawable?, b: Drawable?): Boolean {
        if (a == null || b == null || a.intrinsicHeight <= 0 || b.intrinsicWidth <= 0) {
            return false
        }
        val ratio = a.intrinsicWidth.toFloat() * b.intrinsicHeight / (a.intrinsicHeight.toFloat() * b.intrinsicWidth)
        return abs(ratio - 1f) < ASPECT_TOLERANCE
    }

    private fun currentScale(): Float {
        zoomMatrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    private fun updateBaseMatrix() {
        baseMatrix.reset()
        if (hasImage()) {
            val drawable = drawable
            imageRect.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
            viewRect.set(0f, 0f, width.toFloat(), height.toFloat())
            baseMatrix.setRectToRect(imageRect, viewRect, Matrix.ScaleToFit.CENTER)
        }
        applyMatrix()
    }

    /**
     * Returns the rectangle of the image in view coordinates, or null when there is no image.
     */
    private fun displayRect(): RectF? {
        if (!hasImage()) {
            return null
        }
        val drawable = drawable
        imageRect.set(0f, 0f, drawable.intrinsicWidth.toFloat(), drawable.intrinsicHeight.toFloat())
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(zoomMatrix)
        drawMatrix.mapRect(imageRect)
        return imageRect
    }

    /**
     * Moves the image so that its edges do not leave the view, and centers it on an axis where it is
     * smaller than the view. Then sets the image matrix.
     */
    private fun applyMatrix() {
        displayRect()?.let { rect ->
            zoomMatrix.postTranslate(
                clampOffset(rect.left, rect.right, width.toFloat()),
                clampOffset(rect.top, rect.bottom, height.toFloat())
            )
        }
        drawMatrix.set(baseMatrix)
        drawMatrix.postConcat(zoomMatrix)
        imageMatrix = drawMatrix
    }

    private fun clampOffset(start: Float, end: Float, size: Float): Float = when {
        end - start <= size -> (size - (end - start)) / 2f - start
        start > 0f -> -start
        end < size -> size - end
        else -> 0f
    }

    private fun animateScale(target: Float, focusX: Float, focusY: Float) {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(currentScale(), target).apply {
            duration = ANIMATION_DURATION
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val factor = it.animatedValue as Float / currentScale()
                zoomMatrix.postScale(factor, factor, focusX, focusY)
                applyMatrix()
            }
            start()
        }
    }

    companion object {
        private const val MAX_SCALE = 3f
        private const val DOUBLE_TAP_SCALE = 2.5f

        // The scale limits during a pinch. The scale goes back into 1..MAX_SCALE when the pinch ends.
        private const val MIN_GESTURE_SCALE = 0.8f
        private const val MAX_GESTURE_SCALE = 3.5f

        private const val SCALE_EPSILON = 0.01f
        private const val EDGE_TOLERANCE = 1f
        private const val ASPECT_TOLERANCE = 0.01f
        private const val ANIMATION_DURATION = 200L
    }
}
