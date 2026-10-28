package nodomain.freeyourgadget.gadgetbridge.service.wifi

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A reference-counted connection [C] to a device, over the Wi-Fi hotspot of the device.
 * [S] is the set of connection steps of the subclass, in the order they occur from [stoppedState] to [connectedState].
 */
abstract class WifiSession<C : Closeable, S : Enum<S>>(
    protected val context: Context,
    protected val device: GBDevice,
    private val stoppedState: S,
    private val connectedState: S,
) {
    fun interface Listener<in S> {
        fun onStateChanged(state: S)
    }

    /**
     * Keeps the session open until it is released or until it expires. It does not start the session.
     */
    interface Hold {
        fun release()
    }

    /**
     * The connection step while the connection starts.
     *
     * @property step the step number, from 1 to [stepCount]
     */
    class Progress(@StringRes val text: Int, val step: Int, val stepCount: Int)

    @Volatile
    var state: S = stoppedState
        private set

    val isStopped: Boolean
        get() = state == stoppedState

    /**
     * The progress of the current state, or null when the session is stopped or connected.
     */
    val progress: Progress?
        get() {
            val current = state
            if (current == stoppedState || current == connectedState) {
                return null
            }
            return Progress(
                stateText(current),
                current.ordinal - stoppedState.ordinal,
                connectedState.ordinal - stoppedState.ordinal - 1
            )
        }

    private val listeners = CopyOnWriteArrayList<Listener<S>>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    // Guards the reference count and the idle timer. It is never held for long, so the UI thread can use it.
    private val refLock = Any()
    private var refCount = 0
    private var idleTeardown: ScheduledFuture<*>? = null

    // Guards the connection. It is held while the connection starts.
    private val connectionLock = Any()

    @Volatile
    private var connection: C? = null

    @Volatile
    private var startingThread: Thread? = null

    @Volatile
    protected var disposed = false
        private set

    /**
     * Opens the connection, and calls [setState] for each step.
     */
    @Throws(Exception::class)
    protected abstract fun open(): C

    /**
     * Stops the services on the device that [open] started. It must not send commands when [disposed] is true.
     */
    protected abstract fun shutdown()

    /**
     * The text that describes a state to the user.
     */
    @StringRes
    abstract fun stateText(state: S): Int

    /**
     * Opens the connection if necessary, and blocks until it is ready. Each call must have one [release] call.
     */
    @Throws(IOException::class)
    fun acquire(): C {
        addRef()
        try {
            synchronized(connectionLock) {
                return connection ?: start()
            }
        } catch (e: IOException) {
            release()
            throw e
        }
    }

    /**
     * Returns the connection when it is open, and does not start it. Each non-null result must have one
     * [release] call.
     */
    fun acquireIfOpen(): C? {
        synchronized(refLock) {
            val current = connection ?: return null
            addRef()
            return current
        }
    }

    fun release() {
        synchronized(refLock) {
            refCount = maxOf(0, refCount - 1)
            LOG.debug("Released session for {}, {} references left", device.address, refCount)
            if (refCount == 0 && !scheduler.isShutdown) {
                cancelIdleTeardown()
                idleTeardown = scheduler.schedule({ closeIfIdle() }, IDLE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            }
        }
    }

    fun hold(maxDurationMs: Long = MAX_HOLD_MS): Hold {
        addRef()
        LOG.debug("Hold on session for {} for up to {} ms", device.address, maxDurationMs)
        val released = AtomicBoolean(false)
        val hold = object : Hold {
            override fun release() {
                if (released.compareAndSet(false, true)) {
                    LOG.debug("Hold on session for {} released", device.address)
                    this@WifiSession.release()
                }
            }
        }
        if (!scheduler.isShutdown) {
            scheduler.schedule({
                if (!released.get()) {
                    LOG.warn("Hold on {} expired", device.address)
                }
                hold.release()
            }, maxDurationMs, TimeUnit.MILLISECONDS)
        }
        return hold
    }

    /**
     * Closes the connection now - also if it is in use or starting. Does not block.
     */
    fun close() {
        val starting = startingThread
        LOG.info("Close requested for session of {}, state={}, starting={}", device.address, state, starting != null)
        starting?.interrupt()
        if (!scheduler.isShutdown) {
            scheduler.execute {
                closeNow()
            }
        }
    }

    /**
     * Closes the connection after the device disconnected.
     */
    fun dispose() {
        LOG.info("Disposing session for {}", device.address)
        disposed = true
        close()
        scheduler.shutdown()
    }

    fun addListener(listener: Listener<S>) {
        listeners.add(listener)
    }

    fun removeListener(listener: Listener<S>) {
        listeners.remove(listener)
    }

    protected fun setState(newState: S) {
        if (state == newState) {
            return
        }
        LOG.debug("Session state for {}: {}", device.address, newState)
        state = newState
        if (newState == stoppedState) {
            WifiSessionNotification.cancel(context, device.address)
        } else {
            WifiSessionNotification.update(context, device, stateText(newState), newState == connectedState)
        }
        mainHandler.post {
            for (listener in listeners) {
                listener.onStateChanged(newState)
            }
        }
    }

    private fun start(): C {
        startingThread = Thread.currentThread()
        if (disposed) {
            startingThread = null
            throw IOException(context.getString(R.string.wifi_ftp_error_closed))
        }
        val start = System.currentTimeMillis()
        LOG.info("Opening session for {}", device.address)
        try {
            val opened = open()
            connection = opened
            setState(connectedState)
            LOG.debug("Opened session for {} in {} ms", device.address, System.currentTimeMillis() - start)
            return opened
        } catch (e: Exception) {
            LOG.error(
                "Failed to open session for {} after {} ms in state {}",
                device.address,
                System.currentTimeMillis() - start,
                state,
                e
            )
            teardown(detachConnection())
            if (e is InterruptedException || Thread.interrupted()) {
                throw IOException(context.getString(R.string.wifi_ftp_error_closed), e)
            }
            throw e as? IOException ?: IOException(e.message ?: e.javaClass.simpleName, e)
        } finally {
            startingThread = null
        }
    }

    private fun addRef() {
        synchronized(refLock) {
            cancelIdleTeardown()
            refCount++
            LOG.debug("Acquired session for {}, {} references", device.address, refCount)
        }
    }

    private fun closeNow() {
        synchronized(refLock) {
            cancelIdleTeardown()
        }
        synchronized(connectionLock) {
            teardown(detachConnection())
        }
    }

    private fun closeIfIdle() {
        synchronized(connectionLock) {
            // Check the reference count and detach the connection in one refLock block, so that
            // acquireIfOpen cannot get a connection that is closed
            val idle = synchronized(refLock) {
                if (refCount > 0) {
                    return
                }
                detachConnection()
            } ?: return
            LOG.info("Closing idle session for {}", device.address)
            teardown(idle)
        }
    }

    private fun detachConnection(): C? {
        synchronized(refLock) {
            val current = connection
            connection = null
            return current
        }
    }

    private fun teardown(current: C?) {
        if (state != stoppedState) {
            LOG.info("Stopping session for {} in state {}", device.address, state)
        }
        try {
            current?.close()
        } catch (e: IOException) {
            LOG.warn("Failed to close the connection to {}", device.address, e)
        }
        if (state != stoppedState) {
            shutdown()
        }
        setState(stoppedState)
    }

    private fun cancelIdleTeardown() {
        idleTeardown?.cancel(false)
        idleTeardown = null
    }

    companion object {
        private val LOG: Logger = LoggerFactory.getLogger(WifiSession::class.java)

        private const val IDLE_TIMEOUT_MS = 10_000L
        const val MAX_HOLD_MS = 5 * 60_000L
    }
}
