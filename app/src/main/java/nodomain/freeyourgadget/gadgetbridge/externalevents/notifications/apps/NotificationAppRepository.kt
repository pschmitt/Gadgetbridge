package nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps

import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession
import nodomain.freeyourgadget.gadgetbridge.entities.NotificationAppConfigEntry
import nodomain.freeyourgadget.gadgetbridge.entities.NotificationAppConfigEntryDao
import nodomain.freeyourgadget.gadgetbridge.entities.NotificationAppFilterEntry
import nodomain.freeyourgadget.gadgetbridge.entities.NotificationAppFilterEntryDao
import nodomain.freeyourgadget.gadgetbridge.entities.NotificationAppReplacementEntry
import nodomain.freeyourgadget.gadgetbridge.entities.NotificationAppReplacementEntryDao
import nodomain.freeyourgadget.gadgetbridge.model.workouts.enumByName
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

/**
 * The stored [NotificationAppConfig]s, with an in-memory cache of the compiled configs.
 */
object NotificationAppRepository {
    private val LOG = LoggerFactory.getLogger(NotificationAppRepository::class.java)

    private const val LOAD_RETRY_DELAY_MS = 60_000L

    /**
     * The pref key for [NotificationDefaultAction], stored as the enum name.
     */
    const val PREF_DEFAULT_ACTION = "notification_app_default_action"

    private val configsFlow = MutableStateFlow<Map<String, NotificationAppConfig>>(emptyMap())

    @Volatile
    private var compiled: Map<String, CompiledNotificationAppConfig> = emptyMap()

    @Volatile
    private var loaded = false

    @Volatile
    private var lastLoadFailure = 0L

    /**
     * The stored configs by package name. It includes the "All apps" config when it is stored.
     */
    val configs: StateFlow<Map<String, NotificationAppConfig>>
        get() {
            ensureLoaded()
            return configsFlow.asStateFlow()
        }

    /**
     * The compiled config for [packageName], or null when the app is not configured.
     * Use [NotificationAppConfig.ALL_APPS] to get the "All apps" config.
     */
    fun get(packageName: String?): CompiledNotificationAppConfig? {
        if (packageName == null) {
            return null
        }
        ensureLoaded()
        return compiled[packageName]
    }

    fun getDefaultAction(): NotificationDefaultAction {
        val prefs = GBApplication.getPrefs()
        return enumByName<NotificationDefaultAction>(prefs.getString(PREF_DEFAULT_ACTION, null))
            ?: NotificationDefaultAction.SEND
    }

    fun setDefaultAction(action: NotificationDefaultAction) {
        LOG.debug("Storing notification default action {}", action)
        GBApplication.getPrefs().preferences.edit { putString(PREF_DEFAULT_ACTION, action.name) }
    }

