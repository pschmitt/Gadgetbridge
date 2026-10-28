package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppConfig
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRepository
import nodomain.freeyourgadget.gadgetbridge.util.GB

/**
 * Access to [NotificationAppRepository] for the notification app screens. The writes run on a
 * background thread, one at a time, and in the order of the calls. They continue when the screen
 * closes.
 */
object NotificationAppEditor {
    private val scope = MainScope()
    private val dispatcher = Dispatchers.IO.limitedParallelism(1)

    /**
     * The stored configs. The first collection loads the repository on a background thread.
     */
    val configs: Flow<Map<String, NotificationAppConfig>> = flow { emitAll(NotificationAppRepository.configs) }
        .flowOn(Dispatchers.IO)

    /**
     * Runs [write] after all the writes before it. When [write] returns false, displays a toast
     * and runs [onFailure] on the main thread.
     */
    fun launch(onFailure: () -> Unit = {}, write: () -> Boolean) {
        scope.launch {
            if (!withContext(dispatcher) { write() }) {
                val context = GBApplication.getContext()
                GB.toast(context, context.getString(R.string.notification_apps_save_failed), Toast.LENGTH_LONG, GB.ERROR)
                onFailure()
            }
        }
    }

    /**
     * Applies [transform] to the stored config of [packageName], and saves the result.
     */
    fun update(packageName: String, onFailure: () -> Unit = {}, transform: (NotificationAppConfig) -> NotificationAppConfig) {
        launch(onFailure) { NotificationAppRepository.update(packageName, transform) }
    }
}
