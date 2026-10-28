package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppConfig
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppFilterMode
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppFilterSyntax
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppPrivacyOverride
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppSendState
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppToggleOverride

/**
 * Builds a list of labels for the values of [config] that differ from the defaults. Also includes "Not installed" when
 * [installed] is false.
 */
fun notificationAppChips(context: Context, config: NotificationAppConfig?, installed: Boolean): List<String> {
    val chips = mutableListOf<String>()
    if (!installed) {
        chips.add(context.getString(R.string.notification_apps_not_installed))
    }
    if (config == null) {
        return chips
    }

    when (config.sendState) {
        NotificationAppSendState.DEFAULT -> Unit
        NotificationAppSendState.ALWAYS -> chips.add(context.getString(R.string.notification_apps_chip_always_send))
        NotificationAppSendState.NEVER -> chips.add(context.getString(R.string.notification_apps_chip_never_send))
    }
    when (config.pebbleState) {
        NotificationAppSendState.DEFAULT -> Unit
        NotificationAppSendState.ALWAYS -> chips.add(context.getString(R.string.notification_apps_chip_pebble_always))
        NotificationAppSendState.NEVER -> chips.add(context.getString(R.string.notification_apps_chip_pebble_never))
    }
    if (config.filter.mode != NotificationAppFilterMode.OFF) {
        val count = config.filter.entries.size
        val plural = when (config.filter.syntax) {
            NotificationAppFilterSyntax.WORDS -> R.plurals.notification_apps_chip_filter_words
            NotificationAppFilterSyntax.REGEX -> R.plurals.notification_apps_chip_filter_regular_expression_patterns
        }
        chips.add(context.resources.getQuantityString(plural, count, count))
    }
    if (config.replacements.isNotEmpty()) {
        val count = config.replacements.size
        chips.add(context.resources.getQuantityString(R.plurals.notification_apps_replacement_count, count, count))
    }

    val overrides = config.overrides
    when (overrides.privacy) {
        NotificationAppPrivacyOverride.DEFAULT -> Unit
        NotificationAppPrivacyOverride.OFF -> chips.add(context.getString(R.string.notification_apps_chip_privacy_off))
        NotificationAppPrivacyOverride.COMPLETE -> chips.add(context.getString(R.string.notification_apps_chip_privacy_complete))
        NotificationAppPrivacyOverride.BODY_ONLY -> chips.add(context.getString(R.string.notification_apps_chip_privacy_body_only))
    }
    listOf(
        Triple(
            overrides.whenScreenOn,
            R.string.notification_apps_chip_screen_on_send,
            R.string.notification_apps_chip_screen_on_do_not_send
        ),
        Triple(
            overrides.preferLongText,
            R.string.notification_apps_chip_long_text_on,
            R.string.notification_apps_chip_long_text_off
        ),
        Triple(
            overrides.blockIfOutOfNotificationTimes,
            R.string.notification_apps_chip_times_on,
            R.string.notification_apps_chip_times_off
        ),
        Triple(
            overrides.ignoreLowPriority,
            R.string.notification_apps_chip_low_priority_do_not_send,
            R.string.notification_apps_chip_low_priority_send
        ),
    ).forEach { (override, on, off) ->
        when (override) {
            NotificationAppToggleOverride.DEFAULT -> Unit
            NotificationAppToggleOverride.ON -> chips.add(context.getString(on))
            NotificationAppToggleOverride.OFF -> chips.add(context.getString(off))
        }
    }
    if (config.forwardLocalOnly) {
        chips.add(context.getString(R.string.notification_apps_chip_forward_local_only))
    }
    return chips
}
