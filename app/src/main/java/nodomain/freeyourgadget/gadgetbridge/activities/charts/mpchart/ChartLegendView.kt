package nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSeries
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartTheme
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.SeriesStyle

/**
 * Legend row: square swatch for bars and ranges, dot for lines. Wraps when it doesn't fit.
 */
class ChartLegendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : FlexboxLayout(context, attrs) {
    private val theme = ChartTheme.from(context)

    init {
        flexWrap = FlexWrap.WRAP
        justifyContent = JustifyContent.CENTER
    }

    fun setSeries(series: List<ChartSeries>) {
        removeAllViews()
        for (chartSeries in series) {
            val style = chartSeries.style
            val color = when (style) {
                is SeriesStyle.Column -> style.color
                is SeriesStyle.Line -> style.color
                is SeriesStyle.Range -> style.color
                is SeriesStyle.Stages -> style.colors.firstOrNull() ?: continue
            }
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val size = dp(SWATCH_DP).toInt()
            item.addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(color)
                    if (style !is SeriesStyle.Line) {
                        cornerRadius = dp(SWATCH_CORNER_DP)
                    } else {
                        shape = GradientDrawable.OVAL
                    }
                }
            }, LinearLayout.LayoutParams(size, size))
            item.addView(TextView(context).apply {
                text = chartSeries.label
                setTextColor(theme.textColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, TEXT_SP)
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(LABEL_SPACING_DP).toInt()
            })
            val halfSpacing = (dp(ITEM_SPACING_DP) / 2f).toInt()
            val rowSpacing = dp(ROW_SPACING_DP).toInt()
            addView(item, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(halfSpacing, rowSpacing, halfSpacing, rowSpacing)
            })
        }
    }

    private fun dp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)

    companion object {
        private const val SWATCH_DP = 10f
        private const val SWATCH_CORNER_DP = 2f
        private const val TEXT_SP = 14f
        private const val LABEL_SPACING_DP = 8f
        private const val ITEM_SPACING_DP = 16f
        private const val ROW_SPACING_DP = 2f

        /**
         * A legend entry for a line that isn't a series, like an average limit line.
         */
        @JvmStatic
        fun lineItem(label: String, color: Int) = ChartSeries(label, label, emptyList(), SeriesStyle.Line(color))

        @JvmStatic
        fun squareItem(label: String, color: Int) = ChartSeries(label, label, emptyList(), SeriesStyle.Column(color))
    }
}
