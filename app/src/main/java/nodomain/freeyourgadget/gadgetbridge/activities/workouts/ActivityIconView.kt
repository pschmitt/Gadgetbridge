package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.animation.PathInterpolator
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import kotlin.math.roundToInt

/** The icon takes 55% of the circle, so it gets (1 - 0.55) / 2 of padding on every side. */
private const val ICON_INSET_FRACTION = 0.225f
private const val RING_DP = 2f
private const val BADGE_DP = 18f
private const val BADGE_BORDER_DP = 2f
private const val BADGE_OFFSET_DP = 3f
private const val CHECK_DP = 12f
private const val START_SCALE = 0.6f
private const val ANIMATION_MS = 120L

/**
 * An activity icon on a round background. Size it like any other ImageView (44dp in the workout
 * overview header, 40dp in the workout list rows) and set the icon with `setImageResource`.
 * [setChecked] adds an accent ring and a check badge that reaches past the bottom-right corner, so the
 * parent must not clip its children.
 */
class ActivityIconView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val accent = MaterialColors.getColor(this, R.attr.accent_color)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = RING_DP * density
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val check = ContextCompat.getDrawable(context, R.drawable.ic_done)?.mutate()?.apply {
        setTint(if (MaterialColors.isColorLight(accent)) Color.BLACK else Color.WHITE)
    }
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var checked = false

    init {
        setBackgroundResource(R.drawable.bg_activity_icon)
        imageTintList = ColorStateList.valueOf(MaterialColors.getColor(this, R.attr.textColorPrimary))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = (minOf(w, h) * ICON_INSET_FRACTION).toInt()
        setPadding(inset, inset, inset, inset)
    }

    fun setChecked(checked: Boolean, animate: Boolean) {
        if (checked == this.checked && animator == null) return
        this.checked = checked
        animator?.cancel()
        animator = null
        val target = if (checked) 1f else 0f
        if (!animate) {
            progress = target
            invalidateWithBadge()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = ANIMATION_MS
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidateWithBadge()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator === animation) animator = null
                }
            })
            start()
        }
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)
        if (progress <= 0f) return
        val alpha = (progress * 255).roundToInt()
        val scale = START_SCALE + (1 - START_SCALE) * progress
        val radius = minOf(width, height) / 2f

        canvas.save()
        canvas.scale(scale, scale, width / 2f, height / 2f)
        ringPaint.color = ColorUtils.setAlphaComponent(accent, alpha)
        canvas.drawCircle(width / 2f, height / 2f, radius - ringPaint.strokeWidth / 2, ringPaint)
        canvas.restore()

        val badgeRadius = BADGE_DP * density / 2
        val cx = width + BADGE_OFFSET_DP * density - badgeRadius
        val cy = height + BADGE_OFFSET_DP * density - badgeRadius
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        fillPaint.color = ColorUtils.setAlphaComponent(pageBackground(), alpha)
        canvas.drawCircle(cx, cy, badgeRadius, fillPaint)
        fillPaint.color = ColorUtils.setAlphaComponent(accent, alpha)
        canvas.drawCircle(cx, cy, badgeRadius - BADGE_BORDER_DP * density, fillPaint)
        check?.let {
            val half = CHECK_DP * density / 2
            it.setBounds((cx - half).roundToInt(), (cy - half).roundToInt(), (cx + half).roundToInt(), (cy + half).roundToInt())
            it.alpha = alpha
            it.draw(canvas)
        }
        canvas.restore()
    }

    /** The badge reaches outside this view, so the parent has to repaint that part too. */
    private fun invalidateWithBadge() {
        invalidate()
        (parent as? View)?.invalidate()
    }

    private fun pageBackground(): Int {
        val value = TypedValue()
        if (context.theme.resolveAttribute(android.R.attr.windowBackground, value, true) &&
            value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT
        ) {
            return value.data
        }
        return MaterialColors.getColor(this, android.R.attr.colorBackground)
    }
}
