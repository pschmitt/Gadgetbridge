package nodomain.freeyourgadget.gadgetbridge.util;

import android.content.Context;

import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.export.ActivityTrackExporter;
import nodomain.freeyourgadget.gadgetbridge.export.GPXExporter;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.model.GpxActivityTrackProvider;
import nodomain.freeyourgadget.gadgetbridge.util.elevation.ElevationTiles;

public final class ActivitySummaryUtils {
    private static final Logger LOG = LoggerFactory.getLogger(ActivitySummaryUtils.class);

    private ActivitySummaryUtils() {
        // utility class
    }

    /**
     * File name without extension for an exported workout: {@code <iso start>-<kind>}, so that a
     * folder of exports sorts chronologically. Shared by the automatic FIT export, the FIT built
     * for sharing and uploading, and the workout list export, which must agree on it.
     */
    public static String getExportBaseName(final Context context, final BaseActivitySummary summary) {
        final String kindLabel = ActivityKind.fromCode(summary.getActivityKind()).getLabel(context).toLowerCase(Locale.ROOT);
        final String isoDate = DateTimeUtils.formatIso8601(summary.getStartTime());
        return FileUtils.makeValidFileName(isoDate + "-" + kindLabel);
    }

    @Nullable
    public static File getShareableGpxFile(final ActivityTrackProvider activityTrackProvider,
                                           @Nullable final GBDevice device,
                                           final BaseActivitySummary summary) {
        if (activityTrackProvider == null) {
            return null;
        }

        if (activityTrackProvider instanceof GpxActivityTrackProvider && !ElevationTiles.isActive(GBApplication.getContext(), device)) {
            // Avoid re-processing what already is a gpx file, unless elevation tiles may correct it
            final String gpxTrack = summary.getGpxTrack();
            if (gpxTrack != null) {
                return FileUtils.tryFixPath(new File(gpxTrack));
            }
        }

        final ActivityTrack activityTrack = resolveExportableTrack(activityTrackProvider, device, summary);
        if (activityTrack == null) {
            return null;
        }

        try {
            return writeToTmpGpx(activityTrack, summary);
        } catch (final Exception e) {
            LOG.error("Failed to get gpx track", e);
        }

        return null;
    }

    /**
     * The track to export for {@code summary}: the one the device recorded, or the one in the gpx
     * file attached to the workout when the device recorded no position.
     *
     * A device that tracks no GPS still answers with a track, of heart rate and cadence points, so
     * having a track is not the same as having a route, and an attached gpx is then the only source
     * of one.
     *
     * The two are not merged: the gpx replaces the device's track wholesale, so an export built
     * from it carries the route and not the device's per-point samples. Aligning the two would mean
     * trusting the watch and the phone to agree on the time of day.
     */
    @Nullable
    public static ActivityTrack resolveExportableTrack(@Nullable final ActivityTrackProvider activityTrackProvider,
                                                       @Nullable final GBDevice device,
                                                       final BaseActivitySummary summary) {
        final ActivityTrack deviceTrack = activityTrackProvider != null
                ? activityTrackProvider.getActivityTrack(summary)
                : null;
        final ActivityTrack track;
        if (hasLocation(deviceTrack) || activityTrackProvider instanceof GpxActivityTrackProvider) {
            track = deviceTrack;
        } else {
            final ActivityTrack attachedTrack = new GpxActivityTrackProvider().getActivityTrack(summary);
            track = hasLocation(attachedTrack) ? attachedTrack : deviceTrack;
        }

        if (track != null) {
            // Altitude from imported elevation tiles replaces what the device recorded
            ElevationTiles.correct(GBApplication.getContext(), device, ActivityKind.fromCode(summary.getActivityKind()), track);
        }
        return track;
    }

    /** Whether {@code track} holds at least one point with a position. */
    private static boolean hasLocation(@Nullable final ActivityTrack track) {
        if (track == null) {
            return false;
        }
        for (final ActivityPoint point : track.getAllPoints()) {
            if (point.getLocation() != null) {
                return true;
            }
        }
        return false;
    }

    private static File writeToTmpGpx(final ActivityTrack activityTrack,
                                      final BaseActivitySummary summary) throws IOException, ActivityTrackExporter.GPXTrackEmptyException {
        final String summaryDate = DateTimeUtils.formatIso8601(summary.getStartTime());
        final String gpxFileName;
        if (activityTrack.getName() != null) {
            gpxFileName = FileUtils.makeValidFileName(activityTrack.getName() + "_" + summaryDate + ".gpx");
        } else {
            gpxFileName = FileUtils.makeValidFileName("gadgetbridge-" + summaryDate + ".gpx");
        }

        final File cacheDir = GBApplication.getContext().getCacheDir();
        final File rawCacheDir = new File(cacheDir, "gpx");
        //noinspection ResultOfMethodCallIgnored
        rawCacheDir.mkdir();
        final File gpxFile = new File(rawCacheDir, gpxFileName);

        final GPXExporter gpxExporter = new GPXExporter();
        try {
            gpxExporter.performExport(activityTrack, gpxFile, summary);
        } catch (final IOException | ActivityTrackExporter.GPXTrackEmptyException e) {
            //noinspection ResultOfMethodCallIgnored
            gpxFile.delete();
            throw e;
        }

        return gpxFile;
    }
}
