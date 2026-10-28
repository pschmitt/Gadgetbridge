/*  Copyright (C) 2021-2024 Arjan Schrijver, Daniel Dakhno, Petr Vaněk

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
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.MaterialDatePicker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileGridUtilKt;
import nodomain.freeyourgadget.gadgetbridge.database.DBAccess;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.AbstractActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySession;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils;
import nodomain.freeyourgadget.gadgetbridge.util.dialogs.MaterialDialogFragment;

public class ActivityListingDashboard extends MaterialDialogFragment {
    protected static final Logger LOG = LoggerFactory.getLogger(ActivityListingDashboard.class);
    GBDevice gbDevice;
    ActivityListingAdapter stepListAdapter;
    ActivitySession stepSessionsSummary;
    private int timeFrom;
    private int timeTo;

    public ActivityListingDashboard() {

    }

    public static ActivityListingDashboard newInstance(int timestamp, GBDevice device) {

        ActivityListingDashboard frag = new ActivityListingDashboard();

        Bundle args = new Bundle();
        args.putInt("time", timestamp);
        args.putParcelable(GBDevice.EXTRA_DEVICE, device);
        frag.setArguments(args);
        return frag;

    }

    @Override
    protected int getThemeOverlay() {
        return R.style.ThemeOverlay_App_MaterialAlertDialog_WindowBackground;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,

                             Bundle savedInstanceState) {

        return inflater.inflate(R.layout.activity_list_total_dashboard, container);
    }

    @Override

    public void onViewCreated(@NonNull final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        int time = getArguments().getInt("time", 1);
        Calendar day = Calendar.getInstance();
        day.setTimeInMillis(time * 1000L);
        day.set(Calendar.HOUR_OF_DAY, 23);
        day.set(Calendar.MINUTE, 59);
        day.set(Calendar.SECOND, 59);
        timeTo = (int) (day.getTimeInMillis() / 1000);


        gbDevice = getArguments().getParcelable(GBDevice.EXTRA_DEVICE);
        if (gbDevice == null) {
            throw new IllegalArgumentException("Must provide a device when invoking this activity");
        }
        stepListAdapter = new ActivityListingAdapter(getContext());

        StatTileGridUtilKt.applyStatTileStyle(getView().findViewById(R.id.activity_list_summary_date_from_card), null);
        StatTileGridUtilKt.applyStatTileStyle(getView().findViewById(R.id.activity_list_summary_time_span_card), null);
        StatTileGridUtilKt.applyStatTileStyle(getView().findViewById(R.id.activity_list_summary_date_to_card), null);
        final TextView battery_status_date_from_text = getView().findViewById(R.id.battery_status_date_from_text);
        final TextView battery_status_date_to_text = getView().findViewById(R.id.battery_status_date_to_text);
        LinearLayout battery_status_date_to_layout = getView().findViewById(R.id.battery_status_date_to_layout);
        final SeekBar battery_status_time_span_seekbar = getView().findViewById(R.id.battery_status_time_span_seekbar);

        boolean activity_list_debug_extra_time_range_value = GBApplication.getPrefs().getPreferences().getBoolean("activity_list_debug_extra_time_range", false);

        if (!activity_list_debug_extra_time_range_value) {
            battery_status_time_span_seekbar.setMax(3);
        }
        addStopLabels(getView().findViewById(R.id.activity_list_summary_slider_layout), battery_status_time_span_seekbar);
        final TextView battery_status_time_span_text = getView().findViewById(R.id.battery_status_time_span_text);

        battery_status_time_span_seekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {

            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                String text;
                switch (i) {
                    case 0:
                        text = getString(R.string.calendar_day);
                        timeFrom = DateTimeUtils.shiftDays(timeTo, -1);
                        break;
                    case 1:
                        text = getString(R.string.calendar_week);
                        timeFrom = DateTimeUtils.shiftDays(timeTo, -7);
                        break;
                    case 2:
                        text = getString(R.string.calendar_two_weeks);
                        timeFrom = DateTimeUtils.shiftDays(timeTo, -14);
                        break;
                    case 3:
                        text = getString(R.string.calendar_month);
                        timeFrom = DateTimeUtils.shiftMonths(timeTo, -1);
                        break;
                    case 4:
                        text = getString(R.string.calendar_three_months);
                        timeFrom = DateTimeUtils.shiftMonths(timeTo, -3);
                        break;
                    case 5:
                        text = getString(R.string.calendar_six_months);
                        timeFrom = DateTimeUtils.shiftMonths(timeTo, -6);
                        break;
                    case 6:
                        text = getString(R.string.calendar_year);
                        timeFrom = DateTimeUtils.shiftMonths(timeTo, -12);
                        break;
                    default:
                        text = getString(R.string.calendar_two_weeks);
                        timeFrom = DateTimeUtils.shiftDays(timeTo, -14);

                }

                battery_status_time_span_text.setText(text);
                battery_status_date_from_text.setText(DateTimeUtils.formatDate(new Date(timeFrom * 1000L)));
                battery_status_date_to_text.setText(DateTimeUtils.formatDate(new Date(timeTo * 1000L)));
                createRefreshTask("Visualizing step sessions", getActivity()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });
        battery_status_date_to_layout.setOnClickListener(v -> {
            final MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setSelection(DateTimeUtils.toUtcDayStartMillis(timeTo * 1000L))
                    .build();
            datePicker.addOnPositiveButtonClickListener(selection -> {
                final LocalDate selectedDate = DateTimeUtils.utcMillisToLocalDate(selection);
                final Calendar selectedDay = Calendar.getInstance();
                selectedDay.set(selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth(), 23, 59, 59);
                timeTo = (int) (selectedDay.getTimeInMillis() / 1000);

                battery_status_date_to_text.setText(DateTimeUtils.formatDate(new Date(timeTo * 1000L)));
                battery_status_time_span_seekbar.setProgress(0);
                battery_status_time_span_seekbar.setProgress(1);
            });
            datePicker.show(getChildFragmentManager(), "ACTIVITY_LISTING_DATE_PICKER");
        });
        battery_status_time_span_seekbar.setProgress(2);
    }

    protected RefreshTask createRefreshTask(String task, Context context) {
        return new RefreshTask(task, context);
    }

    private ActivitySession get_data(GBDevice gbDevice, DBHandler db, int timeFrom, int timeTo) {

        List<ActivitySession> stepSessions;
        List<? extends ActivitySample> activitySamples = getAllSamples(db, gbDevice, timeFrom, timeTo);
        final List<BaseActivitySummary> workouts = getAllWorkouts(db, gbDevice, timeFrom, timeTo);
        StepAnalysis stepAnalysis = new StepAnalysis();

        boolean isEmptySummary = false;
        if (activitySamples != null) {
            stepSessions = stepAnalysis.calculateStepSessions(activitySamples, workouts);
            if (stepSessions.isEmpty()) {
                isEmptySummary = true;
            }
            stepSessionsSummary = stepAnalysis.calculateSummary(stepSessions, isEmptySummary);
        }
        return stepSessionsSummary;
    }

    SampleProvider<? extends AbstractActivitySample> getProvider(DBHandler db, GBDevice device) {
        DeviceCoordinator coordinator = device.getDeviceCoordinator();
        return coordinator.getSampleProvider(device, db.getDaoSession());
    }

    protected List<? extends ActivitySample> getAllSamples(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        SampleProvider<? extends ActivitySample> provider = getProvider(db, device);
        return provider.getAllActivitySamples(tsFrom, tsTo);
    }

    protected List<BaseActivitySummary> getAllWorkouts(DBHandler db, GBDevice device, int tsFrom, int tsTo) {
        BaseActivitySummaryDao summaryDao = db.getDaoSession().getBaseActivitySummaryDao();
        Device dbDevice = DBHelper.findDevice(device, db.getDaoSession());
        QueryBuilder<BaseActivitySummary> qb = summaryDao.queryBuilder();
        qb.where(BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.getId()));
        qb.where(BaseActivitySummaryDao.Properties.StartTime.gt(new Date(tsFrom * 1000L)));
        qb.where(BaseActivitySummaryDao.Properties.EndTime.lt(new Date(tsTo * 1000L)));
        qb.orderAsc(BaseActivitySummaryDao.Properties.StartTime);
        final List<BaseActivitySummary> summaries = qb.build().list();
        final ActivitySummaryParser summaryParser = device.getDeviceCoordinator().getActivitySummaryParser(device, getContext());
        for (BaseActivitySummary summary : summaries) {
            summaryParser.parseBinaryData(summary, false);
        }
        return summaries;
    }

    private void addStopLabels(final ConstraintLayout layout, final SeekBar seekBar) {
        final int max = seekBar.getMax();
        final int[] labels = {
            R.string.duration_short_day,
            R.string.duration_short_week,
            R.string.duration_short_two_weeks,
            R.string.duration_short_month,
            R.string.duration_short_three_months,
            R.string.duration_short_six_months,
            R.string.duration_short_year,
        };
        final float density = getResources().getDisplayMetrics().density;
        for (int i = 0; i <= max; i++) {
            final TextView label = new TextView(requireContext());
            label.setText(labels[i]);
            label.setGravity(Gravity.CENTER_HORIZONTAL);
            label.setMaxLines(2);
            label.setTextSize(11);
            label.setTextColor(MaterialColors.getColor(layout, R.attr.textColorSecondary));
            label.setTranslationY(-14 * density);
            // A label as wide as the slider's two side paddings, with a bias of stop / max, centres on its tick
            final ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(seekBar.getPaddingStart() + seekBar.getPaddingEnd(), ViewGroup.LayoutParams.WRAP_CONTENT);
            params.startToStart = ConstraintLayout.LayoutParams.PARENT_ID;
            params.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
            params.topToBottom = seekBar.getId();
            params.horizontalBias = (float) i / max;
            layout.addView(label, params);
        }
    }

    void indicate_progress(boolean inProgress) {
        View view = getView();
        if (view == null) {
            return;
        }
        final View results = view.findViewById(R.id.activity_list_dashboard_results_layout);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            final float radius = 8 * getResources().getDisplayMetrics().density;
            results.setRenderEffect(inProgress ? RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.DECAL) : null);
        } else {
            results.setAlpha(inProgress ? 0.35f : 1f);
        }
        view.findViewById(R.id.activity_list_dashboard_loading_layout).setVisibility(inProgress ? View.VISIBLE : View.GONE);
    }

    void populateData(ActivitySession item) {
        View view = getView();
        if (view == null) {
            return;
        }

        ActivityListingViews.fillTile(view.findViewById(R.id.activity_list_summary_active_steps), R.drawable.ic_footprint, String.valueOf(item.getActiveSteps()), R.string.activity_list_summary_active_steps);
        ActivityListingViews.fillTile(view.findViewById(R.id.activity_list_summary_total_steps), R.drawable.ic_steps, String.valueOf(item.getTotalDaySteps()), R.string.live_activity_total_steps);
        final View distanceTile = view.findViewById(R.id.activity_list_summary_distance);
        ActivityListingViews.fillTile(distanceTile, R.drawable.ic_distance, FormatUtils.getFormattedDistanceLabel(item.getDistance()), R.string.distance);
        distanceTile.setVisibility(item.getDistance() > 0 ? View.VISIBLE : View.GONE);
        ActivityListingViews.fillTile(view.findViewById(R.id.activity_list_summary_active_time), R.drawable.ic_access_time, ActivityListingViews.formatDuration(item), R.string.activity_list_summary_active_time);
        ActivityListingViews.fillTile(view.findViewById(R.id.activity_list_summary_activities), R.drawable.ic_list_numbered, String.valueOf(item.getSessionCount()), R.string.activity_list_summary_activities);
    }

    public class RefreshTask extends DBAccess {

        public RefreshTask(String task, Context context) {
            super(task, context, false);
        }

        @Override
        protected void doInBackground(DBHandler db) {
            stepSessionsSummary = get_data(gbDevice, db, timeFrom, timeTo);
        }

        @Override
        protected void onPreExecute() {
            indicate_progress(true);
        }

        @Override
        protected void onPostExecute(Object o) {
            super.onPostExecute(o);
            FragmentActivity activity = getActivity();
            if (activity != null && !activity.isFinishing() && !activity.isDestroyed()) {
                populateData(stepSessionsSummary);
                indicate_progress(false);
            } else {
                LOG.info("Not filling data because activity is not available anymore");
            }
        }
    }
}