package nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps

import org.slf4j.LoggerFactory
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

data class NotificationText(
    val title: String?,
    val body: String?,
)

/**
 * A [NotificationAppReplacement] with its compiled pattern.
 */
class CompiledNotificationAppReplacement(val replacement: NotificationAppReplacement) {
    private val pattern: Pattern? = NotificationAppRules.compile(replacement.pattern, replacement.ignoreCase, 0)

    /**
     * Applies the rule to [text], also when the rule is disabled. Returns [text] unchanged when
     * the pattern or the replacement is not valid.
     */
    fun apply(text: String): String {
        val compiled = pattern ?: return text
        return try {
            compiled.matcher(text).replaceAll(replacement.replacement)
        } catch (e: Exception) {
            LOG.warn("Invalid notification replacement '{}'", replacement.replacement, e)
            text
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(CompiledNotificationAppReplacement::class.java)
    }
}

/**
 * A [NotificationAppConfig] with its compiled filter and replacement patterns.
 */
class CompiledNotificationAppConfig(val config: NotificationAppConfig) {
    private val filterPatterns: List<Pattern> = config.filter.let { filter ->
        if (filter.mode == NotificationAppFilterMode.OFF) {
            emptyList()
        } else {
            val flags = if (filter.syntax == NotificationAppFilterSyntax.WORDS) Pattern.LITERAL else 0
            filter.entries.mapNotNull {
                NotificationAppRules.compile(it, filter.ignoreCase, flags)
            }
        }
    }

    private val replacements: List<CompiledNotificationAppReplacement> = config.replacements
        .filter { it.enabled }
        .map { CompiledNotificationAppReplacement(it) }

    /**
     * True when [text] passes the filter. A filter without valid entries always passes.
     */
    fun passesFilter(text: String): Boolean {
        if (filterPatterns.isEmpty()) {
            return true
        }
        val matched = when (config.filter.match) {
            NotificationAppFilterMatch.ANY -> filterPatterns.any { it.matcher(text).find() }
            NotificationAppFilterMatch.ALL -> filterPatterns.all { it.matcher(text).find() }
        }
        return when (config.filter.mode) {
            NotificationAppFilterMode.OFF -> true
            NotificationAppFilterMode.BLOCK_IF_CONTAINS -> !matched
            NotificationAppFilterMode.ONLY_SEND_IF_CONTAINS -> matched
        }
    }

    fun applyReplacements(text: NotificationText): NotificationText {
        var title = text.title
        var body = text.body
        for (rule in replacements) {
            val target = rule.replacement.target
            if (title != null && target != NotificationAppReplacementTarget.BODY) {
                title = rule.apply(title)
            }
            if (body != null && target != NotificationAppReplacementTarget.TITLE) {
                body = rule.apply(body)
            }
        }
        return NotificationText(title, body)
    }
}

/**
 * The rules that decide if a notification is sent, and how it is modified.
 * - allApps is the "All apps" config.
 * - app is the config of the source app.
 * Either can be null when not configured.
 */
object NotificationAppRules {
    private val LOG = LoggerFactory.getLogger(NotificationAppRules::class.java)

    fun shouldSend(app: CompiledNotificationAppConfig?, defaultAction: NotificationDefaultAction): Boolean =
        resolveState(app?.config?.sendState, defaultAction)

    fun shouldSendPebble(app: CompiledNotificationAppConfig?, defaultAction: NotificationDefaultAction): Boolean =
        resolveState(app?.config?.pebbleState, defaultAction)

    /**
     * True when the original text passes the "All apps" filter and the app filter. The text is
     * the title and the body that are not empty, joined by a space.
     */
    fun passesFilters(
        allApps: CompiledNotificationAppConfig?,
        app: CompiledNotificationAppConfig?,
        title: String?,
        body: String?,
    ): Boolean {
        val text = listOf(title, body).filterNot { it.isNullOrEmpty() }.joinToString(" ")
        if (allApps != null && !allApps.passesFilter(text)) {
            LOG.debug("Notification blocked by the filter for all apps")
            return false
        }
        if (app != null && !app.passesFilter(text)) {
            LOG.debug("Notification blocked by the filter for {}", app.config.packageName)
            return false
        }
        return true
    }

