package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardRepository
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardSettings
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardWidgetLoader
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.test.MainDispatcherRule
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardNotices
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardQuery
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardSnapshot
import nodomain.freeyourgadget.gadgetbridge.widgets.DeletedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.PlacedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetPlacement
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import nodomain.freeyourgadget.gadgetbridge.widgets.impl.StepsWidget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun loadsWidgetsForTheSelectedDate() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )

        advanceUntilIdle()

        val item = viewModel.uiState.value.items.single()
        assertEquals(WidgetState.Ready("data-1"), item.state)
        val endOfToday = LocalDate.now()
            .atTime(LocalTime.MAX)
            .atZone(ZoneId.systemDefault())
            .toEpochSecond()
            .toInt()
        assertEquals(endOfToday, repository.loadQueries.single().timeTo)
    }

    @Test
    fun changingDateLoadsTheNewDay() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )

        advanceUntilIdle()
        viewModel.onPreviousDay()
        advanceUntilIdle()

        assertEquals(2, repository.loadQueries.size)
        assertEquals(LocalDate.now().minusDays(1), viewModel.uiState.value.date)
        assertFalse(viewModel.uiState.value.isToday)
    }

    @Test
    fun columnsChangeRequestsANewLayout() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )

        advanceUntilIdle()
        viewModel.onColumnsChanged(9)
        advanceUntilIdle()

        assertEquals(listOf(WidgetGrid.BASE_COLUMNS, 9), repository.requestedColumns)
    }

    @Test
    fun settingsChangeReloadsWidgets() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )

        advanceUntilIdle()
        repository.settings.value = DashboardSettings(cardsEnabled = false)
        advanceUntilIdle()

        assertEquals(2, repository.loadQueries.size)
        assertFalse(viewModel.uiState.value.cardsEnabled)
    }

    @Test
    fun onlyNewDataForDashboardDevicesReloads() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )
        val device = GBDevice("AA:BB:CC:DD:EE:FF", "Test", null, null, DeviceType.UNKNOWN)

        advanceUntilIdle()
        repository.inScope = false
        viewModel.onNewData(device)
        advanceUntilIdle()
        assertEquals(1, repository.loadQueries.size)

        repository.inScope = true
        viewModel.onNewData(device)
        advanceUntilIdle()
        assertEquals(2, repository.loadQueries.size)
    }

    @Test
    fun startEditingRequestIsConsumedOnce() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )
        repository.startEditingRequested = true

        advanceUntilIdle()
        assertTrue(viewModel.consumeStartEditingRequest())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.editState != null)
        assertFalse(viewModel.consumeStartEditingRequest())
    }

    @Test
    fun editModeTracksChangesAndRestoresDeletedWidgets() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )

        advanceUntilIdle()
        viewModel.onStartEditing()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.editState != null)
        assertEquals(2, repository.snapshotCalls)

        val undo = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.onDelete("steps-1")
        advanceUntilIdle()
        assertEquals(DashboardEvent.ShowUndo(DashboardUndoAction.DELETE_WIDGET), undo.await())
        assertTrue(viewModel.uiState.value.editState!!.canDiscard)

        viewModel.onUndoDelete()
        advanceUntilIdle()
        assertEquals(1, repository.restoreDeletedCalls)

        viewModel.onFinishEditing()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.editState)
    }

    @Test
    fun discardRestoresTheSnapshotTakenAtTheStartOfEditing() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )

        advanceUntilIdle()
        viewModel.onStartEditing()
        advanceUntilIdle()
        viewModel.onArrange(listOf("steps-1" to 3))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.editState!!.canDiscard)

        viewModel.onDiscardEditing()
        advanceUntilIdle()

        assertEquals(1, repository.restoreSnapshotCalls)
        assertNull(viewModel.uiState.value.editState)
    }

    @Test
    fun restoreClassicDesignCanBeUndone() = runTest {
        val repository = FakeDashboardRepository(listOf(placedWidget()))
        val viewModel = DashboardViewModel(
            savedStateHandle = SavedStateHandle(),
            repository = repository,
            widgetLoader = repository.loader
        )

        advanceUntilIdle()
        val undo = async(start = CoroutineStart.UNDISPATCHED) { viewModel.events.first() }
        viewModel.onRestoreClassicDesign()
        advanceUntilIdle()
        assertEquals(
            DashboardEvent.ShowUndo(DashboardUndoAction.RESTORE_CLASSIC_DESIGN),
            undo.await(),
        )
        assertEquals(1, repository.restoreClassicCalls)

        viewModel.onUndoRestoreClassicDesign()
        advanceUntilIdle()
        assertEquals(1, repository.restoreSnapshotCalls)
    }

    private class FakeDashboardRepository(
        initialLayout: List<PlacedWidget>,
    ) : DashboardRepository {
        val loader = DashboardWidgetLoader { _, query, _ ->
            loadQueries += query
            "data-${loadQueries.size}"
        }
        val loadQueries = mutableListOf<DashboardQuery>()
        val requestedColumns = mutableListOf<Int>()
        val settings = MutableStateFlow(DashboardSettings())
        val notices = MutableStateFlow(DashboardNotices.State.NONE)
        var inScope = true
        var startEditingRequested = false
        var snapshotCalls = 0
        var restoreDeletedCalls = 0
        var restoreSnapshotCalls = 0
        var restoreClassicCalls = 0

        private val layout = MutableStateFlow(initialLayout)
        private var revision = 0

        override fun layoutFlow(columns: Int): Flow<List<PlacedWidget>> {
            requestedColumns += columns
            return layout
        }

        override fun settingsFlow(): Flow<DashboardSettings> = settings

        override fun noticesFlow(): Flow<DashboardNotices.State> = notices

        override fun currentSettings(): DashboardSettings = settings.value

        override fun currentNotices(): DashboardNotices.State = notices.value

        override fun isDeviceInScope(device: GBDevice): Boolean = inScope

        override fun consumeStartEditingRequest(): Boolean {
            val requested = startEditingRequested
            startEditingRequested = false
            return requested
        }

        override fun deviceByAddress(address: String): GBDevice? = null

        override fun availableWidgets(): List<GBWidget<*>> = listOf(StepsWidget)

        override suspend fun newWidgets(device: GBDevice): List<GBWidget<*>> = emptyList()

        override suspend fun add(columns: Int, typeId: String, variantId: String): WidgetInstance {
            revision++
            return WidgetInstance("added-$revision", typeId)
        }

        override suspend fun arrange(columns: Int, arrangement: List<Pair<String, Int>>) {
            revision++
        }

        override suspend fun duplicate(columns: Int, instanceId: String): WidgetInstance {
            revision++
            return WidgetInstance("copy-$revision", StepsWidget.id)
        }

        override suspend fun delete(columns: Int, instanceId: String): DeletedWidget {
            revision++
            return DeletedWidget(
                columns,
                0,
                WidgetPlacement(WidgetInstance(instanceId, StepsWidget.id), WidgetVariant.GAUGE.id, 0),
                emptyMap<String, Any>(),
            )
        }

        override suspend fun restore(deleted: DeletedWidget) {
            restoreDeletedCalls++
            revision++
        }

        override suspend fun setVariant(columns: Int, instanceId: String, variantId: String) {
            revision++
        }

        override suspend fun instanceForEdit(columns: Int, instanceId: String): WidgetInstance =
            WidgetInstance(instanceId, StepsWidget.id)

        override suspend fun snapshot(): DashboardSnapshot {
            snapshotCalls++
            return DashboardSnapshot(emptyMap(), mapOf("fake" to mapOf("revision" to revision)))
        }

        override suspend fun restore(snapshot: DashboardSnapshot) {
            restoreSnapshotCalls++
        }

        override suspend fun restoreClassicDesign() {
            restoreClassicCalls++
            revision++
        }

        override suspend fun setNotices(state: DashboardNotices.State) {
            notices.value = state
        }
    }

    private companion object {
        fun placedWidget() = PlacedWidget(
            WidgetInstance("steps-1", StepsWidget.id),
            StepsWidget,
            WidgetVariant.GAUGE,
            0,
        )
    }
}
