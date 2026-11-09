package nodomain.freeyourgadget.gadgetbridge.activities.debug.data

import android.content.Context
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nodomain.freeyourgadget.gadgetbridge.GBDatabase
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on the test thread, which already holds the write lock from [TestBase.setUp]. The lock is reentrant, so the
 * repository can still acquire it.
 */
class DatabaseDebugRepositoryImplTest : TestBase() {
    private fun TestScope.repository() =
        DatabaseDebugRepositoryImpl(app.databaseManager, StandardTestDispatcher(testScheduler))

    private fun createOldDatabase() {
        app.openOrCreateDatabase(OLD_DATABASE, Context.MODE_PRIVATE, null).close()
    }

    @Test
    fun overviewHasTheVersionAndSortedTables() = runTest {
        dbHandler.database.execSQL("CREATE TABLE zebra (id INTEGER)")
        dbHandler.database.execSQL("CREATE TABLE Apple (id INTEGER)")
        dbHandler.database.execSQL("CREATE TABLE banana (id INTEGER)")

        val overview = repository().getOverview()

        assertEquals(dbHandler.database.version, overview.version)
        assertEquals(overview.tables.sortedWith(String.CASE_INSENSITIVE_ORDER), overview.tables)
        val added = overview.tables.filter { it in setOf("zebra", "Apple", "banana") }
        assertEquals(listOf("Apple", "banana", "zebra"), added)
    }

    @Test
    fun overviewExcludesInternalTables() = runTest {
        val tables = repository().getOverview().tables

        assertTrue(tables.isNotEmpty())
        assertTrue(tables.none { it.startsWith("android_") || it.startsWith("sqlite_") })
    }

    @Test
    fun overviewOfAClosedDatabaseStillReportsTheOldDatabase() = runTest {
        createOldDatabase()
        app.databaseManager.closeDatabase()

        val overview = repository().getOverview()

        assertNull(overview.version)
        assertTrue(overview.tables.isEmpty())
        assertTrue(overview.hasOldActivityDatabase)

        app.databaseManager.setupDatabase()
    }

    @Test
    fun deleteOldDatabaseRemovesIt() = runTest {
        val repository = repository()
        assertFalse(repository.getOverview().hasOldActivityDatabase)

        createOldDatabase()
        assertTrue(repository.getOverview().hasOldActivityDatabase)

        assertTrue(repository.deleteOldDatabase())
        assertFalse(repository.getOverview().hasOldActivityDatabase)
    }

    @Test
    fun deleteDatabaseRemovesTheFilesAndLeavesTheDatabaseUsable() = runTest {
        // Tests use an in-memory database, so the file that is deleted on a device has to be created
        app.openOrCreateDatabase(GBDatabase.DATABASE_NAME, Context.MODE_PRIVATE, null).close()
        createOldDatabase()
        dbHandler.database.execSQL("CREATE TABLE to_delete (id INTEGER)")
        val repository = repository()

        assertTrue(repository.deleteDatabase())

        assertFalse(app.getDatabasePath(GBDatabase.DATABASE_NAME).exists())
        val overview = repository.getOverview()
        assertNotNull(overview.version)
        assertFalse(overview.hasOldActivityDatabase)
        assertFalse("to_delete" in overview.tables)
        assertTrue(overview.tables.isNotEmpty())
    }

    @Test
    fun deleteDatabaseReportsAFailureAndLeavesTheDatabaseUsable() = runTest {
        // There is no database file to delete
        val repository = repository()

        assertFalse(repository.deleteDatabase())

        assertNotNull(repository.getOverview().version)
    }

    private companion object {
        const val OLD_DATABASE = "ActivityDatabase"
    }
}
