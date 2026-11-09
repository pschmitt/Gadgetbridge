package nodomain.freeyourgadget.gadgetbridge.activities.debug.data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBDatabaseManager
import nodomain.freeyourgadget.gadgetbridge.di.IoDispatcher
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import javax.inject.Inject

/**
 * The [DatabaseDebugRepository] backed by [GBDatabaseManager].
 *
 * The manager is blocking, so everything runs on [ioDispatcher]. A database lock must be released on the thread that
 * acquired it, so nothing may suspend while a lock is held.
 */
class DatabaseDebugRepositoryImpl @Inject constructor(
    private val databaseManager: GBDatabaseManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : DatabaseDebugRepository {
    override suspend fun getOverview(): DatabaseOverview = withContext(ioDispatcher) {
        var version: Int? = null
        var tables = emptyList<String>()

        try {
            databaseManager.acquireReadOnly().use { db ->
                val tableNames = mutableListOf<String>()
                db.database.rawQuery(
                    """
                        SELECT name
                        FROM sqlite_master
                        WHERE type='table'
                          AND name NOT LIKE 'android_%'
                          AND name NOT LIKE 'sqlite_%'
                        """.trimIndent(),
                    null
                ).use {
                    while (it.moveToNext()) {
                        tableNames.add(it.getString(it.getColumnIndexOrThrow("name")))
                    }
                }
                version = db.database.version
                tables = tableNames.sortedWith(String.CASE_INSENSITIVE_ORDER)
            }
        } catch (e: Exception) {
            LOG.error("Error accessing database", e)
        }

        DatabaseOverview(version, tables, databaseManager.hasOldActivityDatabase())
    }

    override suspend fun deleteDatabase(): Boolean = withContext(ioDispatcher) {
        try {
            databaseManager.deleteActivityDatabase()
        } catch (e: Exception) {
            LOG.error("Error deleting database", e)
            false
        }
    }

    override suspend fun deleteOldDatabase(): Boolean = withContext(ioDispatcher) {
        try {
            databaseManager.deleteOldActivityDatabase()
        } catch (e: Exception) {
            LOG.error("Error deleting old database", e)
            false
        }
    }

    private companion object {
        private val LOG: Logger = LoggerFactory.getLogger(DatabaseDebugRepositoryImpl::class.java)
    }
}
