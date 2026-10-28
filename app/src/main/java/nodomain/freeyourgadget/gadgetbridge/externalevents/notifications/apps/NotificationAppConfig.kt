package nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps

import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs

/**
 * The global action for an app with the send state [NotificationAppSendState.DEFAULT].
 */
enum class NotificationDefaultAction(@param:StringRes val label: Int) {
    SEND(R.string.activity_send_waypoint_to_device),
    DO_NOT_SEND(R.string.notification_apps_do_not_send),
}

enum class NotificationAppSendState {
    DEFAULT,
    ALWAYS,
    NEVER,
}

enum class NotificationAppFilterMode(@param:StringRes val label: Int) {
    OFF(R.string.filter_mode_none),
    BLOCK_IF_CONTAINS(R.string.notification_apps_filter_mode_block),
    ONLY_SEND_IF_CONTAINS(R.string.notification_apps_filter_mode_only_send),
}

enum class NotificationAppFilterMatch(@param:StringRes val label: Int) {
    ANY(R.string.notification_apps_filter_match_any),
    ALL(R.string.notification_apps_filter_match_all),
}

/**
 * How the filter entries match the text.
 */
enum class NotificationAppFilterSyntax(
    @param:StringRes val label: Int,
    @param:StringRes val entriesTitle: Int,
    @param:StringRes val entriesEmpty: Int,
    @param:StringRes val entryHint: Int,
) {
    /**
     * A literal "string contains"
     */
    WORDS(
        R.string.notification_apps_filter_syntax_words,
        R.string.notification_apps_filter_entries,
        R.string.notification_apps_no_filter_words,
        R.string.notification_apps_filter_word,
    ),

    /**
     * A Java regular expression that must be found in the text.
     */
    REGEX(
        R.string.notification_apps_filter_syntax_regex,
        R.string.notification_apps_filter_regular_expression_patterns,
        R.string.notification_apps_no_filter_regular_expression_patterns,
        R.string.notification_apps_regular_expression_pattern,
    ),
}

enum class NotificationAppReplacementTarget(@param:StringRes val label: Int) {
    TITLE(R.string.title),
    BODY(R.string.notification_apps_notification_body),
    BOTH(R.string.moondrop_touch_earbud_both),
}

/**
 * The message privacy mode. [prefValue] is the value of the global pref.
 */
enum class MessagePrivacyMode(val prefValue: String, @param:StringRes val label: Int) {
    OFF("off", R.string.pref_message_privacy_mode_off),
    COMPLETE("complete", R.string.pref_message_privacy_mode_complete),
    BODY_ONLY("bodyonly", R.string.pref_message_privacy_mode_bodyonly);

    companion object {
        fun fromPrefValue(value: String?): MessagePrivacyMode =
            entries.firstOrNull { it.prefValue == value } ?: OFF
    }
}

/**
 * The app value of the message privacy mode. [DEFAULT] uses the global value.
 */
enum class NotificationAppPrivacyOverride {
    DEFAULT,
    OFF,
    COMPLETE,
    BODY_ONLY,
}

/**
 * The app override for a global switch preference. [DEFAULT] uses the global value.
 */
enum class NotificationAppToggleOverride {
    DEFAULT,
    ON,
    OFF,
}

/**
 * The word or regex filter of an app. The filter is applied to the original title and body.
 */
data class NotificationAppFilter(
    val mode: NotificationAppFilterMode = NotificationAppFilterMode.OFF,
    val match: NotificationAppFilterMatch = NotificationAppFilterMatch.ANY,
    val syntax: NotificationAppFilterSyntax = NotificationAppFilterSyntax.WORDS,
    val ignoreCase: Boolean = false,
    val entries: List<String> = emptyList(),
)

/**
 * A text replacement rule.
 */
data class NotificationAppReplacement(
    /**
     * A Java regular regex.
     */
    val pattern: String,
    /**
     * The replacement can refer to groups with `$n`.
     */
    val replacement: String = "",
    val target: NotificationAppReplacementTarget = NotificationAppReplacementTarget.BOTH,
    val ignoreCase: Boolean = false,
    val enabled: Boolean = true,
)

/**
 * The app overrides for global notification settings.
 */
data class NotificationAppOverrides(
    val privacy: NotificationAppPrivacyOverride = NotificationAppPrivacyOverride.DEFAULT,
    val whenScreenOn: NotificationAppToggleOverride = NotificationAppToggleOverride.DEFAULT,
    val preferLongText: NotificationAppToggleOverride = NotificationAppToggleOverride.DEFAULT,
    val blockIfOutOfNotificationTimes: NotificationAppToggleOverride = NotificationAppToggleOverride.DEFAULT,
    val ignoreLowPriority: NotificationAppToggleOverride = NotificationAppToggleOverride.DEFAULT,
)

/**
 * The notification config for one app, global for all devices. The config with the package name
 * [ALL_APPS] applies to all apps, and only its [filter] and [replacements] are used.
 */
data class NotificationAppConfig(
    val packageName: String,
    val sendState: NotificationAppSendState = NotificationAppSendState.DEFAULT,
    val pebbleState: NotificationAppSendState = NotificationAppSendState.DEFAULT,
    val filter: NotificationAppFilter = NotificationAppFilter(),
    val replacements: List<NotificationAppReplacement> = emptyList(),
    val overrides: NotificationAppOverrides = NotificationAppOverrides(),

    /**
     * True when the notifications that the app marks as local only are also sent.
     */
    val forwardLocalOnly: Boolean = false,
) {
    val isAllApps: Boolean
        get() = packageName == ALL_APPS

    /**
     * True when at least one value is different from the default.
     */
    val isConfigured: Boolean
        get() = this != NotificationAppConfig(packageName)

    companion object {
        /**
         * The reserved package name of the "All apps" config.
         */
        const val ALL_APPS = "*"
    }
}

/**
 * The values of the global notification settings that an app can override.
 */
data class NotificationSettings(
    val privacy: MessagePrivacyMode,
    val whenScreenOn: Boolean,
    val preferLongText: Boolean,
    val blockIfOutOfNotificationTimes: Boolean,
    val ignoreLowPriority: Boolean,
) {
    companion object {
        fun fromPrefs(prefs: GBPrefs): NotificationSettings = NotificationSettings(
            privacy = MessagePrivacyMode.fromPrefValue(
                prefs.getString(
                    "pref_message_privacy_mode",
                    MessagePrivacyMode.OFF.prefValue
                )
            ),
            whenScreenOn = prefs.getBoolean("notifications_generic_whenscreenon", false),
            preferLongText = prefs.getBoolean("notification_prefer_long_text", true),
            blockIfOutOfNotificationTimes = prefs.notificationTimesEnabled,
            ignoreLowPriority = prefs.getBoolean("notifications_ignore_low_priority", true),
        )
    }
}