    /**
     * Applies all replacements: first from the "All apps" config, then the ones for the app.
     */
    fun applyReplacements(
        allApps: CompiledNotificationAppConfig?,
        app: CompiledNotificationAppConfig?,
        title: String?,
        body: String?,
    ): NotificationText {
        var text = NotificationText(title, body)
        if (allApps != null) {
            text = allApps.applyReplacements(text)
        }
        if (app != null) {
            text = app.applyReplacements(text)
        }
        return text
    }

    /**
     * The [global] settings, with the overrides of [app] applied.
     */
    fun resolveSettings(app: CompiledNotificationAppConfig?, global: NotificationSettings): NotificationSettings {
        val overrides = app?.config?.overrides ?: return global
        return NotificationSettings(
            privacy = when (overrides.privacy) {
                NotificationAppPrivacyOverride.DEFAULT -> global.privacy
                NotificationAppPrivacyOverride.OFF -> MessagePrivacyMode.OFF
                NotificationAppPrivacyOverride.COMPLETE -> MessagePrivacyMode.COMPLETE
                NotificationAppPrivacyOverride.BODY_ONLY -> MessagePrivacyMode.BODY_ONLY
            },
            whenScreenOn = resolveToggle(overrides.whenScreenOn, global.whenScreenOn),
            preferLongText = resolveToggle(overrides.preferLongText, global.preferLongText),
            blockIfOutOfNotificationTimes = resolveToggle(overrides.blockIfOutOfNotificationTimes, global.blockIfOutOfNotificationTimes),
            ignoreLowPriority = resolveToggle(overrides.ignoreLowPriority, global.ignoreLowPriority),
        )
    }

    /**
     * The description of the syntax error in [pattern], or null when it is a valid regex.
     */
    fun regexError(pattern: String): String? = try {
        Pattern.compile(pattern)
        null
    } catch (e: PatternSyntaxException) {
        e.description
    }

    /**
     * The error in [replacement] for the groups of [pattern], or null when it is valid.
     * [pattern] must be a valid regex.
     */
    fun replacementError(pattern: String, replacement: String): String? = try {
        // replaceAll only parses the replacement on a match. The empty alternative always
        // matches, with the same groups as the pattern.
        Pattern.compile("(?:$pattern)|").matcher("").replaceAll(replacement)
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: e.toString()
    } catch (e: IndexOutOfBoundsException) {
        e.message ?: e.toString()
    }

    /**
     * Compiles [pattern] with [flags]. Returns null, and logs, when it is empty or not valid.
     */
    internal fun compile(pattern: String, ignoreCase: Boolean, flags: Int): Pattern? {
        if (pattern.isEmpty()) {
            return null
        }
        val caseFlags = if (ignoreCase) Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE else 0
        return try {
            Pattern.compile(pattern, flags or caseFlags)
        } catch (e: PatternSyntaxException) {
            LOG.warn("Ignoring invalid notification pattern '{}'", pattern, e)
            null
        }
    }

    private fun resolveState(state: NotificationAppSendState?, defaultAction: NotificationDefaultAction): Boolean =
        when (state) {
            NotificationAppSendState.ALWAYS -> true
            NotificationAppSendState.NEVER -> false
            NotificationAppSendState.DEFAULT, null -> defaultAction == NotificationDefaultAction.SEND
        }

    private fun resolveToggle(override: NotificationAppToggleOverride, global: Boolean): Boolean =
        when (override) {
            NotificationAppToggleOverride.DEFAULT -> global
            NotificationAppToggleOverride.ON -> true
            NotificationAppToggleOverride.OFF -> false
        }
}
