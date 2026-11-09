package nodomain.freeyourgadget.gadgetbridge.prefs.migrators;

import android.content.SharedPreferences;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.prefs.AbstractPreferenceMigrator;
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper;
import nodomain.freeyourgadget.gadgetbridge.util.permissions.MissingPermissions;

public class PreferenceMigrator75 extends AbstractPreferenceMigrator {
    private static final Logger LOG = LoggerFactory.getLogger(PreferenceMigrator75.class);

    private static final String OLD_PERMISSION_PESTERING = "permission_pestering";

    @Override
    public void migrate(final int oldVersion, final SharedPreferences sharedPrefs, final SharedPreferences.Editor editor) {
        // Avoid the device card hint for users who selected "Do not ask again"
        if (!sharedPrefs.getBoolean(OLD_PERMISSION_PESTERING, true)) {
            try (DBHandler db = GBApplication.acquireDbReadOnly()) {
                for (final Device dbDevice : DBHelper.getActiveDevices(db.getDaoSession())) {
                    final GBDevice device = DeviceHelper.getInstance().toGBDevice(dbDevice);
                    MissingPermissions.save(GBApplication.getContext(), device);
                }
            } catch (final Exception e) {
                LOG.error("Failed to save the missing permissions for devices", e);
            }
        }

        editor.remove(OLD_PERMISSION_PESTERING);
    }
}
