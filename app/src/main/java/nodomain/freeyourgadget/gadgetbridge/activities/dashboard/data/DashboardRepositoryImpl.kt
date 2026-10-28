package nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardDefaults
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardMigration
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardNotices
import nodomain.freeyourgadget.gadgetbridge.widgets.DashboardSnapshot
import nodomain.freeyourgadget.gadgetbridge.widgets.DeletedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.PlacedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetInstance
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetLayoutStore
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry

/**
 * The [DashboardRepository] backed by [WidgetLayoutStore] and the application preferences.
 *
 * The store is synchronous, so every write and every observed read runs on [ioDispatcher].
 */
class DashboardRepositoryImpl(
    private val preferences: DashboardPreferences = DashboardPreferences(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DashboardRepository {
    /**
     * Runs the layout writes one at a time, in call order.
     */
    private val writeDispatcher = ioDispatcher.limitedParallelism(1)

    override fun layoutFlow(columns: Int): Flow<List<PlacedWidget>> =
        merge(WidgetLayoutStore.changes, coordinatorChanges())
            .onStart { emit(Unit) }
            .map { WidgetLayoutStore.placedWidgets(columns) }
            .flowOn(ioDispatcher)
            .distinctUntilChanged()

    override fun settingsFlow(): Flow<DashboardSettings> = preferences.settingsFlow()

    override fun noticesFlow(): Flow<DashboardNotices.State> =
        DashboardNotices.changes
            .onStart { emit(Unit) }
            .map { DashboardNotices.state() }
            .flowOn(ioDispatcher)
            .distinctUntilChanged()

    override fun currentSettings(): DashboardSettings = preferences.current()

    override fun currentNotices(): DashboardNotices.State = DashboardNotices.state()

    override fun isDeviceInScope(device: GBDevice): Boolean = preferences.isDeviceInScope(device)

    override fun consumeStartEditingRequest(): Boolean = preferences.consumeStartEditingRequest()

    override fun deviceByAddress(address: String): GBDevice? =
        GBApplication.app().deviceManager.getDeviceByAddress(address)

    override fun availableWidgets(): List<GBWidget<*>> = WidgetRegistry.available()

    override suspend fun newWidgets(device: GBDevice): List<GBWidget<*>> =
        withContext(ioDispatcher) { DashboardDefaults.newWidgets(device) }

    override suspend fun add(columns: Int, typeId: String, variantId: String): WidgetInstance =
        withContext(writeDispatcher) { WidgetLayoutStore.add(columns, typeId, variantId) }

    override suspend fun arrange(columns: Int, arrangement: List<Pair<String, Int>>) =
        withContext(writeDispatcher) { WidgetLayoutStore.arrange(columns, arrangement) }

    override suspend fun duplicate(columns: Int, instanceId: String): WidgetInstance? =
        withContext(writeDispatcher) { WidgetLayoutStore.duplicate(columns, instanceId) }

    override suspend fun delete(columns: Int, instanceId: String): DeletedWidget? =
        withContext(writeDispatcher) { WidgetLayoutStore.delete(columns, instanceId) }

    override suspend fun restore(deleted: DeletedWidget) =
        withContext(writeDispatcher) { WidgetLayoutStore.restore(deleted) }

    override suspend fun setVariant(columns: Int, instanceId: String, variantId: String) =
        withContext(writeDispatcher) { WidgetLayoutStore.setVariant(columns, instanceId, variantId) }

    override suspend fun instanceForEdit(columns: Int, instanceId: String): WidgetInstance? =
        withContext(writeDispatcher) { WidgetLayoutStore.instanceForEdit(columns, instanceId) }

    override suspend fun snapshot(): DashboardSnapshot =
        withContext(writeDispatcher) { WidgetLayoutStore.snapshot() }

    override suspend fun restore(snapshot: DashboardSnapshot) =
        withContext(writeDispatcher) { WidgetLayoutStore.restore(snapshot) }

    override suspend fun restoreClassicDesign() =
        withContext(writeDispatcher) { DashboardMigration.restoreClassic() }

    /**
     * Emits when the coordinators of the devices change. The available widgets depend only on them.
     */
    private fun coordinatorChanges(): Flow<Unit> = devicesChanges()
        .map { GBApplication.app().deviceManager.devices.map { it.deviceCoordinator }.toSet() }
        .distinctUntilChanged()
        .map { }

    /**
     * Emits when the list of devices changes.
     */
    private fun devicesChanges(): Flow<Unit> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                trySend(Unit)
            }
        }
        val broadcastManager = LocalBroadcastManager.getInstance(GBApplication.getContext())
        broadcastManager.registerReceiver(receiver, IntentFilter(DeviceManager.ACTION_DEVICES_CHANGED))
        awaitClose { broadcastManager.unregisterReceiver(receiver) }
    }

    override suspend fun setNotices(state: DashboardNotices.State) =
        withContext(writeDispatcher) { DashboardNotices.set(state) }
}
