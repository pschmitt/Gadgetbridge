/*  Copyright (C) 2021-2024 Petr Vaněk

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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.ActivitySummariesChartFragment;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySession;
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils;
import nodomain.freeyourgadget.gadgetbridge.util.dialogs.MaterialDialogFragment;

public class ActivityListingDetail extends MaterialDialogFragment {
    protected static final Logger LOG = LoggerFactory.getLogger(ActivityListingDetail.class);

    public ActivityListingDetail() {

    }

    public static ActivityListingDetail newInstance(int tsFrom, int tsTo, ActivitySession item, GBDevice device) {
        ActivityListingDetail frag = new ActivityListingDetail();
        Bundle args = new Bundle();
        args.putInt("tsFrom", tsFrom);
        args.putInt("tsTo", tsTo);
        args.putSerializable("item", item);
        args.putParcelable(GBDevice.EXTRA_DEVICE, device);
        frag.setArguments(args);
        return frag;
    }

    @Override
    protected int getThemeOverlay() {
        return R.style.ThemeOverlay_App_MaterialAlertDialog_WindowBackground;
    }

    @Override
    public View onCreateView(@NonNull final LayoutInflater inflater,
                             final ViewGroup container,
                             final Bundle savedInstanceState) {
        // The dialog inflates the view first. The fragment must use the same view, because the
        // child fragment manager puts the chart into it.
        final View dialogView = getView();
        if (dialogView != null) {
            return dialogView;
        }
        return inflater.inflate(R.layout.activity_list_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int tsFrom = getArguments().getInt("tsFrom");
        int tsTo = getArguments().getInt("tsTo");
        ActivitySession item = (ActivitySession) getArguments().getSerializable("item");
        GBDevice gbDevice;
        gbDevice = getArguments().getParcelable(GBDevice.EXTRA_DEVICE);
        if (gbDevice == null) {
            throw new IllegalArgumentException("Must provide a device when invoking this activity");
        }

        final ActivitySummariesChartFragment activitySummariesChartFragment = new ActivitySummariesChartFragment();
        getChildFragmentManager()
            .beginTransaction()
            .replace(R.id.chartsFragmentHolder, activitySummariesChartFragment)
            .commit();
        activitySummariesChartFragment.setDateAndGetData(null, gbDevice, tsFrom, tsTo);

        final Context context = requireContext();
        final ImageView iconView = view.findViewById(R.id.activity_detail_icon);
        iconView.setImageResource(item.getActivityKind().getIcon());
        ((TextView) view.findViewById(R.id.activity_detail_name)).setText(item.getActivityKind().getLabel(context));
        ((TextView) view.findViewById(R.id.activity_detail_meta)).setText(ActivityListingViews.formatMeta(context, item));

        ActivityListingViews.fillTile(view.findViewById(R.id.activity_detail_tile_duration), R.drawable.ic_access_time, ActivityListingViews.formatDuration(item), R.string.activity_detail_duration_label);
        final int steps = item.getActiveSteps();
        ActivityListingViews.fillTile(view.findViewById(R.id.activity_detail_tile_steps), R.drawable.ic_steps, steps > 0 ? String.valueOf(steps) : "-", R.string.steps);
        final int hr = item.getHeartRateAverage();
        ActivityListingViews.fillTile(view.findViewById(R.id.activity_detail_tile_avg_hr), R.drawable.ic_heartrate, hr > 0 ? context.getString(R.string.bpm_value_unit, hr) : "-", R.string.averageHR);
        final float distance = item.getDistance();
        final View distanceTile = view.findViewById(R.id.activity_detail_tile_distance);
        if (distance > 0) {
            ActivityListingViews.fillTile(distanceTile, R.drawable.ic_distance, FormatUtils.getFormattedDistanceLabel(distance), R.string.distance);
        }
        distanceTile.setVisibility(distance > 0 ? View.VISIBLE : View.INVISIBLE);
        final float intensity = item.getIntensity();
        if (intensity >= 1f) {
            ActivityListingViews.fillTile(view.findViewById(R.id.activity_detail_tile_intensity), R.drawable.ic_intensity, String.valueOf(Math.round(intensity)), R.string.movement_intensity);
        }
        view.findViewById(R.id.activity_detail_tiles_extra).setVisibility(intensity >= 1f ? View.VISIBLE : View.GONE);
    }
}
