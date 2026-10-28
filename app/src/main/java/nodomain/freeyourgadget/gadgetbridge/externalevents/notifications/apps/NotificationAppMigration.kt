package nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps

import android.content.SharedPreferences
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.NotificationFilterEntryDao
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import nodomain.freeyourgadget.gadgetbridge.util.NotificationUtils
import org.slf4j.LoggerFactory
import java.util.Locale

/**
 * The migration from the old app lists, list mode and word filters to [NotificationAppConfig]s.
 * It rewrites all configs and the default action from the old values.
 * The old prefs and tables are not touched, so that users can go back to an older release.
 */
object NotificationAppMigration {
    private val LOG = LoggerFactory.getLogger(NotificationAppMigration::class.java)

    private const val PREF_LIST_IS_BLACKLIST = "notification_list_is_blacklist"

    // The values of the old NotificationFilter columns
    private const val OLD_FILTER_MODE_WHITELIST = 1
    private const val OLD_FILTER_MODE_BLACKLIST = 2
    private const val OLD_FILTER_SUB_MODE_ALL = 1

    fun migrate(sharedPrefs: SharedPreferences, editor: SharedPreferences.Editor) {
        val prefs = GBPrefs(sharedPrefs)
        val defaultAction = oldDefaultAction(prefs)
        editor.putString(NotificationAppRepository.PREF_DEFAULT_ACTION, defaultAction.name)
        try {
            GBApplication.acquireDB().use { db ->
                val session = db.daoSession
                val configs = buildConfigs(session, prefs, defaultAction == NotificationDefaultAction.SEND)
                db.database.beginTransaction()
                try {
                    session.notificationAppReplacementEntryDao.deleteAll()
                    session.notificationAppFilterEntryDao.deleteAll()
                    session.notificationAppConfigEntryDao.deleteAll()
                    configs.forEach {
                        NotificationAppRepository.write(session, it)
                    }
                    db.database.setTransactionSuccessful()
                } finally {
                    db.database.endTransaction()
                }
                LOG.info("Migrated {} notification app configs, default action {}", configs.size, defaultAction)
            }
        } catch (e: Exception) {
            LOG.error("Failed to migrate the notification app configs", e)
        }
    }

    /**
     * The default action from the old list mode. A whitelist does not send the apps that are not
     * in the list.
     */
    private fun oldDefaultAction(prefs: GBPrefs): NotificationDefaultAction =
        if (prefs.getString(PREF_LIST_IS_BLACKLIST, "true") == "false") {
            NotificationDefaultAction.DO_NOT_SEND
        } else {
            NotificationDefaultAction.SEND
        }

    private fun buildConfigs(session: DaoSession, prefs: GBPrefs, isBlacklist: Boolean): List<NotificationAppConfig> {
        val listedState = if (isBlacklist) NotificationAppSendState.NEVER else NotificationAppSendState.ALWAYS
        val notifPackages = prefs.getStringSet(GBPrefs.PACKAGE_BLACKLIST, emptySet())
        val pebblePackages = prefs.getStringSet(GBPrefs.PACKAGE_PEBBLEMSG_BLACKLIST, emptySet())
            .map { NotificationAppRepository.pebbleSenderToPackageName(it) }

        val configs = LinkedHashMap<String, NotificationAppConfig>()
        fun update(packageName: String, change: (NotificationAppConfig) -> NotificationAppConfig) {
            configs[packageName] = change(configs[packageName] ?: NotificationAppConfig(packageName))
        }

        notifPackages.forEach { update(it) { config -> config.copy(sendState = listedState) } }
        pebblePackages.forEach { update(it) { config -> config.copy(pebbleState = listedState) } }

        val filterRows = session.notificationFilterDao.loadAll()
        if (filterRows.isEmpty()) {
            return configs.values.toList()
        }

        // The key for old filters is the lower case package name
        val packageNames = HashMap<String, String>()
        (configs.keys + installedPackageNames()).forEach {
            packageNames.putIfAbsent(it.lowercase(Locale.ROOT), it)
        }

        for (filterRow in filterRows) {
            val mode = when (filterRow.notificationFilterMode) {
                OLD_FILTER_MODE_BLACKLIST -> NotificationAppFilterMode.BLOCK_IF_CONTAINS
                OLD_FILTER_MODE_WHITELIST -> NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS
                else -> continue
            }
            val match = if (filterRow.notificationFilterSubMode == OLD_FILTER_SUB_MODE_ALL) {
                NotificationAppFilterMatch.ALL
            } else {
                NotificationAppFilterMatch.ANY
            }
            val words = session.notificationFilterEntryDao.queryBuilder()
                .where(NotificationFilterEntryDao.Properties.NotificationFilterId.eq(filterRow.id))
                .orderAsc(NotificationFilterEntryDao.Properties.Id)
                .list()
                .map { it.notificationFilterContent }
            val packageName = packageNames[filterRow.appIdentifier] ?: filterRow.appIdentifier
            update(packageName) { config ->
                config.copy(
                    filter = NotificationAppFilter(
                        mode = mode,
                        match = match,
                        syntax = NotificationAppFilterSyntax.WORDS,
                        ignoreCase = false,
                        entries = words,
                    )
                )
            }
        }

        return configs.values.toList()
    }

    private fun installedPackageNames(): List<String> = try {
        NotificationUtils.getAllApplications(GBApplication.getContext())
    } catch (e: Exception) {
        LOG.warn("Failed to list the installed apps", e)
        emptyList()
    }
}
