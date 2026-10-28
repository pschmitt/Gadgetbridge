package nodomain.freeyourgadget.gadgetbridge.widgets

import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * The notices that the dashboard displays above the widgets.
 */
object DashboardNotices {
    private val LOG: Logger = LoggerFactory.getLogger(DashboardNotices::class.java)

    private const val PREF_MIGRATION = "pref_dashboard_notice_migration"
    private const val PREF_DEVICE = "pref_dashboard_notice_device"

    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    /**
     * The notices to display:
     * - dashboard migration
     * - new widgets of the last paired device with [deviceAddress]
     */
    data class State(val migration: Boolean, val deviceAddress: String?) {
        companion object {
            val NONE = State(migration = false, deviceAddress = null)
        }
    }

    fun state(): State {
        val prefs = GBApplication.getPrefs()
        val deviceAddress = prefs.getString(PREF_DEVICE, "").ifBlank { null }
        return State(prefs.getBoolean(PREF_MIGRATION, false), deviceAddress)
    }

    fun set(state: State) {
        LOG.debug("Setting the dashboard notices to {}", state)
        GBApplication.getPrefs().preferences.edit {
            putBoolean(PREF_MIGRATION, state.migration)
            if (state.deviceAddress != null) {
                putString(PREF_DEVICE, state.deviceAddress)
            } else {
                remove(PREF_DEVICE)
            }
        }
        _changes.tryEmit(Unit)
    }
}
