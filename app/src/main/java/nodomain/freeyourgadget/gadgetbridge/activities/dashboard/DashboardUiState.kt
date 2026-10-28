package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardSettings
import nodomain.freeyourgadget.gadgetbridge.widgets.PlacedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import java.time.LocalDate

/**
 * The loading state of one configured widget instance.
 */
sealed interface WidgetState {
    data object Loading : WidgetState
    data class Ready(val data: Any?) : WidgetState
    data class Error(val cause: Throwable) : WidgetState
}

/**
 * One widget instance and the state of its data.
 */
data class DashboardItemState(
    val placed: PlacedWidget,
    val state: WidgetState,
) {
    val instanceId: String get() = placed.instance.instanceId
}

/**
 * The state of the edit mode, or null when the dashboard is not being edited.
 */
data class DashboardEditState(val canDiscard: Boolean)

/**
 * A notice above the widget grid.
 */
sealed interface DashboardNotice {
    data object Migration : DashboardNotice

    data class NewWidgets(val deviceAddress: String, val deviceName: String) : DashboardNotice
}

/**
 * Everything the DashboardFragment renders.
 */
data class DashboardUiState(
    val date: LocalDate,
    val isToday: Boolean,
    val settings: DashboardSettings,
    val items: List<DashboardItemState>,
    val editState: DashboardEditState?,
    val notices: List<DashboardNotice>,
) {
    val cardsEnabled: Boolean get() = settings.cardsEnabled
}

/**
 * A one-shot action for the view, such as scrolling to a new widget or showing an undo snackbar.
 */
sealed interface DashboardEvent {
    data class WidgetAdded(val instanceId: String) : DashboardEvent

    data object OpenWidgetPicker : DashboardEvent

    data object NoWidgetsAvailable : DashboardEvent

    data class ShowUndo(val action: DashboardUndoAction) : DashboardEvent

    data class OpenWidgetSettings(val columns: Int, val instance: WidgetInstance) : DashboardEvent
}

enum class DashboardUndoAction {
    DELETE_WIDGET,
    RESTORE_CLASSIC_DESIGN,
}
