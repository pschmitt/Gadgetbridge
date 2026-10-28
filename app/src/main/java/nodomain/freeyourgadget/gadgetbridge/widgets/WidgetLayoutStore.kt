package nodomain.freeyourgadget.gadgetbridge.widgets

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid.placeAtFirstFree
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Serializable
import java.util.UUID

/**
 * The stored layouts, by column count.
 */
@GsonSerialized
private data class Dashboard(val layouts: Map<Int, List<WidgetPlacement>>)

/**
 * The stored layout for a column count, and the ids of the instances that were copied to store it.
 */
private class OwnLayout(val placements: List<WidgetPlacement>, private val copies: Map<String, String>) {
    /**
     * The id in this layout of [instanceId], also if it is the id of an instance before the copy.
     */
    fun idOf(instanceId: String): String = copies[instanceId] ?: instanceId
}

/**
 * A widget instance that [WidgetLayoutStore.delete] removed: its layout, its position in the order, and its settings.
 */
class DeletedWidget(
    val columns: Int,
    val index: Int,
    val placement: WidgetPlacement,
    val settings: Map<String, *>,
)

/**
 * The stored layouts and the settings of their instances, at one point in time.
 */
data class DashboardSnapshot(
    internal val layouts: Map<Int, List<WidgetPlacement>>,
    internal val settings: Map<String, Map<String, *>>,
) : Serializable

/**
 * Persists the layouts as JSON in one preference, and the settings of each widget instance in its own
 * SharedPreferences file (see [GBApplication.getWidgetSharedPrefs]).
 * <p>
 * The layout of [WidgetGrid.BASE_COLUMNS] is always stored. A layout for a different column count is derived from the
 * base layout, and shows its instances, until the first edit. The first edit stores it with a copy of each instance.
 */
object WidgetLayoutStore {
    private val LOG: Logger = LoggerFactory.getLogger(WidgetLayoutStore::class.java)

    private const val PREF_DASHBOARD = "pref_dashboard_layouts"

    private val gson = Gson()
    private val dashboardType = object : TypeToken<Dashboard>() {}.type

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /**
     * The widget instances of all stored layouts.
     */
    fun instances(): List<WidgetInstance> = load().layouts.values.flatten().map { it.instance }

    /**
     * The stored layouts, by column count.
     */
    fun storedLayouts(): Map<Int, List<WidgetPlacement>> = load().layouts

    /**
     * The placements for [columns]: the stored layout, or a layout derived from the base layout if there is none.
     */
    fun layout(columns: Int): List<WidgetPlacement> = layoutOf(load(), columns)

    /**
     * The widgets that the grid displays for [columns], in their order. Placements of widgets that are not available
     * now are left out.
     */
    fun placedWidgets(columns: Int): List<PlacedWidget> {
        val widgets = WidgetRegistry.all().associateBy { it.id }
        return layout(columns).mapNotNull { placement ->
            val widget = widgets[placement.instance.typeId] ?: return@mapNotNull null
            PlacedWidget(placement.instance, widget, widget.variant(placement.variantId), placement.x)
        }
    }

    /**
     * Stores the layout for [columns] if it is derived. Returns the instance in it that [instanceId] is, or was copied
     * to.
     */
    @Synchronized
    fun instanceForEdit(columns: Int, instanceId: String): WidgetInstance? {
        val layout = ownLayout(columns)
        return layout.placements.find { it.instance.instanceId == layout.idOf(instanceId) }?.instance
    }

    /**
     * Stores the layout for [columns] in the order of [arrangement], each instance at its column. Instances that are
     * not in [arrangement] go to the end.
     */
    @Synchronized
    fun arrange(columns: Int, arrangement: List<Pair<String, Int>>) {
        val layout = ownLayout(columns)
        val byId = layout.placements.associateBy { it.instance.instanceId }
        val arranged = arrangement.mapNotNull { (instanceId, x) -> byId[layout.idOf(instanceId)]?.copy(x = x) }
        val arrangedIds = arranged.map { it.instance.instanceId }.toSet()
        saveLayout(columns, arranged + layout.placements.filterNot { it.instance.instanceId in arrangedIds })
    }

    /**
     * Adds a new instance of [typeId] with [variantId] to the layout for [columns], and returns it.
     */
    @Synchronized
    fun add(columns: Int, typeId: String, variantId: String): WidgetInstance {
        val instance = WidgetInstance(newInstanceId(), typeId)
        val placements = ownLayout(columns).placements
        saveLayout(columns, placements + placeAtFirstFree(columns, placements, WidgetPlacement(instance, variantId, 0)))
        return instance
    }

    /**
     * Duplicates [instanceId] (settings included) in the layout for [columns], with the same variant. Returns the copy,
     * if the original was found.
     */
    @Synchronized
    fun duplicate(columns: Int, instanceId: String): WidgetInstance? {
        val layout = ownLayout(columns)
        val original = layout.placements.find { it.instance.instanceId == layout.idOf(instanceId) } ?: return null
        val copy = copyInstance(original.instance)
        saveLayout(
            columns,
            layout.placements + placeAtFirstFree(columns, layout.placements, original.copy(instance = copy))
        )
        return copy
    }

