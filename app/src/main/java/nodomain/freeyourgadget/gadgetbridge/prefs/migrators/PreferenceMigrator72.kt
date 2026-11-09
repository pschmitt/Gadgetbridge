package nodomain.freeyourgadget.gadgetbridge.prefs.migrators

import android.content.SharedPreferences
import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator

class PreferenceMigrator72 : AbstractPreferenceMigrator() {
    override fun migrate(oldVersion: Int, sharedPrefs: SharedPreferences, editor: SharedPreferences.Editor) {
        // Keep the previous behavior for existing users, but set the better default for new ones
        if (oldVersion > 0) {
            editor.putBoolean("notification_calls_ignore_application_list", false)
        }
    }
}
