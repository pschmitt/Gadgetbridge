/*  Copyright (C) 2021-2024 Daniel Dakhno, José Rebelo, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.appcompat.content.res.AppCompatResources;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.datepicker.MaterialDatePicker;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.DeviceStatusDot;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.BatteryConfig;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;

public class BatteryInfoActivity extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(BatteryInfoActivity.class);
    GBDevice gbDevice;
    private int timeFrom;
    private int timeTo;
    private int batteryIndex = 0;
    TextView battery_status_battery_level_text;
    TextView battery_status_battery_voltage;
    TextView battery_status_connection_state;
    TextView battery_status_extra_name;
    View battery_status_details;
    View battery_status_connection_dot;
    LocalBroadcastManager localBroadcastManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        final Context appContext = this.getApplicationContext();
        if (appContext instanceof GBApplication) {
            setContentView(R.layout.activity_battery_info);
        }

        Intent intent = getIntent();
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            gbDevice = bundle.getParcelable(GBDevice.EXTRA_DEVICE);
            batteryIndex = bundle.getInt(GBDevice.BATTERY_INDEX, 0);
        } else {
            throw new IllegalArgumentException("Must provide a device when invoking this activity");
        }

        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(GBDevice.ACTION_DEVICE_CHANGED);
        localBroadcastManager.registerReceiver(commandReceiver, filter);

        final BatteryInfoChartFragment batteryInfoChartFragment = new BatteryInfoChartFragment();

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.batteryChartFragmentHolder, batteryInfoChartFragment)
                .commit();

        timeTo = (int) (System.currentTimeMillis() / 1000);

        TextView battery_status_device_name_text = findViewById(R.id.battery_status_device_name);
        battery_status_battery_voltage = findViewById(R.id.battery_status_battery_voltage);
        battery_status_extra_name = findViewById(R.id.battery_status_extra_name);
        battery_status_details = findViewById(R.id.battery_status_details);
        final TextView battery_status_date_from_text = findViewById(R.id.battery_status_date_from_text);
        final TextView battery_status_date_to_text = findViewById(R.id.battery_status_date_to_text);
        final SeekBar battery_status_time_span_seekbar = findViewById(R.id.battery_status_time_span_seekbar);
        final TextView battery_status_time_span_text = findViewById(R.id.battery_status_time_span_text);

        LinearLayout battery_status_date_to_layout = findViewById(R.id.battery_status_date_to_layout);

        battery_status_time_span_seekbar.setMax(5);
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
                        text = getString(R.string.calendar_six_months);
                        timeFrom = DateTimeUtils.shiftMonths(timeTo, -6);
                        break;
                    case 5:
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
                batteryInfoChartFragment.setDateAndGetData(gbDevice, batteryIndex, timeFrom, timeTo);
            }


            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        //Button battery_status_calendar_button = findViewById(R.id.battery_status_calendar_button);
        battery_status_date_to_layout.setOnClickListener(v -> {
            final MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setSelection(DateTimeUtils.toUtcDayStartMillis(timeTo * 1000L))
                    .build();
            datePicker.addOnPositiveButtonClickListener(selection -> {
                final LocalDate selectedDate = DateTimeUtils.utcMillisToLocalDate(selection);
                final Calendar date = Calendar.getInstance();
                date.set(selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth());
                timeTo = (int) (date.getTimeInMillis() / 1000);
                battery_status_date_to_text.setText(DateTimeUtils.formatDate(new Date(timeTo * 1000L)));
                battery_status_time_span_seekbar.setProgress(0);
                battery_status_time_span_seekbar.setProgress(1);

                batteryInfoChartFragment.setDateAndGetData(gbDevice, batteryIndex, timeFrom, timeTo);
            });
            datePicker.show(getSupportFragmentManager(), "BATTERY_DATE_PICKER");
        });


        battery_status_time_span_seekbar.setProgress(2);

        DeviceCoordinator coordinator = gbDevice.getDeviceCoordinator();

        ImageView battery_status_device_icon = findViewById(R.id.battery_status_device_icon);
        if (gbDevice.isInitialized()) {
            final Context iconContext = new ContextThemeWrapper(battery_status_device_icon.getContext(), R.style.ThemeOverlay_App_DeviceCardIcon_Connected);
            battery_status_device_icon.setImageDrawable(AppCompatResources.getDrawable(iconContext, coordinator.getDefaultIconResource()));
            battery_status_device_icon.setBackground(AppCompatResources.getDrawable(iconContext, R.drawable.device_card_icon_bg));
            battery_status_device_icon.setColorFilter(null);
        } else {
            battery_status_device_icon.setImageResource(coordinator.getDefaultIconResource());
            final ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);

            battery_status_device_icon.setColorFilter(new ColorMatrixColorFilter(colorMatrix));
        }
        battery_status_battery_level_text = findViewById(R.id.battery_status_battery_level);
        battery_status_device_name_text.setText(gbDevice.getAliasOrName());
        battery_status_connection_state = findViewById(R.id.battery_status_connection_state);
        battery_status_connection_dot = findViewById(R.id.battery_status_connection_dot);

        for (BatteryConfig batteryConfig : coordinator.getBatteryConfig(gbDevice)) {
            if (batteryConfig.getBatteryIndex() == batteryIndex) {
                if (batteryConfig.getBatteryLabel() != GBDevice.BATTERY_LABEL_DEFAULT) {
                    battery_status_extra_name.setText(batteryConfig.getBatteryLabel());
                }
                if (batteryConfig.getBatteryIcon() != GBDevice.BATTERY_ICON_DEFAULT) {
                    battery_status_device_icon.setImageResource(batteryConfig.getBatteryIcon());
                    if (gbDevice.isInitialized()) {
                        battery_status_device_icon.setColorFilter(MaterialColors.getColor(battery_status_device_icon, R.attr.accent_color));
                    }
                }
            }
        }

        setBatteryLabels();
        setConnectionState();
    }

    private void setBatteryLabels() {
        String level = gbDevice.getBatteryLevel(batteryIndex) > 0 ? String.format("%1s%%", gbDevice.getBatteryLevel(batteryIndex)) : "";
        String voltage = gbDevice.getBatteryVoltage(batteryIndex) > 0 ? String.format("%1sV", gbDevice.getBatteryVoltage(batteryIndex)) : "";
        battery_status_battery_level_text.setText(level);
        battery_status_battery_voltage.setText(voltage);
        battery_status_details.setVisibility(voltage.isEmpty() && battery_status_extra_name.getText().length() == 0 ? View.GONE : View.VISIBLE);
    }

    private void setConnectionState() {
        battery_status_connection_state.setText(gbDevice.getStateString(this));
        DeviceStatusDot.apply(battery_status_connection_dot, gbDevice);
    }

    BroadcastReceiver commandReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            LOG.debug("device receiver received {}", intent.getAction());
            if (GBDevice.ACTION_DEVICE_CHANGED.equals(intent.getAction())) {
                GBDevice newDevice = intent.getParcelableExtra(GBDevice.EXTRA_DEVICE);
                if (gbDevice.equals(newDevice)) {
                    gbDevice = newDevice;
                    setBatteryLabels();
                    setConnectionState();
                }

            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(commandReceiver);
    }

}