    /**
     * Removes [instanceId] from the layout for [columns], and deletes its settings. Returns the [DeletedWidget] if it
     * was found, so that the change can be undone.
     */
    @Synchronized
    fun delete(columns: Int, instanceId: String): DeletedWidget? {
        val layout = ownLayout(columns)
        val id = layout.idOf(instanceId)
        val index = layout.placements.indexOfFirst { it.instance.instanceId == id }
        if (index < 0) return null
        val settings = GBApplication.getWidgetSharedPrefs(id)?.all.orEmpty()
        saveLayout(columns, layout.placements.filterIndexed { i, _ -> i != index })
        GBApplication.deleteWidgetSharedPrefs(id)
        return DeletedWidget(columns, index, layout.placements[index], settings)
    }

    /**
     * Restores a widget instance that [delete] removed back into its layout, with its settings.
     */
    @Synchronized
    fun restore(deleted: DeletedWidget) {
        val placements = ownLayout(deleted.columns).placements.toMutableList()
        if (placements.any { it.instance.instanceId == deleted.placement.instance.instanceId }) {
            LOG.warn("Not restoring widget {}, the layout already has it", deleted.placement.instance.instanceId)
            return
        }
        placements.add(deleted.index.coerceAtMost(placements.size), deleted.placement)
        GBApplication.getWidgetSharedPrefs(deleted.placement.instance.instanceId)?.let { dest ->
            copyAllPrefs(deleted.settings, dest) { it }
        }
        saveLayout(deleted.columns, placements)
    }

    /**
     * The stored layouts and the settings of their instances.
     */
    @Synchronized
    fun snapshot(): DashboardSnapshot {
        val layouts = load().layouts
        val settings = layouts.values.flatten().associate { placement ->
            val instanceId = placement.instance.instanceId
            instanceId to GBApplication.getWidgetSharedPrefs(instanceId)?.all.orEmpty()
        }
        return DashboardSnapshot(layouts, settings)
    }

    /**
     * Replaces the stored layouts and the settings of their instances with [snapshot]. Deletes the settings of the
     * instances that are not in [snapshot].
     */
    @Synchronized
    fun restore(snapshot: DashboardSnapshot) {
        LOG.info("Restoring the dashboard with layouts {}", snapshot.layouts.mapValues { it.value.size })
        for (instance in instances()) {
            if (instance.instanceId !in snapshot.settings) {
                GBApplication.deleteWidgetSharedPrefs(instance.instanceId)
            }
        }
        for ((instanceId, settings) in snapshot.settings) {
            GBApplication.deleteWidgetSharedPrefs(instanceId)
            GBApplication.getWidgetSharedPrefs(instanceId)?.let { dest ->
                copyAllPrefs(settings, dest) { it }
            }
        }
        save(Dashboard(snapshot.layouts))
    }

    /**
     * Adds a new instance for each variant of each widget to each stored layout, also for the widgets that no device
     * supports.
     */
    @Synchronized
    fun addAllVariants() {
        addToStoredLayouts(WidgetRegistry.all().flatMap { widget -> widget.variants.map { widget to it } })
    }

    /**
     * Removes the dashboard and its notices. The next load migrates the legacy config again.
     */
    @Synchronized
    fun reset() {
        LOG.info("Resetting the dashboard")
        DashboardNotices.set(DashboardNotices.State.NONE)
        sharedPreferences().edit {
            remove(PREF_DASHBOARD)
            putBoolean(DashboardMigration.PREF_UPGRADE, true)
        }
        _changes.tryEmit(Unit)
    }

    /**
     * Replaces all layouts with [layouts], and deletes the settings of the instances that are not in them.
     */
    @Synchronized
    fun replaceLayouts(layouts: Map<Int, List<WidgetPlacement>>) {
        LOG.info("Replacing the dashboard with layouts {}", layouts.mapValues { it.value.size })
        val keptIds = layouts.values.flatten().map { it.instance.instanceId }.toSet()
        for (instance in instances()) {
            if (instance.instanceId !in keptIds) {
                GBApplication.deleteWidgetSharedPrefs(instance.instanceId)
            }
        }
        save(Dashboard(layouts))
    }

    /**
     * Adds a new instance of each widget, with its variant, to each stored layout at the first free column.
     */
    private fun addToStoredLayouts(widgets: List<Pair<GBWidget<*>, WidgetVariant>>) {
        val layouts = load().layouts.mapValues { (columns, stored) ->
            val placements = stored.toMutableList()
            for ((widget, variant) in widgets) {
                LOG.info("Adding widget {} with variant {} to {} columns", widget.id, variant.id, columns)
                val instance = WidgetInstance(newInstanceId(), widget.id)
                placements += placeAtFirstFree(columns, placements, WidgetPlacement(instance, variant.id, 0))
            }
            placements
        }
        save(Dashboard(layouts))
    }

