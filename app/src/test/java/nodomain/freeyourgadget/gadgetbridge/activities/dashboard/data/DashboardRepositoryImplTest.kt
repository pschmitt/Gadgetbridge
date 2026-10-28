package nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data

import androidx.core.content.edit
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardNotices
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StepsWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardRepositoryImplTest : TestBase() {
    @Test
    fun layoutFlowEmitsLayoutChanges() = runTest {
        val repository = repository()
        val initial = repository.layoutFlow(WidgetGrid.BASE_COLUMNS).first()
        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            repository.layoutFlow(WidgetGrid.BASE_COLUMNS).drop(1).first()
        }
        advanceUntilIdle()

        val instance = repository.add(WidgetGrid.BASE_COLUMNS, StepsWidget.id, WidgetVariant.GAUGE.id)
        val layout = updated.await()

        assertEquals(initial.size + 1, layout.size)
        assertTrue(layout.any { it.instance.instanceId == instance.instanceId })

        repository.delete(WidgetGrid.BASE_COLUMNS, instance.instanceId)
    }

    @Test
    fun settingsFlowEmitsPreferenceChanges() = runTest {
        val prefs = GBApplication.getPrefs().preferences
        val initial = DashboardPreferences(prefs).current()
        val repository = repository()
        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            repository.settingsFlow().drop(1).first()
        }
        advanceUntilIdle()

        prefs.edit { putBoolean(DashboardPreferences.PREF_CARDS_ENABLED, !initial.cardsEnabled) }
        val settings = updated.await()

        assertEquals(!initial.cardsEnabled, settings.cardsEnabled)
        prefs.edit { putBoolean(DashboardPreferences.PREF_CARDS_ENABLED, initial.cardsEnabled) }
    }

    @Test
    fun noticesFlowEmitsNoticeChanges() = runTest {
        val initial = DashboardNotices.state()
        val repository = repository()
        val updated = async(start = CoroutineStart.UNDISPATCHED) {
            repository.noticesFlow().drop(1).first()
        }
        advanceUntilIdle()

        val notice = DashboardNotices.State(migration = !initial.migration, deviceAddress = "test")
        repository.setNotices(notice)

        assertEquals(notice, updated.await())
        repository.setNotices(initial)
    }

    @Test
    fun startEditingRequestIsConsumedOnce() {
        val preferences = DashboardPreferences()

        preferences.requestStartEditing()
        assertTrue(preferences.consumeStartEditingRequest())
        assertFalse(preferences.consumeStartEditingRequest())
    }

    @Test
    fun noticesCanBeReset() = runTest {
        val initial = DashboardNotices.state()
        val repository = repository()

        repository.setNotices(DashboardNotices.State(migration = true, deviceAddress = "test"))
        assertEquals(
            DashboardNotices.State(migration = true, deviceAddress = "test"),
            repository.currentNotices(),
        )

        repository.setNotices(DashboardNotices.State.NONE)
        assertEquals(DashboardNotices.State.NONE, repository.currentNotices())
        repository.setNotices(initial)
    }

    private fun TestScope.repository() = DashboardRepositoryImpl(
        ioDispatcher = StandardTestDispatcher(testScheduler),
    )
}
