/*  Copyright (C) 2022-2024 Arjan Schrijver, Daniel Dakhno, José Rebelo

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

import android.app.Activity;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.TimeZonePickerAdapter;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.databinding.ActivityWorldClockDetailsBinding;
import nodomain.freeyourgadget.gadgetbridge.databinding.DialogTimezonePickerBinding;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.WorldClock;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public class WorldClockDetails extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(WorldClockDetails.class);

    public static final String EXTRA_NEW = "world_clock_new";

    private ActivityWorldClockDetailsBinding binding;

    private WorldClock worldClock;
    private GBDevice device;
    private boolean isNew;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityWorldClockDetailsBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (savedInstanceState != null) {
            worldClock = (WorldClock) savedInstanceState.getSerializable(WorldClock.EXTRA_WORLD_CLOCK);
        } else {
            worldClock = (WorldClock) getIntent().getSerializableExtra(WorldClock.EXTRA_WORLD_CLOCK);
        }
        if (worldClock == null) {
            GB.toast("No worldClock provided to WorldClockDetails Activity", Toast.LENGTH_LONG, GB.ERROR);
            finish();
            return;
        }

        device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        if (device == null) {
            GB.toast("No device provided to WorldClockDetails Activity", Toast.LENGTH_LONG, GB.ERROR);
            finish();
            return;
        }

        isNew = getIntent().getBooleanExtra(EXTRA_NEW, false);

        final DeviceCoordinator coordinator = device.getDeviceCoordinator();

        binding.cardTimezone.setOnClickListener(v -> showTimeZonePicker());

        binding.cardOptions.setVisibility(coordinator.supportsDisabledWorldClocks(device) ? View.VISIBLE : View.GONE);

        final int maxLabelLength = coordinator.getWorldClocksLabelLength();
        binding.worldClockLabel.setFilters(new InputFilter[]{new InputFilter.LengthFilter(maxLabelLength)});
        binding.worldClockLabelLayout.setCounterMaxLength(maxLabelLength);

        binding.worldClockEnabled.setChecked(worldClock.getEnabled() == null || worldClock.getEnabled());
        binding.worldClockLabel.setText(worldClock.getLabel());
        binding.worldClockCode.setText(worldClock.getCode());
        updateTimeZoneText();

        binding.fabSave.setVisibility(isNew ? View.VISIBLE : View.GONE);
        binding.fabSave.setOnClickListener(v -> {
            storeWorldClock();
            setResult(Activity.RESULT_OK);
            finish();
        });

        // Added after the initial values are set, so that only the changes of the user call it
        final TextWatcher changeWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                onWorldClockChanged();
            }
        };
        binding.worldClockLabel.addTextChangedListener(changeWatcher);
        binding.worldClockCode.addTextChangedListener(changeWatcher);
        binding.worldClockEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> onWorldClockChanged());
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == android.R.id.home) {
            // back button
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        if (worldClock != null && device != null && !isNew) {
            storeWorldClock();
        }
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(@NonNull final Bundle state) {
        super.onSaveInstanceState(state);
        updateWorldClockFromUi();
        state.putSerializable(WorldClock.EXTRA_WORLD_CLOCK, worldClock);
    }

    private void storeWorldClock() {
        updateWorldClockFromUi();
        LOG.debug("Storing world clock {}", worldClock.getWorldClockId());
        DBHelper.store(worldClock);
    }

    private void onWorldClockChanged() {
        if (!isNew) {
            setResult(Activity.RESULT_OK);
        }
    }

    private void updateWorldClockFromUi() {
        if (device.getDeviceCoordinator().supportsDisabledWorldClocks(device)) {
            worldClock.setEnabled(binding.worldClockEnabled.isChecked());
        }
        worldClock.setLabel(Objects.requireNonNull(binding.worldClockLabel.getText()).toString());
        worldClock.setCode(Objects.requireNonNull(binding.worldClockCode.getText()).toString());
    }

    private void updateTimeZoneText() {
        final TimeZone timeZone = TimeZone.getTimeZone(worldClock.getTimeZoneId());
        final Date now = new Date();
        final DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(this);
        timeFormat.setTimeZone(timeZone);

        binding.worldClockCurrentTime.setText(timeFormat.format(now));
        binding.worldClockTimezone.setText(worldClock.getTimeZoneId());
        binding.worldClockTimezoneOffset.setText(getString(
            R.string.activity_list_row_stats_pair,
            DateTimeUtils.formatUtcOffset(timeZone, now.getTime()),
            timeZone.getDisplayName(timeZone.inDaylightTime(now), TimeZone.LONG, Locale.getDefault())
        ));
    }

    private void setTimeZone(final String timeZoneId) {
        final String oldTimeZoneId = worldClock.getTimeZoneId();
        if (oldTimeZoneId.equals(timeZoneId)) {
            return;
        }

        updateWorldClockFromUi();
        worldClock.setTimeZoneId(timeZoneId);

        // Check if the label was still the default (the timezone city name)
        // If so, and if the user changed the timezone, update the label to match the new city name
        final int maxLabelLength = device.getDeviceCoordinator().getWorldClocksLabelLength();
        final String[] oldTimeZoneParts = oldTimeZoneId.split("/");
        final String[] newTimeZoneParts = timeZoneId.split("/");
        final String oldLabel = StringUtils.truncate(oldTimeZoneParts[oldTimeZoneParts.length - 1], maxLabelLength);
        final String newLabel = StringUtils.truncate(newTimeZoneParts[newTimeZoneParts.length - 1], maxLabelLength);
        if (StringUtils.isNullOrEmpty(worldClock.getLabel()) || worldClock.getLabel().equals(oldLabel)) {
            binding.worldClockLabel.setText(newLabel);
        }
        final String oldCode = StringUtils.truncate(oldLabel, 3).toUpperCase(Locale.getDefault());
        final String newCode = StringUtils.truncate(newLabel, 3).toUpperCase(Locale.getDefault());
        if (StringUtils.isNullOrEmpty(worldClock.getCode()) || worldClock.getCode().equals(oldCode)) {
            binding.worldClockCode.setText(newCode);
        }

        updateTimeZoneText();
        onWorldClockChanged();
    }

    private void showTimeZonePicker() {
        final DialogTimezonePickerBinding pickerBinding = DialogTimezonePickerBinding.inflate(getLayoutInflater());
        final AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.world_clock_timezone)
            .setView(pickerBinding.getRoot())
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        final TimeZonePickerAdapter adapter = new TimeZonePickerAdapter(worldClock.getTimeZoneId(), timeZoneId -> {
            setTimeZone(timeZoneId);
            dialog.dismiss();
        });
        pickerBinding.timezoneList.setLayoutManager(new LinearLayoutManager(this));
        pickerBinding.timezoneList.setAdapter(adapter);

        new Thread(() -> {
            final List<TimeZonePickerAdapter.Entry> entries = TimeZonePickerAdapter.buildEntries();
            runOnUiThread(() -> {
                adapter.setEntries(entries);
                final int selectedPosition = adapter.getSelectedPosition();
                if (selectedPosition != RecyclerView.NO_POSITION) {
                    pickerBinding.timezoneList.scrollToPosition(selectedPosition);
                }
            });
        }, "TimeZonePickerLoader").start();

        pickerBinding.timezoneSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                adapter.filter(s.toString());
                pickerBinding.timezoneList.scrollToPosition(0);
            }
        });

        dialog.show();
    }
}