    /**
     * Removes the stored layout for [columns] and deletes the settings of its instances.
     */
    @Synchronized
    fun resetLayout(columns: Int) {
        LOG.info("Resetting the layout for {} columns", columns)
        val dashboard = load()
        for (placement in dashboard.layouts[columns].orEmpty()) {
            GBApplication.deleteWidgetSharedPrefs(placement.instance.instanceId)
        }
        if (columns == WidgetGrid.BASE_COLUMNS) {
            save(dashboard.copy(layouts = dashboard.layouts + (columns to DashboardMigration.migrate())))
        } else {
            save(dashboard.copy(layouts = dashboard.layouts - columns))
        }
    }

    /**
     * Changes the variant of [instanceId] in the layout for [columns].
     */
    @Synchronized
    fun setVariant(columns: Int, instanceId: String, variantId: String) {
        val layout = ownLayout(columns)
        val id = layout.idOf(instanceId)
        saveLayout(
            columns,
            layout.placements.map { if (it.instance.instanceId == id) it.copy(variantId = variantId) else it }
        )
    }

    @Synchronized
    private fun load(): Dashboard {
        val json = GBApplication.getPrefs().getString(PREF_DASHBOARD, "")
        if (json.isBlank()) {
            val dashboard = Dashboard(mapOf(WidgetGrid.BASE_COLUMNS to DashboardMigration.migrate()))
            save(dashboard)
            return dashboard
        }

        return try {
            gson.fromJson(json, dashboardType)
        } catch (e: Exception) {
            LOG.error("Failed to parse the dashboard, treating it as empty", e)
            Dashboard(mapOf(WidgetGrid.BASE_COLUMNS to emptyList()))
        }
    }

    @Synchronized
    private fun save(dashboard: Dashboard) {
        LOG.debug("Saving dashboard with layouts {}", dashboard.layouts.mapValues { it.value.size })
        sharedPreferences().edit {
            putString(PREF_DASHBOARD, gson.toJson(dashboard))
        }
        _changes.tryEmit(Unit)
    }

    private fun saveLayout(columns: Int, placements: List<WidgetPlacement>) {
        val dashboard = load()
        save(dashboard.copy(layouts = dashboard.layouts + (columns to placements)))
    }

    private fun layoutOf(dashboard: Dashboard, columns: Int): List<WidgetPlacement> {
        dashboard.layouts[columns]?.let { return it }

        val derived = mutableListOf<WidgetPlacement>()
        for (placement in dashboard.layouts[WidgetGrid.BASE_COLUMNS].orEmpty()) {
            derived += placeAtFirstFree(columns, derived, placement)
        }
        return derived
    }

    /**
     * The layout for [columns]. If it is derived, it is stored first, with a copy of each instance.
     */
    private fun ownLayout(columns: Int): OwnLayout {
        val dashboard = load()
        dashboard.layouts[columns]?.let { return OwnLayout(it, emptyMap()) }

        LOG.info("Storing the derived layout for {} columns", columns)
        val copies = mutableMapOf<String, String>()
        val placements = layoutOf(dashboard, columns).map { placement ->
            val copy = copyInstance(placement.instance)
            copies[placement.instance.instanceId] = copy.instanceId
            placement.copy(instance = copy)
        }
        saveLayout(columns, placements)
        return OwnLayout(placements, copies)
    }

    /**
     * A new instance of the same widget as [instance], with a copy of its settings.
     */
    private fun copyInstance(instance: WidgetInstance): WidgetInstance {
        val copy = instance.copy(instanceId = newInstanceId())
        val source = GBApplication.getWidgetSharedPrefs(instance.instanceId)
        val dest = GBApplication.getWidgetSharedPrefs(copy.instanceId)
        if (source != null && dest != null) {
            copyAllPrefs(source.all, dest) { it }
        }
        return copy
    }

    internal fun newInstanceId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

    private fun sharedPreferences(): SharedPreferences = GBApplication.getPrefs().preferences

    /**
     * Copies every entry of [source] into [dest], transforming each key with [keyTransform] and
     * skipping entries the transform rejects (returns null for). Preserves each value's runtime
     * type (String/boolean/int/long/float/String set), the same types SharedPreferences supports.
     */
    internal fun copyAllPrefs(source: Map<String, *>, dest: SharedPreferences, keyTransform: (String) -> String?) {
        dest.edit {
            for ((key, value) in source) {
                val destKey = keyTransform(key) ?: continue
                when (value) {
                    is Boolean -> putBoolean(destKey, value)
                    is String -> putString(destKey, value)
                    is Int -> putInt(destKey, value)
                    is Long -> putLong(destKey, value)
                    is Float -> putFloat(destKey, value)
                    is Set<*> -> {
                        @Suppress("UNCHECKED_CAST")
                        putStringSet(destKey, value as Set<String>)
                    }
                }
            }
        }
    }
}
