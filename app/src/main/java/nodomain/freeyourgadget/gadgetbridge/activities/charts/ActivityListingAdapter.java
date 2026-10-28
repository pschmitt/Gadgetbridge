/*  Copyright (C) 2020-2024 José Rebelo, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.ActivityIconView;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.RowSelection;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.adapter.AbstractActivityListingAdapter;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySession;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser;
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils;
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors;

public class ActivityListingAdapter extends AbstractActivityListingAdapter<ActivitySession> {
    protected static final Logger LOG = LoggerFactory.getLogger(ActivityListingAdapter.class);
    ActivityUser activityUser = new ActivityUser();
    int stepsGoal = activityUser.getStepsGoal();
    int distanceGoalMeters = activityUser.getDistanceGoalMeters();
    long activeTimeGoalTimeMillis = activityUser.getActiveTimeGoalMinutes() * 60 * 1000L;

    public ActivityListingAdapter(Context context) {
        super(context);
    }

    @NonNull
    @Override
    public AbstractActivityListingViewHolder<ActivitySession> onCreateViewHolder(@NonNull final ViewGroup parent, final int viewType) {
        return switch (viewType) {
            case 0 -> // dashboard
                new DashboardViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.activity_list_dashboard_item, parent, false));
            case 2 -> // item
                new ActivityItemViewHolder(LayoutInflater.from(getContext()).inflate(R.layout.item_activity_row, parent, false));
            default -> super.onCreateViewHolder(parent, viewType);
        };
    }

    private static int percentOf(final double value, final double goal) {
        return goal > 0 ? (int) (value * 100 / goal) : 0;
    }

    public class ActivityItemViewHolder extends AbstractActivityListingViewHolder<ActivitySession> {
        final ActivityIconView iconView;
        final TextView nameView;
        final TextView metaView;
        final TextView statsTopView;
        final TextView statsBottomView;
        final View separatorView;
        final RowSelection selection;

        public ActivityItemViewHolder(@NonNull final View itemView) {
            super(itemView);
            iconView = itemView.findViewById(R.id.activity_row_icon);
            nameView = itemView.findViewById(R.id.activity_row_name);
            metaView = itemView.findViewById(R.id.activity_row_meta);
            statsTopView = itemView.findViewById(R.id.activity_row_stats_top);
            statsBottomView = itemView.findViewById(R.id.activity_row_stats_bottom);
            separatorView = itemView.findViewById(R.id.activity_row_separator);
            selection = new RowSelection(itemView, iconView);
        }

        @Override
        public void fill(final int position, final ActivitySession session, final boolean selected) {
            final Context context = getContext();
            final String name = session.getActivityKind().getLabel(context);
            final String meta = ActivityListingViews.formatMeta(context, session);

            iconView.setImageResource(session.getActivityKind().getIcon());
            nameView.setText(name);
            metaView.setText(meta);

            final int steps = session.getActiveSteps();
            final String stepsText = steps > 0 ? context.getResources().getQuantityString(R.plurals.activity_list_steps_count, steps, steps) : null;
            final float distance = session.getDistance();
            final String distanceText = distance > 0 ? FormatUtils.getFormattedDistanceLabel(distance) : null;
            final int hr = session.getHeartRateAverage();
            final String hrText = hr > 0 ? context.getString(R.string.bpm_value_unit, hr) : null;
            final float intensity = session.getIntensity();
            final String intensityText = intensity >= 1f ? context.getString(R.string.activity_list_intensity_value, String.valueOf(Math.round(intensity))) : null;

            final String statsTop = ActivityListingViews.joinStats(context, stepsText, distanceText);
            final String statsBottom = ActivityListingViews.joinStats(context, hrText, intensityText);
            setStats(statsTopView, statsTop);
            setStats(statsBottomView, statsBottom);

            final StringBuilder description = new StringBuilder(name).append(", ").append(meta);
            if (statsTop != null) {
                description.append(", ").append(statsTop);
            }
            if (statsBottom != null) {
                description.append(", ").append(statsBottom);
            }
            itemView.setContentDescription(description);
            selection.bind(position, selected, !getSelectedItems().isEmpty());
            // The last activity is followed by the end spacer, and gets no separator.
            separatorView.setVisibility(position == getItemCount() - 2 ? View.GONE : View.VISIBLE);
        }

        private void setStats(final TextView view, final String text) {
            view.setText(text);
            view.setVisibility(text != null ? View.VISIBLE : View.GONE);
        }
    }

    public class DashboardViewHolder extends AbstractActivityListingViewHolder<ActivitySession> {
        final View activeStepsRow;
        final View distanceRow;
        final View activeTimeRow;
        final View totalStepsTile;
        final View activitiesTile;
        final View avgHrTile;
        final View intensityTile;
        final TextView noActivitiesView;

        public DashboardViewHolder(@NonNull final View itemView) {
            super(itemView);
            activeStepsRow = itemView.findViewById(R.id.activity_list_goal_active_steps);
            distanceRow = itemView.findViewById(R.id.activity_list_goal_distance);
            activeTimeRow = itemView.findViewById(R.id.activity_list_goal_active_time);
            totalStepsTile = itemView.findViewById(R.id.activity_list_tile_total_steps);
            activitiesTile = itemView.findViewById(R.id.activity_list_tile_activities);
            avgHrTile = itemView.findViewById(R.id.activity_list_tile_avg_hr);
            intensityTile = itemView.findViewById(R.id.activity_list_tile_intensity);
            noActivitiesView = itemView.findViewById(R.id.activity_list_no_activities);
            StatTileGridUtilKt.applyStatTileStyle(itemView.findViewById(R.id.activity_list_goal_card), null);
        }

        @Override
        public void fill(final int position, final ActivitySession session, final boolean selected) {
            final Context context = getContext();

            final int steps = session.getActiveSteps();
            fillGoalRow(activeStepsRow, R.drawable.ic_footprint, R.string.activity_list_summary_active_steps,
                String.valueOf(steps), percentOf(steps, stepsGoal), WidgetColors.INSTANCE.getActivity());

            final float distance = session.getDistance();
            fillGoalRow(distanceRow, R.drawable.ic_distance, R.string.distance,
                FormatUtils.getFormattedDistanceLabel(distance), percentOf(distance, distanceGoalMeters), WidgetColors.INSTANCE.getDistance());

            final long duration = session.getEndTime().getTime() - session.getStartTime().getTime();
            fillGoalRow(activeTimeRow, R.drawable.ic_access_time, R.string.activity_list_summary_active_time,
                ActivityListingViews.formatDuration(session), percentOf(duration, activeTimeGoalTimeMillis), WidgetColors.INSTANCE.getActiveTime());

            ActivityListingViews.fillTile(totalStepsTile, R.drawable.ic_steps, String.valueOf(session.getTotalDaySteps()), R.string.live_activity_total_steps);
            final int hr = session.getHeartRateAverage();
            ActivityListingViews.fillTile(avgHrTile, R.drawable.ic_heartrate, hr > 0 ? context.getString(R.string.bpm_value_unit, hr) : "-", R.string.averageHR);
            final float intensity = session.getIntensity();
            final View activitiesSlot;
            if (intensity > 0) {
                ActivityListingViews.fillTile(intensityTile, R.drawable.ic_intensity, String.valueOf(Math.round(intensity)), R.string.movement_intensity);
                activitiesSlot = activitiesTile;
                activitiesTile.setVisibility(View.VISIBLE);
            } else {
                activitiesSlot = intensityTile;
                activitiesTile.setVisibility(View.INVISIBLE);
            }
            ActivityListingViews.fillTile(activitiesSlot, R.drawable.ic_list_numbered, String.valueOf(session.getSessionCount()), R.string.activity_list_summary_activities);

            // Only the dashboard and the end spacer are left when the day has no activities.
            noActivitiesView.setVisibility(getItemCount() <= 2 ? View.VISIBLE : View.GONE);
        }

        private void fillGoalRow(final View row, @DrawableRes final int icon, final int label, final String value, final int percent, final int color) {
            final Context context = getContext();
            final String labelText = context.getString(label);
            ((ImageView) row.findViewById(R.id.goal_row_icon)).setImageResource(icon);
            ((TextView) row.findViewById(R.id.goal_row_label)).setText(labelText);
            ((TextView) row.findViewById(R.id.goal_row_value)).setText(value);
            ((TextView) row.findViewById(R.id.goal_row_percent)).setText(percent + "%");
            final LinearProgressIndicator bar = row.findViewById(R.id.goal_row_bar);
            bar.setIndicatorColor(color);
            bar.setProgress(Math.max(0, Math.min(100, percent)));
            row.setContentDescription(labelText + ", " + value + ", " + percent + "%");
        }
    }
}
