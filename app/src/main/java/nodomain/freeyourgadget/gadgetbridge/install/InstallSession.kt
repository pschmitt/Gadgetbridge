package nodomain.freeyourgadget.gadgetbridge.install

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.LoggerFactory

/**
 * One attempt to install one or more source files on a device. The device support reports the
 * outcome of each item to the session.
 */
class InstallSession internal constructor(
    val id: Long,
    val device: GBDevice,
    internal val stateMachine: InstallStateMachine,
) {
    @Volatile
    internal var handler: InstallHandlerV2? = null

    @Volatile
    var options: Any? = null
        internal set

    internal var stateJob: Job? = null

    val state: StateFlow<InstallState>
        get() = stateMachine.state

    val items: List<InstallItem>
        get() = stateMachine.items

    fun progress(item: InstallItem, percent: Int) {
        stateMachine.progress(item, percent)
    }

    fun installed(item: InstallItem) {
        LOG.debug("Session {}: {} installed", id, item.displayName)
        stateMachine.outcome(item, ItemStatus.Installed)
    }

    fun failed(item: InstallItem, reason: String?) {
        LOG.debug("Session {}: {} failed: {}", id, item.displayName, reason)
        stateMachine.outcome(item, ItemStatus.Failed(reason))
    }

    fun skipped(item: InstallItem) {
        LOG.debug("Session {}: {} skipped", id, item.displayName)
        stateMachine.outcome(item, ItemStatus.Skipped)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(InstallSession::class.java)
    }
}
