package nodomain.freeyourgadget.gadgetbridge.util

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import org.slf4j.LoggerFactory
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Answers questions about the phone's Do Not Disturb state: the active interruption filter, and whether
 * a phone number may break through it.
 */
@Singleton
class DoNotDisturbPolicy @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationManager: NotificationManager,
) {
    /**
     * Returns true if a message or call from [number] is allowed through Do Not Disturb, for the given
     * [priorityType] ([NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES] or
     * [NotificationManager.Policy.PRIORITY_CATEGORY_CALLS]).
     */
    fun isPriorityNumber(priorityType: Int, number: String?): Boolean {
        val notificationPolicy = notificationManager.notificationPolicy
        if (priorityType == NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES) {
            if ((notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES) == NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES) {
                return isPrioritySender(notificationPolicy.priorityMessageSenders, number)
            }
        } else if (priorityType == NotificationManager.Policy.PRIORITY_CATEGORY_CALLS) {
            if ((notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS) == NotificationManager.Policy.PRIORITY_CATEGORY_CALLS) {
                return isPrioritySender(notificationPolicy.priorityCallSenders, number)
            }
        }
        return false
    }

    /**
     * Returns the current interruption filter, or [NotificationManager.INTERRUPTION_FILTER_ALL] if
     * Gadgetbridge has no access to the notification policy.
     */
    fun getGrantedInterruptionFilter(): Int {
        return if (notificationManager.isNotificationPolicyAccessGranted) {
            notificationManager.currentInterruptionFilter
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
    }

    private fun isPrioritySender(prioritySenders: Int, number: String?): Boolean {
        if (prioritySenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY) {
            return true
        } else if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            LOG.warn("Contacts permission not granted, unable to check if {} is a priority sender", number)
            return true
        } else if (number.isNullOrEmpty()) {
            // Unknown/hidden number cannot match a contact or a starred contact
            return false
        } else {
            val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            val projection = arrayOf(PhoneLookup._ID, PhoneLookup.STARRED)
            val cursor = context.contentResolver.query(uri, projection, null, null, null)
            var exists = false
            var starred = 0
            cursor?.use {
                if (it.moveToFirst()) {
                    exists = true
                    starred = it.getInt(it.getColumnIndexOrThrow(PhoneLookup.STARRED))
                }
            }
            return prioritySenders == NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS && exists || prioritySenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED && starred == 1
        }
    }

    private companion object {
        private val LOG = LoggerFactory.getLogger(DoNotDisturbPolicy::class.java)
    }
}
