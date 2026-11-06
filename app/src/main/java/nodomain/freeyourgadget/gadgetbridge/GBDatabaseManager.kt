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
     * @return true if the legacy (pre 0.12) Activity database exists
     */
    fun hasOldActivityDatabase(): Boolean = DBHelper(context).existsDB("ActivityDatabase")

    /**
     * Deletes the legacy (pre 0.12) Activity database
     *
     * @return true on successful deletion
     */
    fun deleteOldActivityDatabase(): Boolean {
        LOG.trace("Deleting old activity database")
        var result = true
        if (hasOldActivityDatabase()) {
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

    private companion object {
        private val LOG = LoggerFactory.getLogger(GBDatabaseManager::class.java)
    }
}
