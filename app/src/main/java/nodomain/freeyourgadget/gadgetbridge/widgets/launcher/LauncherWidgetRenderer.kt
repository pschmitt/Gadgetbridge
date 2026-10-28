package nodomain.freeyourgadget.gadgetbridge.widgets.launcher

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.createBitmap
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.LauncherWidgetClickActivity
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.applyCardStyle
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import nodomain.freeyourgadget.gadgetbridge.widgets.bindUnsafe
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * What a launcher widget shows.
 */
sealed interface LauncherContent {
    data object Loading : LauncherContent
    data object NoDevices : LauncherContent
    data class Ready(val data: Any?) : LauncherContent
}

/**
 * Draws a dashboard tile into a bitmap, for the [RemoteViews] of a launcher widget.
 * <p>
 * The tile is laid out at the size that it has on the dashboard of this phone. The card is extended to the aspect
 * ratio of the launcher widget, and the tile is centered in it.
 */
object LauncherWidgetRenderer {
    private const val GRID_PADDING_DP = 10
    private const val CARD_MARGIN_DP = 6
    private const val CORNER_RADIUS_DP = 16f
    private const val NO_DEVICES_PADDING_DP = 12

    const val KEY_CARD = "launcher_widget_card"
    const val KEY_THEME = "launcher_widget_theme"

    const val THEME_APP = "app"
    const val THEME_SYSTEM = "system"
    const val THEME_LIGHT = "light"
    const val THEME_DARK = "dark"

    /**
     * The application context with the app theme, in the dark or light version from [KEY_THEME] in [config].
     */
    private fun themedContext(context: Context, config: WidgetConfig): Context {
        val dark = when (config.prefs.getString(KEY_THEME, THEME_APP)) {
            THEME_SYSTEM -> (Resources.getSystem().configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

            THEME_LIGHT -> false
            THEME_DARK -> true
            else -> GBApplication.isDarkThemeEnabled()
        }
        val themed = ContextThemeWrapper(
            context.applicationContext,
            AbstractGBActivity.getThemeResource(AbstractGBActivity.NONE, dark),
        )
        val configuration = Configuration(context.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        themed.applyOverrideConfiguration(configuration)
        AbstractGBActivity.applyThemeOverlays(themed.theme, dark)
        return themed
    }

    /**
     * The variant that the user selected, or else the variant with the best [score] in the launcher widget.
     */
    fun variantFor(
        context: Context,
        appWidgetId: Int,
        widget: GBWidget<*>,
        launcherWidget: LauncherWidget
    ): WidgetVariant {
        launcherWidget.variantId?.let { id -> widget.variants.find { it.id == id }?.let { return it } }
        val box = boxSizeDp(AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId))
            ?: return widget.variants.first()
        return widget.variants.maxBy { score(context, it, box) }
    }

    fun render(
        context: Context,
        appWidgetId: Int,
        widget: GBWidget<*>,
        variant: WidgetVariant,
        config: WidgetConfig,
        content: LauncherContent,
    ): RemoteViews {
        val themed = themedContext(context, config)
        val density = themed.resources.displayMetrics.density
        val natural = naturalSizeDp(themed, variant)
        val box = boxSizeDp(AppWidgetManager.getInstance(context).getAppWidgetOptions(appWidgetId)) ?: natural

        val boxAspect = box.width / box.height
        val cardWidth = (max(natural.width, natural.height * boxAspect) * density).roundToInt()
        val cardHeight = (max(natural.height, natural.width / boxAspect) * density).roundToInt()
        val bitmapWidth = (box.width * density).roundToInt()
        val bitmapHeight = (box.height * density).roundToInt()
        val scale = bitmapWidth.toFloat() / cardWidth

        val radius = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.resources.getDimension(android.R.dimen.system_app_widget_background_radius)
        } else {
            CORNER_RADIUS_DP * density
        }

        val inflater = LayoutInflater.from(themed)
        val card = inflater.inflate(R.layout.dashboard_widget_card, FrameLayout(themed), false) as MaterialCardView
        applyCardStyle(card, config.prefs.getBoolean(KEY_CARD, true))
        card.radius = radius / scale
        val slot = card.findViewById<FrameLayout>(R.id.dashboard_widget_content)
        val view = when (content) {
            LauncherContent.NoDevices -> noDevicesView(themed)
            else -> widget.createView(inflater, slot, variant)
        }
        slot.addView(
            view,
            FrameLayout.LayoutParams(
                (natural.width * density).roundToInt(),
                (natural.height * density).roundToInt(),
                Gravity.CENTER,
            )
        )
        when (content) {
            LauncherContent.Loading -> widget.bindLoading(view, variant, config)
            LauncherContent.NoDevices -> {}
            is LauncherContent.Ready -> widget.bindUnsafe(view, variant, config, content.data)
        }

        card.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(cardHeight, View.MeasureSpec.EXACTLY),
        )
        card.layout(0, 0, cardWidth, cardHeight)
        val bitmap = createBitmap(bitmapWidth, bitmapHeight)
        val canvas = Canvas(bitmap)
        canvas.scale(scale, scale)
        card.draw(canvas)

        val views = RemoteViews(context.packageName, R.layout.launcher_widget)
        views.setImageViewBitmap(R.id.launcher_widget_image, bitmap)
        views.setContentDescription(
            R.id.launcher_widget_image,
            view.contentDescription ?: config.title(themed, widget.label)
        )
        if (content !is LauncherContent.Loading) {
            val intent = Intent(context, LauncherWidgetClickActivity::class.java)
            intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.launcher_widget_image, pendingIntent)
        }
        return views
    }

    private fun noDevicesView(context: Context): View {
        val padding = NO_DEVICES_PADDING_DP * context.resources.displayMetrics.density
        val text = MaterialTextView(context)
        text.setText(R.string.no_supported_devices_found)
        text.gravity = Gravity.CENTER
        text.setPadding(padding.roundToInt(), padding.roundToInt(), padding.roundToInt(), padding.roundToInt())
        return text
    }

    /**
     * The size of the card of [variant] on the dashboard of this phone, in portrait.
     */
    private fun naturalSizeDp(context: Context, variant: WidgetVariant): SizeF {
        val screenWidth = context.resources.configuration.smallestScreenWidthDp
        val column = (screenWidth - 2f * GRID_PADDING_DP) / WidgetGrid.columnsForWidth(screenWidth)
        return SizeF(variant.width * column - 2 * CARD_MARGIN_DP, variant.heightDp.toFloat())
    }

    /**
     * The part of [box] that [variant] fills when it is scaled to fit, multiplied by the square of the scale when the
     * variant must be made smaller.
     */
    private fun score(context: Context, variant: WidgetVariant, box: SizeF): Float {
        val natural = naturalSizeDp(context, variant)
        val scale = min(box.width / natural.width, box.height / natural.height)
        val fill = scale * scale * natural.width * natural.height / (box.width * box.height)
        val shrink = min(scale, 1f)
        return fill * shrink * shrink
    }

    /**
     * The size of the launcher widget in portrait, or null if the launcher did not report it.
     */
    private fun boxSizeDp(options: Bundle): SizeF? {
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (width <= 0 || height <= 0) {
            return null
        }
        return SizeF(width.toFloat(), height.toFloat())
    }
}
