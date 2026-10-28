package nodomain.freeyourgadget.gadgetbridge.install

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.slf4j.LoggerFactory

/**
 * Model the state machine of one [InstallSession]. A call that is not a legal transition for the current state
 * does not change it.
 */
class InstallStateMachine(initial: InstallState = InstallState.Preparing) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<InstallState> = _state.asStateFlow()

    @Volatile
    var parsedFiles: ParsedFiles.Supported? = null
        private set

    private var lastCheck: DeviceCheck? = null

    val items: List<InstallItem>
        get() = parsedFiles?.items.orEmpty()

    @Synchronized
    fun supported(files: ParsedFiles.Supported, check: DeviceCheck) {
        if (_state.value != InstallState.Preparing) {
            LOG.warn("Ignoring parsed files in {}", _state.value)
            return
        }
        parsedFiles = files
        lastCheck = check
        apply(files, check)
    }

    @Synchronized
    fun unsupported(reason: String) {
        if (_state.value != InstallState.Preparing) {
            LOG.warn("Ignoring rejection in {}", _state.value)
            return
        }
        _state.value = InstallState.Blocked(reason, emptyList())
    }

    @Synchronized
    fun deviceChecked(check: DeviceCheck) {
        lastCheck = check
        val files = parsedFiles ?: return
        when (_state.value) {
            is InstallState.WaitingForDevice, is InstallState.Ready, is InstallState.Blocked -> apply(files, check)
            else -> {}
        }
    }

    @Synchronized
    fun start(): Boolean {
        val ready = _state.value as? InstallState.Ready
        if (ready == null) {
            LOG.warn("Ignoring start in {}", _state.value)
            return false
        }
        val items = ready.parsedFiles.items.map { ItemProgress(it, ItemStatus.Pending) }
        _state.value = if (items.isEmpty()) InstallState.Finished(items) else InstallState.Installing(items)
        return true
    }

    @Synchronized
    fun progress(item: InstallItem, percent: Int) {
        update(item, "progress") { ItemStatus.Transferring(percent.coerceIn(0, 100)) }
    }

    @Synchronized
    fun outcome(item: InstallItem, outcome: ItemStatus) {
        require(outcome.isOutcome) { "Not an outcome: $outcome" }
        update(item, "outcome") { outcome }
    }

    @Synchronized
    fun deviceLost(reason: String) {
        val installing = _state.value as? InstallState.Installing ?: return
        LOG.warn("Device connection lost during install: {}", reason)
        _state.value = InstallState.Finished(installing.items.map {
            if (it.status.isOutcome) it else ItemProgress(it.item, ItemStatus.Failed(reason))
        })
    }

    @Synchronized
    fun restart() {
        val files = parsedFiles
        val check = lastCheck
        if (_state.value !is InstallState.Finished || files == null || check == null) {
            LOG.warn("Ignoring restart in {}", _state.value)
            return
        }
        apply(files, check)
    }

    private fun apply(files: ParsedFiles.Supported, check: DeviceCheck) {
        _state.value = when (check) {
            is DeviceCheck.NotReady -> InstallState.WaitingForDevice(files, check.reason)
            is DeviceCheck.Checked -> when (val compatibility = check.compatibility) {
                is Compatibility.Compatible -> InstallState.Ready(files, compatibility.warnings)
                is Compatibility.Incompatible -> InstallState.Blocked(compatibility.reason, files.items)
            }
        }
    }

    private fun update(item: InstallItem, report: String, status: () -> ItemStatus) {
        val installing = _state.value as? InstallState.Installing
        if (installing == null) {
            LOG.warn("Ignoring {} for {} in {}", report, item.displayName, _state.value)
            return
        }
        val index = installing.items.indexOfFirst { it.item === item }
        if (index < 0) {
            LOG.warn("Ignoring {} for unknown item {}", report, item.displayName)
            return
        }
        if (installing.items[index].status.isOutcome) {
            LOG.warn("Ignoring {} for {}, it already has an outcome", report, item.displayName)
            return
        }
        val items = installing.items.toMutableList()
        items[index] = ItemProgress(item, status())
        _state.value = if (items.all { it.status.isOutcome }) {
            InstallState.Finished(items)
        } else {
            InstallState.Installing(items)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(InstallStateMachine::class.java)
    }
}
