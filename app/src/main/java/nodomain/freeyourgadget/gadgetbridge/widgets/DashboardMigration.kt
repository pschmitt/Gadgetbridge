package nodomain.freeyourgadget.gadgetbridge.widgets

import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.gson.GsonSerialized
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid.placeAtFirstFree
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.ActiveCaloriesWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.CaloriesWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxWidget
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * One widget in the legacy layout format, before the cell grid.
 */
@GsonSerialized
private data class LegacyLayoutEntry(val id: String, val type: String, val cols: Int)

/**
 * The migration from the dashboard preferences before the cell grid.
 */
object DashboardMigration {
    private val LOG: Logger = LoggerFactory.getLogger(DashboardMigration::class.java)

    private const val PREF_LEGACY_LAYOUT = "pref_dashboard_layout"
    private const val PREF_LEGACY_ORDER = "pref_dashboard_widgets_order"

    /**
     * Set when the app was updated from a version before the cell grid.
     */
    const val PREF_UPGRADE = "pref_dashboard_upgrade"

    /**
     * The default widgets before the cell grid, in order.
     */
    private val DEFAULT_ORDER = listOf(
        "today",
        "goals",
        "steps",
        "distance",
        "activetime",
        "sleep",
        "bodyenergy",
        "stress_segmented",
        "hrv",
        "bloodpressure",
        "vo2max",
        "calories_active",
        "calories_segmented",
    )

    private val gson = Gson()
    private val legacyListType = object : TypeToken<List<LegacyLayoutEntry>>() {}.type

    /**
     * The placements of the first base layout.
     * - After an update, it is the legacy config, or the default widgets if the legacy config is the default.
     * - After a fresh install, it is the default widgets.
     */
    fun migrate(): List<WidgetPlacement> {
        val prefs = GBApplication.getPrefs()
        val upgrade = prefs.getBoolean(PREF_UPGRADE, false) ||
            prefs.contains(PREF_LEGACY_LAYOUT) ||
            prefs.contains(PREF_LEGACY_ORDER)
        prefs.preferences.edit {
            remove(PREF_UPGRADE)
        }
        if (!upgrade) {
            LOG.info("Creating the default dashboard")
            return DashboardDefaults.placements()
        }

        val entries = loadLegacyLayout() ?: migrateLegacyOrder()
        LOG.info("Migrating {} dashboard widgets to the cell grid", entries.size)

        val legacyDefault = DEFAULT_ORDER.map { type ->
            LegacyLayoutEntry(id = type, type = type, cols = if (type == "today" || type == "goals") 2 else 1)
        }
        val placements = if (entries == legacyDefault) {
            DashboardDefaults.placements()
        } else {
            legacyPlacements(entries, migrateVariants = true)
        }
        if (placements != legacyPlacements(entries, migrateVariants = false)) {
            DashboardNotices.set(DashboardNotices.state().copy(migration = true))
        }
        return placements
    }

    /**
     * Replaces all layouts with the legacy config in the gauge designs.
     */
    fun restoreClassic() {
        LOG.info("Restoring the classic dashboard")
        val classic = legacyPlacements(loadLegacyLayout() ?: migrateLegacyOrder(), migrateVariants = false)
        WidgetLayoutStore.replaceLayouts(mapOf(WidgetGrid.BASE_COLUMNS to classic))
    }

