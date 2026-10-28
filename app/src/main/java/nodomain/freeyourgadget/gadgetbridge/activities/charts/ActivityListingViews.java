package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.card.MaterialCardView;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySession;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

/** Formatting shared by the activity list, its summary dialog and the activity detail dialog. */
final class ActivityListingViews {
    private ActivityListingViews() {
    }

    static void fillTile(final View tile, @DrawableRes final int icon, final String value, @StringRes final int label) {
        StatTileGridUtilKt.applyStatTileStyle((MaterialCardView) tile, null);
        ((ImageView) tile.findViewById(R.id.stat_tile_icon)).setImageResource(icon);
        ((TextView) tile.findViewById(R.id.stat_tile_value)).setText(value);
        ((TextView) tile.findViewById(R.id.stat_tile_label)).setText(label);
    }

    static String formatDuration(final ActivitySession session) {
        return DateTimeUtils.formatDurationHoursMinutes(session.getEndTime().getTime() - session.getStartTime().getTime(), TimeUnit.MILLISECONDS);
    }

    static String formatMeta(final Context context, final ActivitySession session) {
        final Date start = session.getStartTime();
        final Date end = session.getEndTime();
        final String from = DateTimeUtils.formatTime(start.getHours(), start.getMinutes());
        final String to = DateTimeUtils.formatTime(end.getHours(), end.getMinutes());
        return context.getString(R.string.activity_list_row_meta, from, to, formatDuration(session));
    }

    @Nullable
    static String joinStats(final Context context, @Nullable final String first, @Nullable final String second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return context.getString(R.string.activity_list_row_stats_pair, first, second);
    }
}
