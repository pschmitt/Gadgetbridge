package nodomain.freeyourgadget.gadgetbridge.activities.debug.data

/**
 * @property version the database version, or null if the database could not be read
 * @property tables the table names, sorted ignoring case. Empty if the database could not be read
 * @property hasOldActivityDatabase whether the legacy Activity database exists
 */
data class DatabaseOverview(
    val version: Int?,
    val tables: List<String>,
    val hasOldActivityDatabase: Boolean,
)

/**
 * Inspects and deletes the Gadgetbridge database for the debug screens. All functions are safe to call from the main
 * thread, and report failures through their return values.
 */
interface DatabaseDebugRepository {
    suspend fun getOverview(): DatabaseOverview

    /**
     * Deletes the database and the legacy Activity database, and recreates the former with empty tables.
     *
     * @return true on successful deletion
     */
    suspend fun deleteDatabase(): Boolean

    /**
     * Deletes the legacy Activity database.
     *
     * @return true on successful deletion
     */
    suspend fun deleteOldDatabase(): Boolean
}