    /**
     * The placements of the legacy [entries]. If [migrateVariants] is false, each widget keeps its gauge design.
     */
    private fun legacyPlacements(entries: List<LegacyLayoutEntry>, migrateVariants: Boolean): List<WidgetPlacement> {
        val placements = mutableListOf<WidgetPlacement>()
        for ((id, type, cols) in entries) {
            val (typeId, variantId) = when (type) {
                "today", "goals" -> type to if (cols >= 2) WidgetVariant.LARGE.id else WidgetVariant.OVERVIEW_TALL.id
                "stress_segmented" -> StressWidget.id to StressWidget.SEGMENTED.id
                "stress_simple" -> StressWidget.id to StressWidget.SIMPLE.id
                "stress_breakdown" -> StressWidget.id to StressWidget.BREAKDOWN.id
                "calories_active" -> ActiveCaloriesWidget.id to WidgetVariant.GAUGE.id
                "calories_segmented" -> CaloriesWidget.id to CaloriesWidget.SEGMENTED.id
                "vo2max_running" -> {
                    setVo2MaxSport(id, Vo2MaxWidget.SPORT_RUNNING)
                    Vo2MaxWidget.id to WidgetVariant.GAUGE.id
                }

                "vo2max_cycling" -> {
                    setVo2MaxSport(id, Vo2MaxWidget.SPORT_CYCLING)
                    Vo2MaxWidget.id to WidgetVariant.GAUGE.id
                }

                else -> type to (WidgetRegistry.byId(type)?.variants?.first()?.id ?: "")
            }
            val migratedVariantId = if (migrateVariants) migratedVariant(typeId, variantId) else variantId
            placements += placeAtFirstFree(
                WidgetGrid.BASE_COLUMNS,
                placements,
                WidgetPlacement(WidgetInstance(id, typeId), migratedVariantId, 0)
            )
        }
        return placements
    }

    /**
     * The variant that replaces the gauge design [variantId] of [typeId]: default to NUMBER, if the widget supports it.
     */
    private fun migratedVariant(typeId: String, variantId: String): String =
        if (WidgetRegistry.byId(typeId)?.variants?.contains(WidgetVariant.NUMBER) == true) {
            WidgetVariant.NUMBER.id
        } else {
            variantId
        }

    private fun setVo2MaxSport(instanceId: String, sport: String) {
        GBApplication.getWidgetSharedPrefs(instanceId)?.edit {
            putString(Vo2MaxWidget.KEY_SPORT, sport)
        }
    }

    private fun loadLegacyLayout(): List<LegacyLayoutEntry>? {
        val json = GBApplication.getPrefs().getString(PREF_LEGACY_LAYOUT, "")
        if (json.isBlank()) return null

        return try {
            gson.fromJson<List<LegacyLayoutEntry>>(json, legacyListType).orEmpty()
        } catch (e: Exception) {
            LOG.error("Failed to parse the legacy widget layout", e)
            null
        }
    }

    /**
     * Reads the widget order from before the widget registry, and copies the global Today and Goals
     * preferences into the settings of their instances.
     */
    private fun migrateLegacyOrder(): List<LegacyLayoutEntry> {
        val prefs = GBApplication.getPrefs()

        val order = prefs.getString(PREF_LEGACY_ORDER, DEFAULT_ORDER.joinToString(","))
            .split(",")
            .filter { it.isNotBlank() }

        val entries = order.map { typeId ->
            val columns = when (typeId) {
                "today" -> if (prefs.getBoolean("dashboard_widget_today_2columns", true)) 2 else 1
                "goals" -> if (prefs.getBoolean("dashboard_widget_goals_2columns", true)) 2 else 1
                else -> 1
            }
            LegacyLayoutEntry(id = typeId, type = typeId, cols = columns)
        }

        // The old per-widget options were global preferences, e.g. "dashboard_widget_today_24h".
        // Copy each family into its instance's own settings file, dropping the widget prefix so
        // the key matches what TodayWidget/GoalsWidget declare via `settings()`.
        val defaultPrefs = prefs.preferences
        if (order.contains("today")) {
            GBApplication.getWidgetSharedPrefs("today")?.let { dest ->
                WidgetLayoutStore.copyAllPrefs(defaultPrefs.all, dest) { key ->
                    key.removePrefix("dashboard_widget_today_").takeIf { it != key }
                }
            }
        }
        if (order.contains("goals")) {
            GBApplication.getWidgetSharedPrefs("goals")?.let { dest ->
                WidgetLayoutStore.copyAllPrefs(defaultPrefs.all, dest) { key ->
                    key.removePrefix("dashboard_widget_goals_").takeIf { it != key }
                }
            }
        }

        return entries
    }
}
