package nodomain.freeyourgadget.gadgetbridge.test

import nodomain.freeyourgadget.gadgetbridge.GBHiltApplication
import org.slf4j.LoggerFactory

class GBTestApplication : GBHiltApplication() {
    override fun migratePrefsIfNeeded() {
        // In tests, do not migrate preferences
        // FIXME: This is not ideal. In tests, do not migrate preferences. We should be able to initialize
        //  the database before the application is created so that this works and is actually testable, but
        //  right now all it does is spam the logs with dozens of stack traces for failed preference migrations
        LOG.info("Skipping preference migration during tests")
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(GBTestApplication::class.java)
    }
}
