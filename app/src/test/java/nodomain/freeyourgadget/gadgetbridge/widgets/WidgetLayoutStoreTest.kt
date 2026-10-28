package nodomain.freeyourgadget.gadgetbridge.widgets

import androidx.core.content.edit
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StressWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.Vo2MaxWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WidgetLayoutStoreTest : TestBase() {
    @Before
    fun setUpLegacyLayout() {
        WidgetLayoutStore.reset()
        GBApplication.getPrefs().preferences.edit {
            putString(
                "pref_dashboard_layout",
                """[
                    {"id":"today","type":"today","cols":1},
                    {"id":"a","type":"steps","cols":1},
                    {"id":"b","type":"distance","cols":1},
                    {"id":"c","type":"stress_simple","cols":1},
                    {"id":"d","type":"vo2max_running","cols":1}
                ]"""
            )
        }
    }

    private fun columns(columns: Int): Map<String, Int> =
        WidgetLayoutStore.layout(columns).associate { it.instance.instanceId to it.x }

    private fun order(columns: Int): List<String> = WidgetLayoutStore.layout(columns).map { it.instance.instanceId }

    @Test
    fun migratesLegacyLayoutIntoBaseLayout() {
        val placements = WidgetLayoutStore.layout(WidgetGrid.BASE_COLUMNS).associateBy { it.instance.instanceId }

        assertEquals(WidgetVariant.OVERVIEW_TALL.id, placements["today"]?.variantId)
        assertEquals(WidgetVariant.NUMBER.id, placements["c"]?.variantId)
        assertEquals(listOf("today", "a", "b", "c", "d"), order(WidgetGrid.BASE_COLUMNS))
        assertEquals(
            mapOf("today" to 0, "a" to 3, "b" to 3, "c" to 3, "d" to 0),
            columns(WidgetGrid.BASE_COLUMNS),
        )

        val types = WidgetLayoutStore.instances().associate { it.instanceId to it.typeId }
        assertEquals(StressWidget.id, types["c"])
        assertEquals(Vo2MaxWidget.id, types["d"])
        val vo2MaxPrefs = Prefs(GBApplication.getWidgetSharedPrefs("d"))
        assertEquals(Vo2MaxWidget.SPORT_RUNNING, vo2MaxPrefs.getString(Vo2MaxWidget.KEY_SPORT, ""))
    }

    @Test
    fun derivesWiderLayoutFromBaseLayout() {
        assertEquals(listOf("today", "a", "b", "c", "d"), order(12))
        assertEquals(mapOf("today" to 0, "a" to 3, "b" to 6, "c" to 9, "d" to 3), columns(12))
    }

    @Test
    fun firstEditOfDerivedLayoutCopiesInstances() {
        WidgetLayoutStore.delete(12, "a")

        val copies = WidgetLayoutStore.layout(12)
        assertEquals(4, copies.size)
        assertTrue(copies.none { it.instance.instanceId in setOf("today", "b", "c", "d") })
        assertEquals(listOf("today", "a", "b", "c", "d"), order(WidgetGrid.BASE_COLUMNS))

        val vo2MaxCopy = copies.single { it.instance.typeId == Vo2MaxWidget.id }.instance
        val vo2MaxPrefs = Prefs(GBApplication.getWidgetSharedPrefs(vo2MaxCopy.instanceId))
        assertEquals(Vo2MaxWidget.SPORT_RUNNING, vo2MaxPrefs.getString(Vo2MaxWidget.KEY_SPORT, ""))
    }

    @Test
    fun deleteKeepsOtherStoredLayouts() {
        WidgetLayoutStore.instanceForEdit(12, "a")
        WidgetLayoutStore.delete(WidgetGrid.BASE_COLUMNS, "a")

        assertFalse(columns(WidgetGrid.BASE_COLUMNS).containsKey("a"))
        assertEquals(5, WidgetLayoutStore.layout(12).size)
    }

    @Test
    fun addPlacesInstanceOnlyInEditedLayout() {
        WidgetLayoutStore.instanceForEdit(12, "a")
        val instance = WidgetLayoutStore.add(WidgetGrid.BASE_COLUMNS, "sleep", WidgetVariant.GAUGE.id)

        assertEquals(instance.instanceId, order(WidgetGrid.BASE_COLUMNS).last())
        assertEquals(3, columns(WidgetGrid.BASE_COLUMNS)[instance.instanceId])
        assertFalse(columns(12).containsKey(instance.instanceId))
    }

    @Test
    fun restorePutsDeletedInstanceBackWithSettings() {
        val deleted = WidgetLayoutStore.delete(WidgetGrid.BASE_COLUMNS, "d")!!
        WidgetLayoutStore.restore(deleted)

        assertEquals(listOf("today", "a", "b", "c", "d"), order(WidgetGrid.BASE_COLUMNS))
        val vo2MaxPrefs = Prefs(GBApplication.getWidgetSharedPrefs("d"))
        assertEquals(Vo2MaxWidget.SPORT_RUNNING, vo2MaxPrefs.getString(Vo2MaxWidget.KEY_SPORT, ""))
    }

    @Test
    fun arrangeStoresOrderAndColumnsAndKeepsMissingInstances() {
        WidgetLayoutStore.arrange(WidgetGrid.BASE_COLUMNS, listOf("b" to 0, "a" to 3, "today" to 0))

        assertEquals(listOf("b", "a", "today", "c", "d"), order(WidgetGrid.BASE_COLUMNS))
        assertEquals(mapOf("b" to 0, "a" to 3, "today" to 0, "c" to 3, "d" to 0), columns(WidgetGrid.BASE_COLUMNS))
    }
}
