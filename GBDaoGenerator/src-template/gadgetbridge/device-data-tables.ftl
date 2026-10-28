package nodomain.freeyourgadget.gadgetbridge.entities;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Rows from all tables that have a relation path to a device.
 */
public final class DeviceDataTables {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceDataTables.class);

    private static final String[][] DELETE_BY_DEVICE = {
<#list deleteStatements as statement>
            {"${statement[0]?j_string}", "${statement[1]?j_string}"},
</#list>
    };

    private static final String[][] SELECT_BY_DEVICE = {
<#list selectStatements as statement>
            {"${statement[0]?j_string}", "${statement[1]?j_string}"},
</#list>
    };

    private static final String[][] DELETE_ORPHANS = {
<#list orphanStatements as statement>
            {"${statement[0]?j_string}", "${statement[1]?j_string}"},
</#list>
    };

    private DeviceDataTables() {
    }

    public interface ProgressListener {
        void onProgress(int done, int total);
    }

    public interface TableConsumer {
        void accept(@NonNull String table, @NonNull Cursor cursor) throws IOException;
    }

    /**
     * Deletes the rows that belong to a device. Cascades to all child tables.
     * Does not touch the device table itself.
     */
    public static void delete(@NonNull final SQLiteDatabase db,
                              final long deviceId,
                              @Nullable final ProgressListener listener) {
        reportProgress(listener, 0, DELETE_BY_DEVICE.length);
        for (int i = 0; i < DELETE_BY_DEVICE.length; i++) {
            final String[] statement = DELETE_BY_DEVICE[i];
            try (SQLiteStatement compiled = db.compileStatement(statement[1])) {
                compiled.bindLong(1, deviceId);
                final int count = compiled.executeUpdateDelete();
                LOG.debug("Deleted {} rows from {} for device {}", count, statement[0], deviceId);
            }
            reportProgress(listener, i + 1, DELETE_BY_DEVICE.length);
        }
    }

    /**
     * Queries the rows that belong to a device, one table at a time, parents before children.
     * Includes the row of the device itself. The cursor is closed after the consumer returns.
     */
    public static void query(@NonNull final SQLiteDatabase db,
                             final long deviceId,
                             @NonNull final TableConsumer consumer,
                             @Nullable final ProgressListener listener) throws IOException {
        final String[] args = {String.valueOf(deviceId)};
        reportProgress(listener, 0, SELECT_BY_DEVICE.length);
        for (int i = 0; i < SELECT_BY_DEVICE.length; i++) {
            final String[] statement = SELECT_BY_DEVICE[i];
            try (Cursor cursor = db.rawQuery(statement[1], args)) {
                consumer.accept(statement[0], cursor);
            }
            reportProgress(listener, i + 1, SELECT_BY_DEVICE.length);
        }
    }

    /**
     * Deletes the rows whose parent rows do not exist anymore, parents before children.
     */
    public static void deleteOrphans(@NonNull final SQLiteDatabase db, @Nullable final ProgressListener listener) {
        reportProgress(listener, 0, DELETE_ORPHANS.length);
        for (int i = 0; i < DELETE_ORPHANS.length; i++) {
            final String[] statement = DELETE_ORPHANS[i];
            try (SQLiteStatement compiled = db.compileStatement(statement[1])) {
                final int count = compiled.executeUpdateDelete();
                LOG.debug("Deleted {} orphan rows from {}", count, statement[0]);
            }
            reportProgress(listener, i + 1, DELETE_ORPHANS.length);
        }
    }

    private static void reportProgress(@Nullable final ProgressListener listener, final int done, final int total) {
        if (listener != null) {
            listener.onProgress(done, total);
        }
    }
}
