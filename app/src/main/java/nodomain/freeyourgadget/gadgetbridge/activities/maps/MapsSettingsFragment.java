/*  Copyright (C) 2025 José Rebelo

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities.maps;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.documentfile.provider.DocumentFile;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.reader.header.MapFileInfo;
import org.mapsforge.map.rendertheme.ZipXmlThemeResourceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipInputStream;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment;
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.UriUtils;
import nodomain.freeyourgadget.gadgetbridge.util.maps.MapsManager;

public class MapsSettingsFragment extends AbstractPreferenceFragment {
    private static final Logger LOG = LoggerFactory.getLogger(MapsSettingsFragment.class);

    public static final String ACTION_SETTING_CHANGE = "nodomain.freeyourgadget.gadgetbridge.maps.setting_change";
    public static final String EXTRA_SETTING_KEY = "nodomain.freeyourgadget.gadgetbridge.maps_setting_key";
    private static final String PREF_CATEGORY_MAP_FILES = "pref_category_map_files";
    private static final String PREF_MAP_FILE_PREFIX = "pref_map_file_";
    private static final String PREF_MAP_THEME_CUSTOM_FILE = "maps_theme_custom_file";
    private static final String PREF_MAP_THEME_DARK_CUSTOM_FILE = "maps_theme_dark_custom_file";
    private static final String[] THEME_FILE_MIME_TYPES = {"application/zip", "application/x-zip-compressed", "*/*"};

    @Override
    public void onCreatePreferences(@Nullable final Bundle savedInstanceState, @Nullable final String rootKey) {
        setPreferencesFromResource(R.xml.map_settings, rootKey);

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) {
            requireActivity().finish();
            return;
        }

        final Preference prefDownload = Objects.requireNonNull(findPreference("maps_download"));
        prefDownload.setOnPreferenceClickListener(preference -> {
            startActivity(new Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/")
            ));
            return true;
        });

        final Preference prefFolder = Objects.requireNonNull(findPreference(MapsManager.PREF_MAPS_FOLDER));
        final ActivityResultLauncher<Uri> mapsFolderChooser = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree(),
            localUri -> {
                LOG.info("Maps folder: {}", localUri);
                if (localUri != null) {
                    requireContext().getContentResolver().takePersistableUriPermission(localUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    prefs.edit()
                        .putString(MapsManager.PREF_MAPS_FOLDER, localUri.toString())
                        .apply();
                    prefFolder.setSummary(UriUtils.INSTANCE.resolveLocationSummary(requireContext(), localUri.toString()));
                    broadcastPreferenceChange(MapsManager.PREF_MAPS_FOLDER);
                    refreshMapFiles();
                }
            }
        );
        final String currentFolder = prefs.getString(MapsManager.PREF_MAPS_FOLDER, "");
        prefFolder.setSummary(UriUtils.INSTANCE.resolveLocationSummary(requireContext(), currentFolder));
        prefFolder.setOnPreferenceClickListener(preference -> {
            mapsFolderChooser.launch(null);
            return true;
        });

        refreshMapFiles();

        setupTheme(prefs, MapsManager.PREF_MAP_THEME, PREF_MAP_THEME_CUSTOM_FILE, MapsManager.PREF_MAP_THEME_CUSTOM_URI);
        setupTheme(prefs, MapsManager.PREF_MAP_THEME_DARK, PREF_MAP_THEME_DARK_CUSTOM_FILE, MapsManager.PREF_MAP_THEME_DARK_CUSTOM_URI);

        final Preference prefTrackColor = Objects.requireNonNull(findPreference(MapsManager.PREF_TRACK_COLOR));
        prefTrackColor.setOnPreferenceChangeListener((preference, newValue) -> {
            broadcastPreferenceChange(MapsManager.PREF_TRACK_COLOR);
            return true;
        });
    }

    private void setupTheme(final SharedPreferences prefs,
                            final String themePrefKey,
                            final String filePrefKey,
                            final String uriPrefKey) {
        final ListPreference prefTheme = Objects.requireNonNull(findPreference(themePrefKey));
        final Preference prefThemeFile = Objects.requireNonNull(findPreference(filePrefKey));
        prefTheme.setOnPreferenceChangeListener((preference, newValue) -> {
            broadcastPreferenceChange(themePrefKey);
            prefThemeFile.setVisible(MapsManager.MAP_THEME_VALUE_CUSTOM.equals(newValue));
            return true;
        });
        prefThemeFile.setVisible(MapsManager.MAP_THEME_VALUE_CUSTOM.equals(prefTheme.getValue()));

        final ActivityResultLauncher<String[]> themeFileChooser = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(),
            fileUri -> {
                if (fileUri == null) {
                    return;
                }
                final String problem = validateRenderTheme(fileUri);
                if (problem != null) {
                    GB.toast(requireContext().getString(R.string.map_theme_custom_file_invalid, problem), Toast.LENGTH_LONG, GB.ERROR);
                    return;
                }
                try {
                    requireContext().getContentResolver().takePersistableUriPermission(fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (final SecurityException e) {
                    LOG.error("Could not persist permission for theme file {}", fileUri, e);
                    GB.toast(requireContext().getString(R.string.map_theme_custom_file_invalid, e.getMessage()), Toast.LENGTH_LONG, GB.ERROR);
                    return;
                }
                prefs.edit()
                    .putString(uriPrefKey, fileUri.toString())
                    .apply();
                prefThemeFile.setSummary(UriUtils.INSTANCE.resolveLocationSummary(requireContext(), fileUri.toString()));
                broadcastPreferenceChange(themePrefKey);
            }
        );
        final String currentThemeFile = prefs.getString(uriPrefKey, "");
        if (!currentThemeFile.isEmpty()) {
            prefThemeFile.setSummary(UriUtils.INSTANCE.resolveLocationSummary(requireContext(), currentThemeFile));
        }
        prefThemeFile.setOnPreferenceClickListener(preference -> {
            themeFileChooser.launch(THEME_FILE_MIME_TYPES);
            return true;
        });
    }

    /**
     * Checks the picked theme up front, so a broken file gets rejected with a message instead of
     * showing up later as a blank map.
     *
     * @return null if the archive holds a render theme, otherwise the problem
     */
    private String validateRenderTheme(final Uri uri) {
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return "the file could not be opened";
            }
            final BufferedInputStream buffered = new BufferedInputStream(in);
            buffered.mark(2);
            final byte[] magic = new byte[2];
            final int read = buffered.read(magic);
            buffered.reset();
            if (read != 2 || magic[0] != 'P' || magic[1] != 'K') {
                return "a zip archive with the render theme and its icons is required";
            }

            final ZipXmlThemeResourceProvider provider = new ZipXmlThemeResourceProvider(new ZipInputStream(buffered));
            if (provider.getXmlThemes().isEmpty()) {
                return "the archive contains no rendertheme";
            }
            return null;
        } catch (final IOException e) {
            return "could not read the archive: " + e.getMessage();
        } catch (final Exception e) {
            return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        }
    }

    private void refreshMapFiles() {
        final PreferenceCategory category = findPreference(PREF_CATEGORY_MAP_FILES);
        if (category == null) {
            return;
        }

        // Remove the dynamically added preferences from a previous refresh
        for (int i = 0; i < category.getPreferenceCount(); i++) {
            final Preference pref = category.getPreference(i);
            if (pref.getKey() != null && pref.getKey().startsWith(PREF_MAP_FILE_PREFIX)) {
                category.removePreference(pref);
            }
        }

        final SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        if (prefs == null) {
            return;
        }

        final List<DocumentFile> mapFiles = new ArrayList<>();
        final String folderUri = prefs.getString(MapsManager.PREF_MAPS_FOLDER, "");
        if (folderUri.isEmpty()) {
            // no folder selected
            category.setVisible(false);
            return;
        }

        final DocumentFile folder = DocumentFile.fromTreeUri(requireContext(), Uri.parse(folderUri));
        if (folder != null) {
            for (final DocumentFile documentFile : folder.listFiles()) {
                final String name = documentFile.getName();
                if (name != null && name.endsWith(".map")) {
                    mapFiles.add(documentFile);
                }
            }
        }

        // No map files
        if (mapFiles.isEmpty()) {
            final Preference emptyPref = new Preference(requireContext());
            emptyPref.setKey(PREF_MAP_FILE_PREFIX + "empty");
            emptyPref.setSelectable(false);
            emptyPref.setSummary(R.string.maps_folder_empty);
            emptyPref.setIconSpaceReserved(false);
            category.addPreference(emptyPref);
            return;
        }

        for (final DocumentFile documentFile : mapFiles) {
            final Preference pref = new Preference(requireContext());
            pref.setKey(PREF_MAP_FILE_PREFIX + documentFile.getUri());
            pref.setSelectable(false);
            pref.setTitle(documentFile.getName());
            pref.setIcon(R.drawable.ic_map);

            updateMapInfo(pref, documentFile);

            category.addPreference(pref);
        }
    }

    private void updateMapInfo(final Preference pref, final DocumentFile documentFile) {
        try {
            final FileInputStream inputStream = (FileInputStream) requireContext().getContentResolver().openInputStream(documentFile.getUri());
            if (inputStream == null) {
                throw new IOException("FileInputStream is null");
            }
            // Reading the header fails with a MapFileException
            final MapFile mapFile = new MapFile(inputStream, 0, null);
            final MapFileInfo mapFileInfo = mapFile.getMapFileInfo();

            final String formattedDate = DateUtils.formatDateTime(
                requireContext(),
                mapFileInfo.mapDate,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_TIME
            );
            pref.setSummary(formattedDate + "\n" + FormatUtils.formatBytes(mapFileInfo.fileSize));

            mapFile.close();
        } catch (final Exception e) {
            LOG.error("Failed to get MapFileInfo for {}", documentFile.getName(), e);
            pref.setSummary(requireContext().getString(R.string.maps_file_invalid, e.getMessage()));
        }
    }

    private void broadcastPreferenceChange(final String key) {
        final Intent intent = new Intent();
        intent.setAction(ACTION_SETTING_CHANGE);
        intent.putExtra(EXTRA_SETTING_KEY, key);
        LocalBroadcastManager.getInstance(requireContext()).sendBroadcast(intent);
    }
}
