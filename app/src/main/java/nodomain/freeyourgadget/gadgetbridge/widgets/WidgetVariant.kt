package nodomain.freeyourgadget.gadgetbridge.widgets

import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * One presentation of a [GBWidget] at one size: a width in columns and a height. All variants of a widget show the same
 * data.
 */
data class WidgetVariant(
    val id: String,
    @StringRes val name: Int,
    val width: Int,
    val heightDp: Int,
) {
    companion object {
        @JvmField
        val GAUGE = WidgetVariant("gauge", R.string.widget_variant_gauge, 3, GAUGE_HEIGHT_DP)

        @JvmField
        val NUMBER = WidgetVariant("number", R.string.widget_variant_number, 3, 142)

        @JvmField
        val TALL = WidgetVariant("tall", R.string.widget_variant_tall, 3, 219)

        @JvmField
        val WIDE = WidgetVariant("wide", R.string.widget_variant_wide, 6, 142)

        /**
         * The large tile of a single metric.
         */
        @JvmField
        val METRIC_LARGE = WidgetVariant("large", R.string.widget_variant_large, 6, 219)

        /**
         * The small size of the day and goals overviews.
         */
        @JvmField
        val SMALL = WidgetVariant("small", R.string.widget_variant_small, 6, 142)

        /**
         * The single-column size of the day and goals overviews.
         */
        @JvmField
        val OVERVIEW_TALL = WidgetVariant("tall", R.string.widget_variant_tall, 3, 296)

        /**
         * The large size of the day and goals overviews.
         */
        @JvmField
        val LARGE = WidgetVariant("large", R.string.widget_variant_large, 6, 373)

        /**
         * The height of the gauge tile: the padding, the header and the gauge at its maximum width.
         */
        const val GAUGE_HEIGHT_DP = 127
    }
}

/**
 * The variant with [variantId], or the default variant if there is none.
 */
fun GBWidget<*>.variant(variantId: String?): WidgetVariant =
    variants.find { it.id == variantId } ?: variants.first()
