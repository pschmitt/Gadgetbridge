/*  Copyright (C) 2026 Manuel Marcatili

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
package nodomain.freeyourgadget.gadgetbridge.activities.elevation

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractPreferenceFragment
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils
import nodomain.freeyourgadget.gadgetbridge.util.GB
import nodomain.freeyourgadget.gadgetbridge.util.elevation.ElevationTile
import nodomain.freeyourgadget.gadgetbridge.util.elevation.ElevationTiles
import nodomain.freeyourgadget.gadgetbridge.util.elevation.HgtResolution
import org.slf4j.LoggerFactory

/** Import, list and delete the SRTM tiles workouts take their altitude from. */
class ElevationSettingsFragment : AbstractPreferenceFragment() {

    private val tileChooser = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importTiles(uris)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.elevation_settings, rootKey)

        findPreference<Preference>(PREF_DOWNLOAD)?.setOnPreferenceClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, TILES_URL.toUri()))
            true
        }
        findPreference<Preference>(PREF_IMPORT)?.setOnPreferenceClickListener {
            // .hgt has no registered type
            tileChooser.launch(arrayOf("*/*"))
            true
        }

        refreshTiles()
    }

    private fun importTiles(uris: List<Uri>) {
        if (uris.isEmpty()) {
            return
        }
        val context = requireContext().applicationContext
        lifecycleScope.launch {
            for (uri in uris) {
                val result = withContext(Dispatchers.IO) {
                    runCatching { ElevationTiles.importTiles(context, uri) }
                }
                result.onSuccess { tiles ->
                    val tile = tiles.singleOrNull()
                    val message = if (tile != null) {
                        context.getString(R.string.elevation_tile_imported, tile.displayName, context.getString(resolutionLabel(tile.resolution)))
                    } else {
                        context.resources.getQuantityString(R.plurals.elevation_tiles_imported, tiles.size, tiles.size)
                    }
                    GB.toast(message, Toast.LENGTH_SHORT, GB.INFO)
                }.onFailure { e ->
                    LOG.warn("Failed to import elevation tile {}", uri, e)
                    GB.toast(
                        context.getString(R.string.elevation_tile_import_failed, e.message ?: e.javaClass.simpleName),
                        Toast.LENGTH_LONG,
                        GB.ERROR
                    )
                }
            }
            if (isAdded) {
                refreshTiles()
            }
        }
    }

    private fun refreshTiles() {
        val category = findPreference<PreferenceCategory>(PREF_CATEGORY_TILES) ?: return
        category.removeAll()

        val tiles = ElevationTiles.tiles(requireContext())
        if (tiles.isEmpty()) {
            category.addPreference(Preference(requireContext()).apply {
                key = PREF_TILE_PREFIX + "empty"
                isSelectable = false
                isIconSpaceReserved = false
                setSummary(R.string.elevation_tiles_empty)
            })
            return
        }

        for (tile in tiles) {
            category.addPreference(Preference(requireContext()).apply {
                key = PREF_TILE_PREFIX + tile.displayName
                title = tile.displayName
                summary = getString(
                    R.string.elevation_tile_details,
                    getString(resolutionLabel(tile.resolution)),
                    FormatUtils.formatBytes(tile.sizeBytes)
                )
                setIcon(R.drawable.ic_landscape)
                setOnPreferenceClickListener {
                    confirmDelete(tile)
                    true
                }
            })
        }
    }

    private fun confirmDelete(tile: ElevationTile) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.elevation_tile_delete_title, tile.displayName))
            .setMessage(R.string.elevation_tile_delete_message)
            .setPositiveButton(R.string.delete) { _, _ ->
                ElevationTiles.deleteTile(requireContext(), tile.key)
                refreshTiles()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun resolutionLabel(resolution: HgtResolution): Int = when (resolution) {
        HgtResolution.ONE_ARC_SECOND -> R.string.elevation_tile_resolution_1
        HgtResolution.THREE_ARC_SECOND -> R.string.elevation_tile_resolution_3
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ElevationSettingsFragment::class.java)

        private const val PREF_DOWNLOAD = "elevation_tiles_download"
        private const val PREF_IMPORT = "elevation_tiles_import"
        private const val PREF_CATEGORY_TILES = "pref_category_elevation_tiles"
        private const val PREF_TILE_PREFIX = "pref_elevation_tile_"

        /** Void-filled SRTM tiles for the whole world, free and without an account. */
        private const val TILES_URL = "https://viewfinderpanoramas.org/Coverage%20map%20viewfinderpanoramas_org3.htm"
    }
}
