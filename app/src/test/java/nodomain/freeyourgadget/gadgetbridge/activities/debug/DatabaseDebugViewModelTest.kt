package nodomain.freeyourgadget.gadgetbridge.activities.debug

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nodomain.freeyourgadget.gadgetbridge.activities.debug.data.DatabaseDebugRepository
import nodomain.freeyourgadget.gadgetbridge.activities.debug.data.DatabaseOverview
import nodomain.freeyourgadget.gadgetbridge.test.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseDebugViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = mock(DatabaseDebugRepository::class.java)
    private val viewModel = DatabaseDebugViewModel(repository)

    @Test
    fun nothingLoadsUntilRefresh() = runTest {
        advanceUntilIdle()

        verify(repository, never()).getOverview()
        assertEquals(DatabaseDebugUiState(), viewModel.uiState.value)
    }

    @Test
    fun refreshMapsTheOverviewToUiState() = runTest {
        `when`(repository.getOverview()).thenReturn(
            DatabaseOverview(version = 42, tables = listOf("a", "b"), hasOldActivityDatabase = true)
        )

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(
            DatabaseDebugUiState(version = 42, tables = listOf("a", "b"), showDeleteOldDatabase = true),
            viewModel.uiState.value
        )
    }

    @Test
    fun deleteDatabaseEmitsItsResult() = runTest {
        `when`(repository.deleteDatabase()).thenReturn(true, false)

        viewModel.deleteDatabase()
        assertEquals(DatabaseDebugEvent.DatabaseDeleted(true), viewModel.events.first())

        viewModel.deleteDatabase()
        assertEquals(DatabaseDebugEvent.DatabaseDeleted(false), viewModel.events.first())

        verify(repository, times(2)).deleteDatabase()
    }

    @Test
    fun deleteOldDatabaseEmitsItsResultAndRefreshes() = runTest {
        `when`(repository.deleteOldDatabase()).thenReturn(true)
        `when`(repository.getOverview()).thenReturn(
            overview(hasOldActivityDatabase = true),
            overview(hasOldActivityDatabase = false),
        )
        viewModel.refresh()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.showDeleteOldDatabase)

        viewModel.deleteOldDatabase()
        assertEquals(DatabaseDebugEvent.OldDatabaseDeleted(true), viewModel.events.first())
        advanceUntilIdle()

        verify(repository, times(1)).deleteOldDatabase()
        assertFalse(viewModel.uiState.value.showDeleteOldDatabase)
    }

    @Test
    fun deleteOldDatabaseEmitsAFailureAndRefreshes() = runTest {
        `when`(repository.deleteOldDatabase()).thenReturn(false)
        `when`(repository.getOverview()).thenReturn(overview(hasOldActivityDatabase = true))

        viewModel.deleteOldDatabase()
        assertEquals(DatabaseDebugEvent.OldDatabaseDeleted(false), viewModel.events.first())
        advanceUntilIdle()

        // Only the refresh can show the option, because it starts hidden
        assertTrue(viewModel.uiState.value.showDeleteOldDatabase)
    }

    @Test
    fun aSecondDeleteIsIgnoredWhileOneIsInProgress() = runTest {
        `when`(repository.deleteDatabase()).thenReturn(true)

        viewModel.deleteDatabase()
        viewModel.deleteDatabase()
        advanceUntilIdle()

        verify(repository, times(1)).deleteDatabase()
    }

    @Test
    fun anOldDatabaseDeleteIsIgnoredWhileADeleteIsInProgress() = runTest {
        `when`(repository.deleteDatabase()).thenReturn(true)

        viewModel.deleteDatabase()
        viewModel.deleteOldDatabase()
        advanceUntilIdle()

        verify(repository, never()).deleteOldDatabase()
    }

    @Test
    fun aDeleteIsIgnoredWhileAnOldDatabaseDeleteIsInProgress() = runTest {
        `when`(repository.deleteOldDatabase()).thenReturn(true)
        `when`(repository.getOverview()).thenReturn(overview(hasOldActivityDatabase = false))

        viewModel.deleteOldDatabase()
        viewModel.deleteDatabase()
        advanceUntilIdle()

        verify(repository, never()).deleteDatabase()
    }

    @Test
    fun deleteInProgressClearsWhenTheDeleteFinishes() = runTest {
        `when`(repository.deleteDatabase()).thenReturn(true)

        viewModel.deleteDatabase()
        assertTrue(viewModel.uiState.value.deleteInProgress)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.deleteInProgress)

        viewModel.deleteDatabase()
        advanceUntilIdle()
        verify(repository, times(2)).deleteDatabase()
    }

    @Test
    fun refreshDuringADeleteKeepsDeleteInProgress() = runTest {
        var inProgressDuringDelete: Boolean? = null
        `when`(repository.getOverview()).thenReturn(overview(hasOldActivityDatabase = false))
        `when`(repository.deleteDatabase()).thenAnswer {
            inProgressDuringDelete = viewModel.uiState.value.deleteInProgress
            true
        }

        // The refresh is queued first, so it finishes before the delete reaches the repository
        viewModel.refresh()
        viewModel.deleteDatabase()
        advanceUntilIdle()

        assertEquals(true, inProgressDuringDelete)
        assertEquals(1, viewModel.uiState.value.version)
    }

    private fun overview(hasOldActivityDatabase: Boolean) =
        DatabaseOverview(version = 1, tables = emptyList(), hasOldActivityDatabase = hasOldActivityDatabase)
}
