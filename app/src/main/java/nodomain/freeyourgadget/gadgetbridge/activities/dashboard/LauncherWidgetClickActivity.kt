package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry
import nodomain.freeyourgadget.gadgetbridge.widgets.bindUnsafe
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetRenderer
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgetStore
import nodomain.freeyourgadget.gadgetbridge.widgets.launcher.LauncherWidgets
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate

/**
 * A transparent activity that handles the click action of a launcher widget.
 * It closes when it starts a different activity, or when it gets the focus back after a dialog of the click action.
 */
class LauncherWidgetClickActivity : AbstractGBActivity() {
    private var clicked = false
    private var lostFocus = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme.applyStyle(R.style.ThemeOverlay_App_Translucent, true)
        if (savedInstanceState != null) {
            finish()
            return
        }

        val appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val launcherWidget = LauncherWidgetStore.get(appWidgetId)
        val widget = launcherWidget?.let { WidgetRegistry.byId(it.instance.typeId) }
        if (launcherWidget == null || widget == null) {
            LOG.warn("No widget for launcher widget {}", appWidgetId)
            finish()
            return
        }

        val variant = LauncherWidgetRenderer.variantFor(this, appWidgetId, widget, launcherWidget)
        val config = LauncherWidgets.configOf(launcherWidget, variant)
        val query = DashboardQuery.forDay(LocalDate.now())
        lifecycleScope.launch {
            val data = try {
                withContext(Dispatchers.IO) {
                    widget.loadData(
                        WidgetDataScope(query, config.resolveDevices(widget)),
                        config
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load data for launcher widget {}", appWidgetId, e)
                finish()
                return@launch
            }

            // Some widgets read the bound data in onClick
            val view = widget.createView(layoutInflater, FrameLayout(this@LauncherWidgetClickActivity), variant)
            widget.bindUnsafe(view, variant, config, data)
            widget.onClick(view, variant, config, query.timeTo)
            clicked = true
            if (hasWindowFocus()) {
                finishUnlessFocusLost()
            }
        }
    }

    /**
     * Closes the activity if it still has the focus after a short delay. A dialog of the click action takes the
     * focus during this delay.
     */
    private fun finishUnlessFocusLost() {
        window.decorView.postDelayed({
            if (hasWindowFocus() && !lostFocus) {
                finish()
            }
        }, FOCUS_DELAY_MS)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        super.startActivity(intent, options)
        finish()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!clicked) {
            return
        }
        if (!hasFocus) {
            lostFocus = true
        } else if (lostFocus) {
            finish()
        } else {
            finishUnlessFocusLost()
        }
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(LauncherWidgetClickActivity::class.java)

        private const val FOCUS_DELAY_MS = 300L
    }
}
