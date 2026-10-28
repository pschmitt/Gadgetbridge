package nodomain.freeyourgadget.gadgetbridge.database;

import android.database.sqlite.SQLiteDatabase;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;

import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * Imports the rows from CSV test resources into database tables of a device.
 * <p>
 * - Each file is named after a table
 * - The header row holds the column names
 * - Any DEVICE_ID and USER_ID columns are set to the given device and the test user
 * - An empty field is inserted as NULL.
 */
public final class CsvTableImporter {
    private CsvTableImporter() {
    }

    public static void importDirectory(final DaoSession session, final GBDevice gbDevice, final String resourceDir) {
        final URL url = CsvTableImporter.class.getResource(resourceDir);
        if (url == null) {
            throw new IllegalArgumentException("Resource directory not found: " + resourceDir);
        }

        final File[] files;
        try {
            files = new File(url.toURI()).listFiles((dir, name) -> name.endsWith(".csv"));
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Invalid resource directory: " + resourceDir, e);
        }
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("No CSV files in " + resourceDir);
        }

        final Device device = DBHelper.getDevice(gbDevice, session);
        final User user = DBHelper.getUser(session);
        final SQLiteDatabase database = session.getDatabase();
        database.beginTransaction();
        try {
            for (final File file : files) {
                final String table = file.getName().substring(0, file.getName().length() - ".csv".length());
                //noinspection DataFlowIssue
                importTable(database, table, file, device.getId(), user.getId());
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private static void importTable(final SQLiteDatabase database,
                                    final String table,
                                    final File file,
                                    final long deviceId,
                                    final long userId) {
        try (BufferedReader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            final String headerLine = reader.readLine();
            if (headerLine == null) {
                return;
            }
            final String[] columns = headerLine.split(",", -1);
            final String sql = "INSERT INTO \"" + table + "\" (DEVICE_ID, USER_ID, " +
                String.join(", ", columns) + ") VALUES (?, ?, " +
                String.join(", ", Collections.nCopies(columns.length, "?")) + ")";

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty()) {
                    continue;
                }
                final String[] fields = line.split(",", -1);
                if (fields.length != columns.length) {
                    throw new IllegalArgumentException("Column count mismatch in " + file + ": " + line);
                }
                final Object[] args = new Object[columns.length + 2];
                args[0] = deviceId;
                args[1] = userId;
                for (int i = 0; i < fields.length; i++) {
                    args[i + 2] = fields[i].isEmpty() ? null : Long.parseLong(fields[i]);
                }
                database.execSQL(sql, args);
            }
        } catch (final IOException e) {
            throw new UncheckedIOException("Failed to read " + file, e);
        }
    }
}
