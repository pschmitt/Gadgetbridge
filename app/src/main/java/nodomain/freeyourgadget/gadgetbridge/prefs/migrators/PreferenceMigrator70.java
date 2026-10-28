package nodomain.freeyourgadget.gadgetbridge.prefs.migrators;

import android.content.SharedPreferences;

import nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppMigration;
import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator;

public class PreferenceMigrator70 extends AbstractPreferenceMigrator {
    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        NotificationAppMigration.INSTANCE.migrate(sharedPrefs, editor);
    }
}
