/*  Copyright (C) 2026 a0z

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

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.card.MaterialCardView;

import com.github.mikephil.charting.charts.Chart;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.TrainingStatus;

public class TrainingStatusDayFragment extends AbstractChartFragment<TrainingStatusDayFragment.TrainingStatusDayData> {
    private static final int WEEK_DAYS = 7;

    private TextView dateView;
    private View statusRow;
    private ImageView iconView;
    private TextView statusView;
    private TextView rangeView;
    private LinearLayout weekView;
    private LinearLayout statsContainer;

    @Override
    public String getTitle() {
        return getString(R.string.metric_garmin_training_status);
    }

    @Override
    protected void init() {
    }

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_training_status_day, container, false);
        rootView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) ->
                getChartsHost().enableSwipeRefresh(scrollY == 0));

        dateView = rootView.findViewById(R.id.training_status_day_date_view);
        statusRow = rootView.findViewById(R.id.training_status_day_status_row);
        iconView = rootView.findViewById(R.id.training_status_day_icon);
        statusView = rootView.findViewById(R.id.training_status_day_status);
        rangeView = rootView.findViewById(R.id.training_status_day_range);
        weekView = rootView.findViewById(R.id.training_status_day_week);
        final MaterialCardView header = rootView.findViewById(R.id.training_status_day_header);
        StatTileGridUtilKt.applyStatTileStyle(header, null);
        header.setStrokeWidth(dp(1.5f));
        header.setRadius(dp(12));
        statsContainer = rootView.findViewById(R.id.training_status_day_stats_container);

        refresh();

        return rootView;
    }

    @Override
    protected TrainingStatusDayData refreshInBackground(final ChartsHost chartsHost, final DBHandler db, final GBDevice device) {
        final long day = TrainingStatusPeriodFragment.toEpochDay(getTSEnd() * 1000L);
        return new TrainingStatusDayData(TrainingStatusReport.load(db, device, day, WEEK_DAYS));
    }

    @Override
    protected void updateChartsnUIThread(final TrainingStatusDayData data) {
        final TrainingStatusReport report = data.report;
        dateView.setText(new SimpleDateFormat("E, MMM dd", Locale.getDefault()).format(getEndDate()));
        final Integer selected = report.getStatus();
        final int textColor = selected != null
                ? TrainingStatus.color(requireContext(), selected)
                : GBApplication.getSecondaryTextColor(requireContext());
        final String label = selected != null ? TrainingStatus.label(requireContext(), selected) : getString(R.string.no_data);
        iconView.setImageResource(selected != null ? TrainingStatus.icon(selected) : R.drawable.ic_help_outline);
        iconView.setImageTintList(ColorStateList.valueOf(textColor));
        statusView.setText(label);
        statusView.setTextColor(textColor);
        final String range = report.rangeText(requireContext());
        if (range != null) {
            rangeView.setText(range);
            rangeView.setVisibility(View.VISIBLE);
            statusRow.setContentDescription(label + ", " + range);
        } else {
            rangeView.setVisibility(View.GONE);
            statusRow.setContentDescription(label);
        }
        updateWeek(report.getDays(), report.getDay());

        final List<StatTileData> stats = new ArrayList<>();
        stats.add(TrainingStatTiles.acuteLoad(requireContext(), getString(R.string.training_status_load), report.getAcuteLoad(), report.getChronicLoad()));
        stats.add(TrainingStatTiles.hrv(requireContext(), report.getHrvStatus(), report.getHrvWeeklyAverage()));
        stats.add(new StatTileData(
                report.getVo2Max() != null ? String.format(Locale.getDefault(), "%.1f", report.getVo2Max()) : getString(R.string.stats_empty_value),
                getString(R.string.menuitem_vo2_max)
        ));

        statsContainer.removeAllViews();
        StatTileGridUtilKt.addStatTileGrid(statsContainer, requireContext(), stats, 0);
    }

    private void updateWeek(final Integer[] week, final long lastDay) {
        weekView.removeAllViews();
        final Locale locale = Locale.getDefault();
        final List<String> description = new ArrayList<>();
        for (int i = 0; i < week.length; i++) {
            final boolean selected = i == week.length - 1;
            final DayOfWeek dayOfWeek = LocalDate.ofEpochDay(lastDay - week.length + 1 + i).getDayOfWeek();

            final LinearLayout column = new LinearLayout(requireContext());
            column.setOrientation(LinearLayout.VERTICAL);
            column.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            final LinearLayout.LayoutParams columnParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            columnParams.setMarginStart(i == 0 ? 0 : dp(6));
            column.setLayoutParams(columnParams);

            final View block = new View(requireContext());
            block.setBackground(dayBlock(week[i], selected));
            column.addView(block, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(selected ? 26 : 18)));

            final TextView letter = new TextView(requireContext());
            letter.setText(dayOfWeek.getDisplayName(TextStyle.NARROW, locale));
            letter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            letter.setTextColor(selected ? GBApplication.getTextColor(requireContext()) : GBApplication.getSecondaryTextColor(requireContext()));
            letter.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            final LinearLayout.LayoutParams letterParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            letterParams.topMargin = dp(5);
            column.addView(letter, letterParams);

            weekView.addView(column);
            description.add(dayOfWeek.getDisplayName(TextStyle.FULL, locale) + " " + (week[i] != null
                    ? TrainingStatus.label(requireContext(), week[i]).toLowerCase(locale)
                    : getString(R.string.no_data).toLowerCase(locale)));
        }
        weekView.setContentDescription(getString(R.string.training_status_last_7_days, TextUtils.join(", ", description)));
    }

    /**
     * A filled block for a day with a status, faded unless [selected], or a dashed outline for a day without one.
     */
    private GradientDrawable dayBlock(@Nullable final Integer status, final boolean selected) {
        final GradientDrawable drawable = new GradientDrawable();
        drawable.setCornerRadius(dp(6));
        if (status == null) {
            drawable.setStroke(dp(1), ColorUtils.setAlphaComponent(GBApplication.getSecondaryTextColor(requireContext()), 0x60), dp(4), dp(3));
        } else {
            final int color = TrainingStatus.color(requireContext(), status);
            drawable.setColor(selected ? color : ColorUtils.setAlphaComponent(color, Math.round(0.45f * 255)));
        }
        return drawable;
    }

    private int dp(final float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    @Override
    protected void renderCharts() {
    }

    @Override
    protected void setupLegend(final Chart<?> chart) {
    }

    protected static class TrainingStatusDayData extends ChartsData {
        final TrainingStatusReport report;

        TrainingStatusDayData(final TrainingStatusReport report) {
            this.report = report;
        }
    }
}
