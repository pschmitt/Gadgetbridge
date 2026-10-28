package nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data

import kotlinx.coroutines.flow.Flow
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardNotices
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardSnapshot
import nodomain.freeyourgadget.gadgetbridge.widgets.DeletedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.PlacedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance

/**
 * The data source of the dashboard: the widget layout, its settings, and the notices above the grid.
 *
 * Implementations must not perform blocking work on the calling thread.
 */
interface DashboardRepository {
    fun layoutFlow(columns: Int): Flow<List<PlacedWidget>>

    fun settingsFlow(): Flow<DashboardSettings>

    fun noticesFlow(): Flow<DashboardNotices.State>

    fun currentSettings(): DashboardSettings

    fun currentNotices(): DashboardNotices.State

    fun isDeviceInScope(device: GBDevice): Boolean

    fun consumeStartEditingRequest(): Boolean

    fun deviceByAddress(address: String): GBDevice?

    fun availableWidgets(): List<GBWidget<*>>

    suspend fun newWidgets(device: GBDevice): List<GBWidget<*>>

    suspend fun add(columns: Int, typeId: String, variantId: String): WidgetInstance

    suspend fun arrange(columns: Int, arrangement: List<Pair<String, Int>>)

    suspend fun duplicate(columns: Int, instanceId: String): WidgetInstance?

    suspend fun delete(columns: Int, instanceId: String): DeletedWidget?

    suspend fun restore(deleted: DeletedWidget)

    suspend fun setVariant(columns: Int, instanceId: String, variantId: String)

    /**
     * Returns [instanceId] in the layout for [columns]. A derived layout is stored first, and the copy is returned.
     */
    suspend fun instanceForEdit(columns: Int, instanceId: String): WidgetInstance?

    suspend fun snapshot(): DashboardSnapshot

    suspend fun restore(snapshot: DashboardSnapshot)

    suspend fun restoreClassicDesign()

    suspend fun setNotices(state: DashboardNotices.State)
}
