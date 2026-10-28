package nodomain.freeyourgadget.gadgetbridge.prefs.migrators

import android.Manifest
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.externalevents.NotificationListener
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRepository
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppSendState
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppToggleOverride
import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationSettings
import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import org.slf4j.LoggerFactory

class PreferenceMigrator71 : AbstractPreferenceMigrator() {
    override fun migrate(oldVersion: Int, sharedPrefs: SharedPreferences, editor: SharedPreferences.Editor) {
        if (oldVersion == 0) {
            // Fresh install, never used the SMS receiver
            return
        }

        val smsMode = sharedPrefs.getString("notification_mode_sms", "when_screen_off")
        if (smsMode == "never") {
            // With "never" notifications for SMS apps already followed the per-app config
            return
        }

        try {
            val context = GBApplication.getContext()
            val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
            if (defaultSmsPackage == null) {
                LOG.warn("Failed to determine default SMS package")
                return
            }

            val permissionsGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECEIVE_SMS
            ) == PackageManager.PERMISSION_GRANTED

            val saved = if (permissionsGranted) {
                val globalWhenScreenOn = NotificationSettings.fromPrefs(GBPrefs(sharedPrefs)).whenScreenOn
                val smsWhenScreenOn = smsMode == "always"

                NotificationAppRepository.update(defaultSmsPackage) { config ->
                    val current = config.overrides.whenScreenOn
                    val effective = when (current) {
                        NotificationAppToggleOverride.DEFAULT -> globalWhenScreenOn
                        NotificationAppToggleOverride.ON -> true
                        NotificationAppToggleOverride.OFF -> false
                    }
                    val whenScreenOn = when {
                        effective == smsWhenScreenOn -> current
                        smsWhenScreenOn == globalWhenScreenOn -> NotificationAppToggleOverride.DEFAULT
                        smsWhenScreenOn -> NotificationAppToggleOverride.ON
                        else -> NotificationAppToggleOverride.OFF
                    }
                    LOG.debug(
                        "Setting notification config for {} to always send, whenScreenOn={}",
                        defaultSmsPackage,
                        whenScreenOn
                    )

                    config.copy(
                        sendState = NotificationAppSendState.ALWAYS,
                        overrides = config.overrides.copy(whenScreenOn = whenScreenOn),
                    )
                }
            } else if (defaultSmsPackage in NotificationListener.SMS_APPS) {
                // Without the permission, the receiver never received any SMS, and the notifications from
                // the SMS app itself were ignored
                LOG.debug(
                    "Setting notification config for {} to never send",
                    defaultSmsPackage,
                )
                NotificationAppRepository.update(defaultSmsPackage) { config ->
                    config.copy(sendState = NotificationAppSendState.NEVER)
                }
            } else {
                return
            }

            if (!saved) {
                LOG.error("Failed to save the notification config")
            }
        } catch (e: Exception) {
            LOG.error("Failed to migrate the SMS receiver to the notification app configs", e)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(PreferenceMigrator71::class.java)
    }
}
