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
package nodomain.freeyourgadget.gadgetbridge.util.elevation

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.annotation.VisibleForTesting
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException

/**
 * The user's imported SRTM tiles, and the correction of workout tracks from them.
 * Nothing is downloaded: the user imports tiles obtained elsewhere.
 *
 * The correction is switched on per device, since a watch with a barometer records
 * a better profile than the model, while one without is at the mercy of GPS altitude.
 */
object ElevationTiles {
    private val LOG = LoggerFactory.getLogger(ElevationTiles::class.java)

    private const val DIRECTORY_NAME = "elevation_tiles"

    @Volatile
    private var library: HgtTileLibrary? = null

    /** Whether any tile is imported, so a track is not walked for nothing. Refreshed on every change. */
    @Volatile
    private var hasTiles: Boolean? = null

    @JvmStatic
    fun library(context: Context): HgtTileLibrary =
        library ?: synchronized(this) {
            library ?: HgtTileLibrary(File(context.applicationContext.filesDir, DIRECTORY_NAME)).also { library = it }
        }

    /** The device's own switch, off unless the user turned it on for that device. */
    @JvmStatic
    fun isEnabledFor(device: GBDevice?): Boolean =
        device != null && GBApplication.getDevicePrefs(device).getBoolean(DeviceSettingsPreferenceConst.PREF_ELEVATION_CORRECTION, false)

    @JvmStatic
    fun tiles(context: Context): List<ElevationTile> =
        library(context).scan().also { hasTiles = it.isNotEmpty() }

    @JvmStatic
    @Throws(IOException::class)
    fun importTiles(context: Context, uri: Uri): List<ElevationTile> {
        val displayName = displayNameOf(context, uri)?.takeIf { it.isNotBlank() } ?: uri.lastPathSegment.orEmpty()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Unable to open the selected elevation tile.")
        val tiles = input.use { library(context).importTiles(displayName, it) }
        hasTiles = true
        return tiles
    }

    @JvmStatic
    fun deleteTile(context: Context, key: HgtTileKey): Boolean =
        library(context).deleteTile(key).also { tiles(context) }

    /** Whether a track of this device could be corrected at all: its switch is on and a tile is imported. */
    @JvmStatic
    fun isActive(context: Context?, device: GBDevice?): Boolean =
        context != null && isEnabledFor(device) && (hasTiles ?: tiles(context).isNotEmpty())

    /**
     * Replaces the track's altitudes with tile altitudes when the device's switch is on, the
     * activity follows the terrain and the tiles cover every positioned point on ground that is
     * not too steep for the fixes. Anything else leaves the track as recorded.
     */
    @JvmStatic
    fun correct(context: Context?, device: GBDevice?, kind: ActivityKind?, track: ActivityTrack): Boolean =
        correct(context, device, kind, track.allPoints)

    /** As [correct] for a track, for code that holds the points. */
    @JvmStatic
    fun correct(context: Context?, device: GBDevice?, kind: ActivityKind?, points: List<ActivityPoint>): Boolean {
        if (context == null || !isActive(context, device)) {
            return false
        }
        if (kind != null && !ActivityKind.followsTerrain(kind)) {
            LOG.debug("Not correcting the altitude of a {} workout, it does not follow the terrain", kind)
            return false
        }
        val library = library(context)
        return try {
            val outcome = ElevationCorrection.correct(points, library::elevationAt)
            if (outcome != ElevationCorrection.Outcome.CORRECTED) {
                LOG.debug("Keeping the track's altitudes: {}", outcome)
            }
            outcome == ElevationCorrection.Outcome.CORRECTED
        } catch (e: Exception) {
            // A tile that vanished under a mapping: keep what the device recorded
            LOG.warn("Failed to read elevation tiles, keeping the track's altitudes", e)
            false
        }
    }

    /**
     * Replaces the device's ascent, descent and altitude range in [data] with those of
     * the corrected [points], as the tiles make the device's own figures stale.
     */
    @JvmStatic
    fun applyToSummary(data: ActivitySummaryData, points: List<ActivityPoint>) {
        val change = ElevationCorrection.elevationChange(points)
        val ascentKeys = listOf(ActivitySummaryEntries.TOTAL_ASCENT, ActivitySummaryEntries.ASCENT_METERS, ActivitySummaryEntries.ELEVATION_GAIN)
            .filter { data.has(it) }
            .ifEmpty { listOf(ActivitySummaryEntries.TOTAL_ASCENT) }
        val descentKeys = listOf(ActivitySummaryEntries.TOTAL_DESCENT, ActivitySummaryEntries.DESCENT_METERS, ActivitySummaryEntries.ELEVATION_LOSS)
            .filter { data.has(it) }
            .ifEmpty { listOf(ActivitySummaryEntries.TOTAL_DESCENT) }
        for (key in ascentKeys) {
            data.add(key, Math.round(change.gain), ActivitySummaryEntries.UNIT_METERS, true)
        }
        for (key in descentKeys) {
            data.add(key, Math.round(change.loss), ActivitySummaryEntries.UNIT_METERS, true)
        }
        ElevationCorrection.altitudeRange(points)?.let { (min, max, average) ->
            data.add(ActivitySummaryEntries.ALTITUDE_MIN, Math.round(min), ActivitySummaryEntries.UNIT_METERS, true)
            data.add(ActivitySummaryEntries.ALTITUDE_MAX, Math.round(max), ActivitySummaryEntries.UNIT_METERS, true)
            data.add(ActivitySummaryEntries.ALTITUDE_AVG, Math.round(average), ActivitySummaryEntries.UNIT_METERS, true)
        }
    }

    @VisibleForTesting
    internal fun resetForTests() {
        library = null
        hasTiles = null
    }

    private fun displayNameOf(context: Context, uri: Uri): String? =
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 } ?: return@use null
            cursor.getString(index)
        }
}
