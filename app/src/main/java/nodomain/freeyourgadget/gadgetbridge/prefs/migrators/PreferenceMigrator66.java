package nodomain.freeyourgadget.gadgetbridge.prefs.migrators;

import android.content.SharedPreferences;

import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator66 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        // If the user had indigo or custom selected, keep that for the dark theme
        final String currentTheme = sharedPrefs.getString("maps_theme", null);
        if ("indigo".equals(currentTheme) || "custom".equals(currentTheme)) {
            editor.putString("maps_theme_dark", currentTheme);
            editor.putString("maps_theme_dark_custom_file", sharedPrefs.getString("maps_theme_custom_file", null));
        }
    }
}