    /**
     * Stores [config] with its filter entries and replacements in their order. If the config does
     * not have any value different from the defaults, it is deleted. Returns false when the database
     * write failed.
     */
    @Synchronized
    fun save(config: NotificationAppConfig): Boolean {
        if (!config.isConfigured) {
            return delete(config.packageName)
        }
        if (!ensureLoaded()) {
            return false
        }
        try {
            GBApplication.acquireDB().use { db ->
                db.database.beginTransaction()
                try {
                    write(db.daoSession, config)
                    db.database.setTransactionSuccessful()
                } finally {
                    db.database.endTransaction()
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to save the notification config for {}", config.packageName, e)
            return false
        }
        LOG.debug("Stored notification config {}", config)
        publish(configsFlow.value + (config.packageName to config))
        return true
    }

    /**
     * Stores the result of [transform] on the stored config for [packageName], or on a new config
     * when the app is not yet configured. Returns false when the database write failed.
     */
    @Synchronized
    fun update(packageName: String, transform: (NotificationAppConfig) -> NotificationAppConfig): Boolean {
        if (!ensureLoaded()) {
            return false
        }
        return save(transform(configsFlow.value[packageName] ?: NotificationAppConfig(packageName)))
    }

    /**
     * Deletes the config for [packageName]. Returns false when the database write failed.
     */
    @Synchronized
    fun delete(packageName: String): Boolean {
        if (!ensureLoaded()) {
            return false
        }
        try {
            GBApplication.acquireDB().use { db ->
                db.database.beginTransaction()
                try {
                    val session = db.daoSession
                    findRow(session, packageName)?.let {
                        deleteRow(session, it)
                    }
                    db.database.setTransactionSuccessful()
                } finally {
                    db.database.endTransaction()
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to delete the notification config for {}", packageName, e)
            return false
        }
        LOG.debug("Deleted notification config for {}", packageName)
        publish(configsFlow.value - packageName)
        return true
    }

    /**
     * Deletes the configs of all apps. The "All apps" config and the default action stay.
     * Returns false when the database write failed.
     */
    @Synchronized
    fun resetAll(): Boolean {
        if (!ensureLoaded()) {
            return false
        }
        try {
            GBApplication.acquireDB().use { db ->
                db.database.beginTransaction()
                try {
                    val session = db.daoSession
                    session.notificationAppConfigEntryDao.queryBuilder()
                        .where(NotificationAppConfigEntryDao.Properties.PackageName.notEq(NotificationAppConfig.ALL_APPS))
                        .list()
                        .forEach { deleteRow(session, it) }
                    db.database.setTransactionSuccessful()
                } finally {
                    db.database.endTransaction()
                }
            }
        } catch (e: Exception) {
            LOG.error("Failed to reset the notification configs", e)
            return false
        }
        LOG.debug("Deleted all notification app configs")
        publish(configsFlow.value.filterKeys { it == NotificationAppConfig.ALL_APPS })
        return true
    }

    /**
     * Loads the configs on a background thread, if they are not yet loaded.
     */
    fun loadInBackground() {
        thread(name = "gb-notification-apps-load") {
            ensureLoaded()
        }
    }

    /**
     * Clears the cache and reloads the configs again on a background thread. The load emits the
     * configs on [configs].
     */
    @Synchronized
    fun reload() {
        LOG.debug("Clearing notification app config cache")
        loaded = false
        lastLoadFailure = 0L
        loadInBackground()
    }

    /**
     * The package name for a Pebble message sender.
     */
    fun pebbleSenderToPackageName(sender: String): String = when (sender) {
        "Conversations" -> "eu.siacs.conversations"
        "OsmAnd" -> "net.osmand.plus"
        else -> sender
    }

    /**
     * Loads the configs if they are not yet loaded. After a failed load, it does not try again
     * for [LOAD_RETRY_DELAY_MS]. Returns false when the configs are not loaded.
     */
    fun ensureLoaded(): Boolean {
        if (loaded) {
            return true
        }
        synchronized(this) {
            if (loaded) {
                return true
            }
            if (System.currentTimeMillis() - lastLoadFailure < LOAD_RETRY_DELAY_MS) {
                return false
            }
            LOG.debug("Loading notification app configs")
            try {
                publish(readAll())
                loaded = true
                lastLoadFailure = 0L
            } catch (e: Exception) {
                LOG.error("Failed to load the notification app configs", e)
                lastLoadFailure = System.currentTimeMillis()
            }
            return loaded
        }
    }

    private fun publish(configs: Map<String, NotificationAppConfig>) {
        compiled = configs.mapValues { CompiledNotificationAppConfig(it.value) }
        configsFlow.value = configs
    }

    private fun readAll(): Map<String, NotificationAppConfig> = GBApplication.acquireDbReadOnly().use { db ->
        val session = db.daoSession
        val entries = session.notificationAppFilterEntryDao.queryBuilder()
            .orderAsc(NotificationAppFilterEntryDao.Properties.Position)
            .list()
            .groupBy { it.configId }
        val replacements = session.notificationAppReplacementEntryDao.queryBuilder()
            .orderAsc(NotificationAppReplacementEntryDao.Properties.Position)
            .list()
            .groupBy { it.configId }
        val configs = session.notificationAppConfigEntryDao.loadAll().associate { row ->
            row.packageName to toConfig(row, entries[row.id].orEmpty(), replacements[row.id].orEmpty())
        }
        LOG.debug("Loaded {} notification app configs", configs.size)
        configs
    }

    /**
     * Writes [config] and replaces its filter entries and replacements. Should be called in a transaction.
     */
    internal fun write(session: DaoSession, config: NotificationAppConfig) {
        val row = findRow(session, config.packageName) ?: NotificationAppConfigEntry()
        row.packageName = config.packageName
        row.sendState = config.sendState.name
        row.pebbleState = config.pebbleState.name
        row.filterMode = config.filter.mode.name
        row.filterMatch = config.filter.match.name
        row.filterSyntax = config.filter.syntax.name
        row.filterIgnoreCase = config.filter.ignoreCase
        row.privacyMode = config.overrides.privacy.name
        row.whenScreenOn = config.overrides.whenScreenOn.name
        row.preferLongText = config.overrides.preferLongText.name
        row.blockIfOutOfNotificationTimes = config.overrides.blockIfOutOfNotificationTimes.name
        row.ignoreLowPriority = config.overrides.ignoreLowPriority.name
        row.forwardLocalOnly = config.forwardLocalOnly
        session.notificationAppConfigEntryDao.insertOrReplace(row)
        val configId = requireNotNull(row.id) {
            "NotificationAppConfigEntry row ID should not be null after inserting it"
        }

        deleteChildren(session, configId)
        config.filter.entries.forEachIndexed { position, content ->
            val entryRow = NotificationAppFilterEntry()
            entryRow.configId = configId
            entryRow.position = position
            entryRow.content = content
            session.notificationAppFilterEntryDao.insert(entryRow)
        }
        config.replacements.forEachIndexed { position, replacement ->
            val replacementRow = NotificationAppReplacementEntry()
            replacementRow.configId = configId
            replacementRow.position = position
            replacementRow.pattern = replacement.pattern
            replacementRow.replacement = replacement.replacement
            replacementRow.target = replacement.target.name
            replacementRow.ignoreCase = replacement.ignoreCase
            replacementRow.enabled = replacement.enabled
            session.notificationAppReplacementEntryDao.insert(replacementRow)
        }
    }

    private fun findRow(session: DaoSession, packageName: String): NotificationAppConfigEntry? =
        session.notificationAppConfigEntryDao.queryBuilder()
            .where(NotificationAppConfigEntryDao.Properties.PackageName.eq(packageName))
            .unique()

    private fun deleteRow(session: DaoSession, row: NotificationAppConfigEntry) {
        deleteChildren(session, requireNotNull(row.id))
        session.notificationAppConfigEntryDao.delete(row)
    }

    private fun deleteChildren(session: DaoSession, configId: Long) {
        session.notificationAppFilterEntryDao.queryBuilder()
            .where(NotificationAppFilterEntryDao.Properties.ConfigId.eq(configId))
            .buildDelete()
            .executeDeleteWithoutDetachingEntities()
        session.notificationAppReplacementEntryDao.queryBuilder()
            .where(NotificationAppReplacementEntryDao.Properties.ConfigId.eq(configId))
            .buildDelete()
            .executeDeleteWithoutDetachingEntities()
    }

    private fun toConfig(
        row: NotificationAppConfigEntry,
        entries: List<NotificationAppFilterEntry>,
        replacements: List<NotificationAppReplacementEntry>,
    ) = NotificationAppConfig(
        packageName = row.packageName,
        sendState = enumByName(row.sendState) ?: NotificationAppSendState.DEFAULT,
        pebbleState = enumByName(row.pebbleState) ?: NotificationAppSendState.DEFAULT,
        filter = NotificationAppFilter(
            mode = enumByName(row.filterMode) ?: NotificationAppFilterMode.OFF,
            match = enumByName(row.filterMatch) ?: NotificationAppFilterMatch.ANY,
            syntax = enumByName(row.filterSyntax) ?: NotificationAppFilterSyntax.WORDS,
            ignoreCase = row.filterIgnoreCase,
            entries = entries.map { it.content },
        ),
        replacements = replacements.map {
            NotificationAppReplacement(
                pattern = it.pattern,
                replacement = it.replacement,
                target = enumByName(it.target) ?: NotificationAppReplacementTarget.BOTH,
                ignoreCase = it.ignoreCase,
                enabled = it.enabled,
            )
        },
        overrides = NotificationAppOverrides(
            privacy = enumByName(row.privacyMode) ?: NotificationAppPrivacyOverride.DEFAULT,
            whenScreenOn = enumByName(row.whenScreenOn) ?: NotificationAppToggleOverride.DEFAULT,
            preferLongText = enumByName(row.preferLongText) ?: NotificationAppToggleOverride.DEFAULT,
            blockIfOutOfNotificationTimes = enumByName(row.blockIfOutOfNotificationTimes) ?: NotificationAppToggleOverride.DEFAULT,
            ignoreLowPriority = enumByName(row.ignoreLowPriority) ?: NotificationAppToggleOverride.DEFAULT,
        ),
        forwardLocalOnly = row.forwardLocalOnly,
    )
}
