package nodomain.freeyourgadget.gadgetbridge.prefs.migrators;

import android.content.SharedPreferences;

import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator67 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        if (sharedPrefs.getBoolean("dashboard_as_default_view", true)) {
            editor.putString("default_main_screen", "dashboard");
        } else {
            editor.putString("default_main_screen", "devices");
        }
    }
}
