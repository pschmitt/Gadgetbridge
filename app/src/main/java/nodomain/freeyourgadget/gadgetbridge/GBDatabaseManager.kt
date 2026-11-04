package nodomain.freeyourgadget.gadgetbridge

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantReadWriteLock
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.withLock

/**
 * Owns the Gadgetbridge database and the lock that guards every access to it.
 *
 * Inject this class with Hilt. The static functions in the companion object are kept so that
 * existing callers keep working, and forward to the instance of the current application.
 */
@Singleton
class GBDatabaseManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dbLock = ReentrantReadWriteLock(true)
    private val gbDatabase = GBDatabase(context)

    fun closeDatabase() {
        LOG.trace("Trying to close database")
        dbLock.writeLock().withLock {
            gbDatabase.closeDatabase()
        }
    }

    fun setupDatabase() {
        LOG.trace("Setting up database")
        dbLock.writeLock().withLock {
            gbDatabase.setupDatabase()
        }
    }

    /**
     * Returns the DBHandler instance for reading/writing or throws GBException
     * when that was not successful
     * If acquiring was successful, callers must call close when they
     * are done (from the same thread that acquired the lock!
     *
     * Callers must not hold a reference to the returned instance because it
     * will be invalidated at some point.
     *
     * @return the DBHandler
     * @throws GBException if the lock could not be acquired, or the database is not open
     */
    @Throws(GBException::class)
    fun acquireWrite(): DBHandler {
        try {
            LOG.trace("Trying to acquire write lock")
            if (dbLock.writeLock().tryLock(30, TimeUnit.SECONDS)) {
                LOG.trace("Acquired write lock")
                return createHandler(dbLock.writeLock())
            }
        } catch (e: InterruptedException) {
            LOG.error("Interrupted while waiting for write DB lock", e)
        }
        throw GBException("Failed to acquire database write lock")
    }

    @Throws(GBException::class)
    fun acquireReadOnly(): DBHandler {
        try {
            LOG.trace("Trying to acquire read lock")
            if (dbLock.readLock().tryLock(30, TimeUnit.SECONDS)) {
                LOG.trace("Acquired read lock")
                return createHandler(dbLock.readLock())
            }
        } catch (e: InterruptedException) {
            LOG.error("Interrupted while waiting for read DB lock", e)
        }
        throw GBException("Failed to acquire database read lock")
    }

    /**
     * Wraps the held [lock] in a handler, or releases it and throws if the database is closed, for
     * example by [closeDatabase] while the caller was waiting for the lock.
     */
    @Throws(GBException::class)
    private fun createHandler(lock: Lock): DBHandler {
        val session = gbDatabase.session
        if (session == null) {
            lock.unlock()
            throw GBException("Database is not open")
        }
        return LockHandler(lock, gbDatabase.daoMaster, session)
    }

    /**
     * Deletes both the old Activity database and the new one recreates it with empty tables.
     *
     * @return true on successful deletion
     */
    fun deleteActivityDatabase(): Boolean {
        LOG.trace("Deleting activity database")
        dbLock.writeLock().withLock {
            try {
                gbDatabase.closeDatabase()
                var result = deleteOldActivityDatabase()
                result = result and context.deleteDatabase(GBDatabase.DATABASE_NAME)
                return result
            } finally {
                gbDatabase.setupDatabase()
            }
        }
    }

    @Throws(IOException::class)
    fun exportDB(destFile: File) {
        LOG.trace("Exporting database to file")
        dbLock.writeLock().withLock {
            gbDatabase.exportDB(destFile)
        }
    }

    @Throws(IOException::class)
    fun exportDB(dest: OutputStream) {
        LOG.trace("Exporting database to OutputStream")
        dbLock.writeLock().withLock {
            gbDatabase.exportDB(dest)
        }
    }

    /**
     * Deletes the legacy (pre 0.12) Activity database
     *
     * @return true on successful deletion
     */
    fun deleteOldActivityDatabase(): Boolean {
        LOG.trace("Deleting old activity database")
        val dbHelper = DBHelper(context)
        var result = true
        if (dbHelper.existsDB("ActivityDatabase")) {
            result = context.deleteDatabase("ActivityDatabase")
        }
        return result
    }

    @Throws(IllegalStateException::class, IOException::class)
    fun importDB(fromFile: File) {
        FileInputStream(fromFile).use { importDB(it) }
    }

    @Throws(IllegalStateException::class, IOException::class)
    fun importDB(inputStream: InputStream) {
        LOG.trace("Importing database")
        dbLock.writeLock().withLock {
            gbDatabase.importDB(inputStream)
        }
    }

    /**
     * Static access to the [GBDatabaseManager] of the current application.
     *
     * Every function calls the instance explicitly: an unqualified call here would resolve to the
     * companion function itself. Functions with the same JVM signature as an instance method cannot
     * be [JvmStatic], so Java callers must use the instance for those.
     */
    companion object {
        private val LOG = LoggerFactory.getLogger(GBDatabaseManager::class.java)

        private fun manager(): GBDatabaseManager = GBApplication.app().databaseManager

        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        fun closeDatabase() {
            manager().closeDatabase()
        }

        /**
         * @param context ignored, the database always uses the application context
         */
        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun setupDatabase(context: Context) {
            manager().setupDatabase()
        }

        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @Throws(GBException::class)
        fun acquireWrite(): DBHandler = manager().acquireWrite()

        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @Throws(GBException::class)
        fun acquireReadOnly(): DBHandler = manager().acquireReadOnly()

        /**
         * @param context ignored, the database always uses the application context
         */
        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun deleteActivityDatabase(context: Context): Boolean = manager().deleteActivityDatabase()

        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @Throws(IOException::class)
        fun exportDB(destFile: File) {
            manager().exportDB(destFile)
        }

        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @Throws(IOException::class)
        fun exportDB(dest: OutputStream) {
            manager().exportDB(dest)
        }

        /**
         * @param context ignored, the database always uses the application context
         */
        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @JvmStatic
        @Suppress("UNUSED_PARAMETER")
        fun deleteOldActivityDatabase(context: Context): Boolean = manager().deleteOldActivityDatabase()

        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @Throws(IllegalStateException::class, IOException::class)
        fun importDB(fromFile: File) {
            manager().importDB(fromFile)
        }

        @Deprecated("Use Hilt DI to inject GBDatabaseManager instead.")
        @Throws(IllegalStateException::class, IOException::class)
        fun importDB(inputStream: InputStream) {
            manager().importDB(inputStream)
        }
    }
}
