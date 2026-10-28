package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.view.LayoutInflater
import android.widget.FrameLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.EmptySharedPreferences
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.GridRect
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import nodomain.freeyourgadget.gadgetbridge.widgets.bindUnsafe
import kotlin.math.roundToInt

private const val PREVIEW_INSTANCE_ID = "preview"
private const val SELECTED_STROKE_DP = 3f
private const val MAX_REORDERED_VARIANTS = 6
private const val AUTOMATIC_HEIGHT_DP = 84

/**
 * Populates [grid] with a card for each variant of [widget], with its preview data and its default settings.
 * The widgets are stacked as on the dashboard, in the order that takes the least height. The card of
 * [selectedVariantId] has a border in the primary color. A tap on a card calls [onClick] with the variant that was clicked.
 * <p>
 * With [onAutomatic], a card for the automatic variant is above the variants. A tap on it calls [onAutomatic]. It
 * has the border when [automaticSelected].
 */
fun showVariantPreviews(
    grid: WidgetGridLayout,
    widget: GBWidget<*>,
    selectedVariantId: String?,
    onAutomatic: (() -> Unit)? = null,
    automaticSelected: Boolean = false,
    onClick: (WidgetVariant) -> Unit,
) {
    val context = grid.context
    val inflater = LayoutInflater.from(context)
    val density = context.resources.displayMetrics.density
    grid.columns = WidgetGrid.columnsForWidth(context.resources.configuration.screenWidthDp)
    grid.removeAllViews()

    if (onAutomatic != null) {
        val card = inflater.inflate(R.layout.dashboard_widget_card, grid, false) as MaterialCardView
        applyCardStyle(card, true)
        val slot = card.findViewById<FrameLayout>(R.id.dashboard_widget_content)
        slot.addView(inflater.inflate(R.layout.dashboard_widget_automatic, slot, false))
        if (automaticSelected) {
            markSelected(card)
        }
        card.contentDescription = context.getString(R.string.automatic)
        card.setOnClickListener { onAutomatic() }

        val lp = card.layoutParams as WidgetGridLayout.LayoutParams
        lp.cellX = 0
        lp.cellWidth = grid.columns
        lp.childHeight = (AUTOMATIC_HEIGHT_DP * density).roundToInt()
        grid.addView(card)
    }

    val data = widget.previewData()
    val instance = WidgetInstance(PREVIEW_INSTANCE_ID, widget.id)
    val variants = compactOrder(grid.columns, widget.variants)
    for ((variant, rect) in variants.zip(place(grid.columns, variants))) {
        val card = inflater.inflate(R.layout.dashboard_widget_card, grid, false) as MaterialCardView
        applyCardStyle(card, true)
        val slot = card.findViewById<FrameLayout>(R.id.dashboard_widget_content)
        val content = widget.createView(inflater, slot, variant)
        slot.addView(content)
        val config = WidgetConfig(instance, Prefs(EmptySharedPreferences), true, emptySet(), variant)
        widget.bindUnsafe(content, variant, config, data)

        if (variant.id == selectedVariantId) {
            markSelected(card)
        }
        card.contentDescription = context.getString(variant.name)
        card.setOnClickListener { onClick(variant) }

        val lp = card.layoutParams as WidgetGridLayout.LayoutParams
        lp.cellX = rect.x
        lp.cellWidth = variant.width
        lp.childHeight = (variant.heightDp * density).roundToInt()
        grid.addView(card)
    }
}

private fun markSelected(card: MaterialCardView) {
    card.isSelected = true
    card.strokeColor = MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary)
    card.strokeWidth = (SELECTED_STROKE_DP * card.resources.displayMetrics.density).roundToInt()
}

/**
 * The order of [variants] that stacks them to the least height. Of the orders with the same height, the first one in
 * [permutations].
 */
private fun compactOrder(columns: Int, variants: List<WidgetVariant>): List<WidgetVariant> {
    if (variants.size > MAX_REORDERED_VARIANTS) return variants
    return permutations(variants).minBy { order -> place(columns, order).maxOfOrNull { it.y + it.height } ?: 0 }
}

/**
 * Stacks each variant in the first column where it goes highest. Returns the rectangles in the order of [variants].
 */
private fun place(columns: Int, variants: List<WidgetVariant>): List<GridRect> {
    val rects = mutableListOf<GridRect>()
    for (variant in variants) {
        val x = WidgetGrid.firstFreeColumn(columns, WidgetGrid.stack(columns, rects), variant.width)
        rects += GridRect(x, 0, variant.width, variant.heightDp)
    }
    return WidgetGrid.stack(columns, rects)
}

/**
 * All the orders of [items], starting with the given order.
 */
private fun <T> permutations(items: List<T>): Sequence<List<T>> = sequence {
    if (items.size <= 1) {
        yield(items)
        return@sequence
    }
    for (i in items.indices) {
        val rest = items.filterIndexed { index, _ -> index != i }
        for (order in permutations(rest)) {
            yield(listOf(items[i]) + order)
        }
    }
}
