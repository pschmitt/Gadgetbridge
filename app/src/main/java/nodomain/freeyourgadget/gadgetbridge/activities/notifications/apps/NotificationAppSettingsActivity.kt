package nodomain.freeyourgadget.gadgetbridge.activities.notifications.apps

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2

/**
 * The notification settings of the app in [EXTRA_PACKAGE_NAME], or of "All apps".
 */
class NotificationAppSettingsActivity : AbstractSettingsActivityV2() {
    override fun onCreate(savedInstanceState: Bundle?) {
        if (intent.getStringExtra(EXTRA_PACKAGE_NAME) == null) {
            finish()
        }
        super.onCreate(savedInstanceState)
    }

    override fun newFragment(): PreferenceFragmentCompat = NotificationAppSettingsFragment()

    companion object {
        const val EXTRA_PACKAGE_NAME = "packageName"
    }
}
