package nodomain.freeyourgadget.gadgetbridge.widgets.launcher

import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A widget instance on the launcher. A null [variantId] selects the variant from the size of the launcher widget.
 */
@GsonSerialized
data class LauncherWidget(
    val instance: WidgetInstance,
    val variantId: String?,
)

/**
 * Persists the launcher widgets as JSON in one preference, by app widget id. The settings of each instance are in
 * their own SharedPreferences file (same as the dashboard).
 */
object LauncherWidgetStore {
    private val LOG: Logger = LoggerFactory.getLogger(LauncherWidgetStore::class.java)

    const val PREF_LAUNCHER_WIDGETS = "pref_launcher_widgets"

    private val gson = Gson()
    private val widgetsType = object : TypeToken<Map<Int, LauncherWidget>>() {}.type

    fun get(appWidgetId: Int): LauncherWidget? = load()[appWidgetId]

    fun instances(): List<WidgetInstance> = load().values.map { it.instance }

    /**
     * Stores a new instance of [typeId] for [appWidgetId]. A null [variantId] is the automatic variant.
     */
    fun add(appWidgetId: Int, typeId: String, variantId: String?): LauncherWidget {
        val widget = LauncherWidget(WidgetInstance(WidgetLayoutStore.newInstanceId(), typeId), variantId)
        LOG.info("Adding launcher widget {}: {}", appWidgetId, widget)
        save(load() + (appWidgetId to widget))
        return widget
    }

    fun setVariant(appWidgetId: Int, variantId: String?) {
        val widgets = load()
        val widget = widgets[appWidgetId] ?: return
        save(widgets + (appWidgetId to widget.copy(variantId = variantId)))
    }

    /**
     * Removes [appWidgetIds], and deletes the settings of their instances.
     */
    fun delete(appWidgetIds: IntArray) {
        val widgets = load()
        for (appWidgetId in appWidgetIds) {
            val widget = widgets[appWidgetId] ?: continue
            LOG.info("Deleting launcher widget {}: {}", appWidgetId, widget)
            GBApplication.deleteWidgetSharedPrefs(widget.instance.instanceId)
        }
        save(widgets - appWidgetIds.toSet())
    }

    /**
     * Moves each widget from its id in [oldIds] to the id at the same index in [newIds].
     */
    fun remap(oldIds: IntArray, newIds: IntArray) {
        LOG.info("Remapping launcher widgets {} to {}", oldIds.contentToString(), newIds.contentToString())
        val widgets = load()
        val moved = oldIds.zip(newIds).mapNotNull { (old, new) -> widgets[old]?.let { new to it } }.toMap()
        save(widgets - oldIds.toSet() + moved)
    }

    private fun load(): Map<Int, LauncherWidget> {
        val json = GBApplication.getPrefs().getString(PREF_LAUNCHER_WIDGETS, "")
        if (json.isBlank()) {
            return emptyMap()
        }
        return try {
            gson.fromJson(json, widgetsType)
        } catch (e: Exception) {
            LOG.error("Failed to parse the launcher widgets, treating them as empty", e)
            emptyMap()
        }
    }

    private fun save(widgets: Map<Int, LauncherWidget>) {
        GBApplication.getPrefs().preferences.edit {
            putString(PREF_LAUNCHER_WIDGETS, gson.toJson(widgets))
        }
    }
}
