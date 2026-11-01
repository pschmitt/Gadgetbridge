package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.graphics.createBitmap
import com.google.android.material.card.MaterialCardView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.applyCardStyle
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.math.roundToInt

/**
 * Draws the preview of each variant of each widget to a PNG file, in the light and the dark theme.
 * <p>
 * Runs only when the environment variable [ENV_OUTPUT] is set with the value of the output directory.
 */
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class WidgetPreviewRenderTest : TestBase() {
    @Test
    fun renderAllPreviews() {
        val output = System.getenv(ENV_OUTPUT)
        assumeTrue("$ENV_OUTPUT is not set", !output.isNullOrBlank())
        val outputDir = File(output!!).apply { mkdirs() }

        val failures = mutableListOf<String>()
        for (dark in listOf(false, true)) {
            val themed = themedContext(dark)
            val themeName = if (dark) "dark" else "light"
            for (widget in allWidgets()) {
                for (variant in widget.variants) {
                    val name = "${widget.id}_${variant.id}_$themeName"
                    try {
                        val file = File(outputDir, "$name.png")
                        file.outputStream()
                            .use { render(themed, widget, variant).compress(Bitmap.CompressFormat.PNG, 100, it) }
                        LOG.debug("Wrote {}", file)
                    } catch (e: Exception) {
                        LOG.error("Failed to render {}", name, e)
                        failures += "$name: $e"
                    }
                }
            }
        }

        assertTrue("Failed to render:\n${failures.joinToString("\n")}", failures.isEmpty())
    }

    /**
     * Superset of all global and device-specific widgets.
     */
    private fun allWidgets(): List<GBWidget<*>> {
        val byId = LinkedHashMap<String, GBWidget<*>>()
        for (widget in WidgetRegistry.all()) byId.putIfAbsent(widget.id, widget)
        DeviceType.entries
            .map { it.deviceCoordinator }
            .distinctBy { it.javaClass }
            .flatMap { it.widgetsProvider.getWidgets() }
            .forEach { byId.putIfAbsent(it.id, it) }
        return byId.values.toList()
    }

    private fun themedContext(dark: Boolean): Context {
        val themed = ContextThemeWrapper(app, AbstractGBActivity.getThemeResource(AbstractGBActivity.NONE, dark))
        val configuration = Configuration(app.resources.configuration)
        configuration.uiMode = (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
            if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        themed.applyOverrideConfiguration(configuration)
        AbstractGBActivity.applyThemeOverlays(themed.theme, dark)
        return themed
    }

    private fun render(context: Context, widget: GBWidget<*>, variant: WidgetVariant): Bitmap {
        val density = context.resources.displayMetrics.density
        val screenWidth = context.resources.configuration.screenWidthDp
        val column = (screenWidth - 2f * GRID_PADDING_DP) / WidgetGrid.columnsForWidth(screenWidth)
        val width = ((variant.width * column - 2 * CARD_MARGIN_DP) * density).roundToInt()
        val height = (variant.heightDp * density).roundToInt()

        val inflater = LayoutInflater.from(context)
        val card = inflater.inflate(R.layout.dashboard_widget_card, FrameLayout(context), false) as MaterialCardView
        applyCardStyle(card, true)
        val slot = card.findViewById<FrameLayout>(R.id.dashboard_widget_content)
        val content = widget.createView(inflater, slot, variant)
        slot.addView(content)
        val config =
            WidgetConfig(WidgetInstance("preview", widget.id), Prefs(EmptySharedPreferences), true, emptySet(), variant)
        widget.bindUnsafe(content, variant, config, widget.previewData())

        card.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY),
        )
        card.layout(0, 0, width, height)
        val bitmap = createBitmap(width, height)
        card.draw(Canvas(bitmap))
        return bitmap
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WidgetPreviewRenderTest::class.java)

        private const val ENV_OUTPUT = "GB_WIDGET_PREVIEWS"
        private const val GRID_PADDING_DP = 10
        private const val CARD_MARGIN_DP = 6
    }
}
