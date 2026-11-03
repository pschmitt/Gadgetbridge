package nodomain.freeyourgadget.gadgetbridge

import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

class GBExceptionHandlerTest : TestBase() {
    private class RecordingHandler : Thread.UncaughtExceptionHandler {
        var count = 0
        override fun uncaughtException(t: Thread, e: Throwable) {
            count++
        }
    }

    private var original: Thread.UncaughtExceptionHandler? = null
    private val base = RecordingHandler()

    @Before
    fun installBaseHandler() {
        original = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler(base)
    }

    @After
    fun restoreOriginalHandler() {
        Thread.setDefaultUncaughtExceptionHandler(original)
    }

    @Test
    fun registerMakesItTheDefaultAndForwardsToThePreviousOne() {
        val handler = GBExceptionHandler(false)
        handler.register()

        assertSame(Thread.getDefaultUncaughtExceptionHandler(), handler)
        handler.uncaughtException(Thread.currentThread(), RuntimeException("test"))
        assertEquals(1, base.count)
    }

    @Test
    fun registerTwiceDoesNotForwardToItself() {
        val handler = GBExceptionHandler(false)
        handler.register()
        handler.register()

        // would recurse (and never reach base) if the handler had become its own previous handler
        handler.uncaughtException(Thread.currentThread(), RuntimeException("test"))
        assertEquals(1, base.count)

        handler.unregister()
        assertSame(Thread.getDefaultUncaughtExceptionHandler(), base)
    }

    @Test
    fun unregisterLeavesAHandlerInstalledOnTopInPlace() {
        val handler = GBExceptionHandler(false)
        handler.register()
        val outer = Thread.UncaughtExceptionHandler { t, e -> handler.uncaughtException(t, e) }
        Thread.setDefaultUncaughtExceptionHandler(outer)

        handler.unregister()

        assertSame(Thread.getDefaultUncaughtExceptionHandler(), outer)
        // the chain through this handler to the previous one is still intact
        outer.uncaughtException(Thread.currentThread(), RuntimeException("test"))
        assertEquals(1, base.count)
    }

    @Test
    fun unregisterWithoutRegisterDoesNothing() {
        GBExceptionHandler(false).unregister()

        assertSame(Thread.getDefaultUncaughtExceptionHandler(), base)
    }
}
