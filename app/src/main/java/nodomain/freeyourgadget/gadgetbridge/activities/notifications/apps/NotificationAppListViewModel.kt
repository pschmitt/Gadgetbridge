package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppConfig
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRepository
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationDefaultAction
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils
import org.slf4j.LoggerFactory
import java.text.Collator

/**
 * An app from the package manager.
 */
data class InstalledApp(
    val packageName: String,
    val label: String,
    val system: Boolean,
)

sealed interface NotificationAppListItem {
    val id: String

    data class Defaults(
        val defaultAction: NotificationDefaultAction,
        val mediaIgnoresList: Boolean,
    ) : NotificationAppListItem {
        override val id: String
            get() = "defaults"
    }

    data class AllApps(val config: NotificationAppConfig?) : NotificationAppListItem {
        override val id: String
            get() = "all_apps"
    }

    data class Section(@param:StringRes val title: Int) : NotificationAppListItem {
        override val id: String
            get() = "section_$title"
    }

    /**
     * An installed app, or a configured app that is not installed.
     */
    data class App(
        val packageName: String,
        val label: String,
        val installed: Boolean,
        val config: NotificationAppConfig?,
    ) : NotificationAppListItem {
        override val id: String
            get() = "app_$packageName"
    }
}

class NotificationAppListViewModel(application: Application) : AndroidViewModel(application) {
    private val installedApps = MutableStateFlow<List<InstalledApp>?>(null)
    private val defaults = MutableStateFlow<NotificationAppListItem.Defaults?>(null)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _showSystemApps = MutableStateFlow(false)
    val showSystemApps: StateFlow<Boolean> = _showSystemApps.asStateFlow()

    /**
     * The rows of the list, or null while the apps or the configs load.
     */
    val items: StateFlow<List<NotificationAppListItem>?> = combine(
        installedApps,
        NotificationAppEditor.configs,
        _query,
        _showSystemApps,
        defaults,
    ) { apps, configs, text, showSystem, defaultsItem ->
        if (apps == null || defaultsItem == null) {
            null
        } else {
            buildItems(apps, configs, text.trim(), showSystem, defaultsItem)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            installedApps.value = withContext(Dispatchers.IO) { loadInstalledApps(getApplication()) }
        }
        viewModelScope.launch {
            defaults.value = withContext(Dispatchers.IO) {
                NotificationAppListItem.Defaults(
                    NotificationAppRepository.getDefaultAction(),
                    GBApplication.getPrefs().getBoolean(PREF_MEDIA_IGNORES_LIST, false),
                )
            }
        }
    }

    fun setQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun setShowSystemApps(show: Boolean) {
        _showSystemApps.value = show
    }

    fun setDefaultAction(action: NotificationDefaultAction) {
        NotificationAppRepository.setDefaultAction(action)
        defaults.update { it?.copy(defaultAction = action) }
    }

    fun setMediaIgnoresList(enabled: Boolean) {
        LOG.debug("Storing {} = {}", PREF_MEDIA_IGNORES_LIST, enabled)
        GBApplication.getPrefs().preferences.edit { putBoolean(PREF_MEDIA_IGNORES_LIST, enabled) }
        defaults.update { it?.copy(mediaIgnoresList = enabled) }
    }

    fun resetAll() {
        NotificationAppEditor.launch { NotificationAppRepository.resetAll() }
    }

    private fun buildItems(
        apps: List<InstalledApp>,
        configs: Map<String, NotificationAppConfig>,
        query: String,
        showSystemApps: Boolean,
        defaults: NotificationAppListItem.Defaults,
    ): List<NotificationAppListItem> {
        val collator = Collator.getInstance()
        val byLabel = compareBy<NotificationAppListItem.App, String>(collator) { it.label }
        val installedByPackage = apps.associateBy { it.packageName }

        val configured = configs.values
            .filter { !it.isAllApps }
            .map { config ->
                val app = installedByPackage[config.packageName]
                NotificationAppListItem.App(config.packageName, app?.label ?: config.packageName, app != null, config)
            }
            .filter { it.matches(query) }
            .sortedWith(byLabel)
        val others = apps
            .filter { it.packageName !in configs && (showSystemApps || !it.system) }
            .map { NotificationAppListItem.App(it.packageName, it.label, true, null) }
            .filter { it.matches(query) }
            .sortedWith(byLabel)

        val rows = mutableListOf<NotificationAppListItem>()
        if (query.isEmpty()) {
            rows.add(defaults)
            rows.add(NotificationAppListItem.AllApps(configs[NotificationAppConfig.ALL_APPS]))
        }
        if (configured.isNotEmpty()) {
            rows.add(NotificationAppListItem.Section(R.string.notification_apps_configured))
            rows.addAll(configured)
        }
        if (others.isNotEmpty()) {
            rows.add(NotificationAppListItem.Section(R.string.notification_apps_other_apps))
            rows.addAll(others)
        }
        return rows
    }

    private fun NotificationAppListItem.App.matches(query: String): Boolean =
        query.isEmpty() || label.contains(query, ignoreCase = true) || packageName.contains(query, ignoreCase = true)

    private suspend fun loadInstalledApps(context: Context): List<InstalledApp> = coroutineScope {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = pm.queryIntentActivities(launcherIntent, 0).map { it.activityInfo.packageName }.toSet()
        val packages = pm.getInstalledPackages(0)
        val ownPackages = packages.map { it.packageName }.toSet()
        val profileActivities = NotificationUtils.getOtherProfileActivities(context).filter { it.applicationInfo.packageName !in ownPackages }

        val apps = packages.chunked(LABEL_BATCH_SIZE).map { batch ->
            async(Dispatchers.IO) {
                batch.map { info ->
                    val appInfo = info.applicationInfo
                    val label = appInfo?.let { pm.getApplicationLabel(it).toString() } ?: info.packageName
                    val system =
                        appInfo != null && (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 && info.packageName !in launchable
                    InstalledApp(info.packageName, label, system)
                }
            }
        }.awaitAll().flatten() + profileActivities.distinctBy { it.applicationInfo.packageName }.map {
            InstalledApp(it.applicationInfo.packageName, it.label.toString(), false)
        }
        LOG.debug("Loaded {} applications", apps.size)
        apps
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(NotificationAppListViewModel::class.java)

        private const val PREF_MEDIA_IGNORES_LIST = "notification_media_ignores_application_list"
        private const val LABEL_BATCH_SIZE = 50
    }
}
