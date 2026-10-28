package nodomain.freeyourgadget.gadgetbridge.prefs.migrators;

import android.content.SharedPreferences;

import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator;
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs;

public class PreferenceMigrator69 extends AbstractPreferenceMigrator {
    private static final String OLD_CHARTS_HEART_RATE_COLOR = "chart_heartrate_color";
    private static final String OLD_CHARTS_RANGE = "charts_range";
    private static final String OLD_GLOBAL_CHARTS_TABS = "charts_tabs";

    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        final Object heartRateColor = sharedPrefs.getAll().get(OLD_CHARTS_HEART_RATE_COLOR);
        if (heartRateColor != null) {
            final boolean red = Boolean.parseBoolean(String.valueOf(heartRateColor));
            editor.putString(GBPrefs.CHARTS_HEART_RATE_COLOR, red ? "red" : "orange");
            editor.remove(OLD_CHARTS_HEART_RATE_COLOR);
        }

        final Object chartsRange = sharedPrefs.getAll().get(OLD_CHARTS_RANGE);
        if (chartsRange != null) {
            final boolean month = Boolean.parseBoolean(String.valueOf(chartsRange));
            editor.putString(GBPrefs.CHARTS_RANGE, month ? "month" : "week");
            editor.remove(OLD_CHARTS_RANGE);
        }

        editor.remove(OLD_GLOBAL_CHARTS_TABS);
    }
}
