/*  Copyright (C) 2020-2024 Daniel Dakhno, Petr Vaněk

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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.appcompat.content.res.AppCompatResources;

import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.adapter.SpinnerWithIconAdapter;
import nodomain.freeyourgadget.gadgetbridge.adapter.SpinnerWithIconItem;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.databinding.SportActivityFilterBinding;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils;
import nodomain.freeyourgadget.gadgetbridge.util.WorkoutFilterUtils;


public class ActivitySummariesFilter extends AbstractGBActivity {
    private static final Logger LOG = LoggerFactory.getLogger(ActivitySummariesFilter.class);
    private static final String PREF_QUICK_FILTER = "workout_list_quick_filter";
    private static final String QUICK_FILTER_NONE = "noselection";
    public static final long ALL_DEVICES = 999;
    private int activityFilter = 0;
    private long dateFromFilter = 0;
    private long dateToFilter = 0;
    private String nameContainsFilter;
    private HashMap<String, ActivityKind> activityKindMap = new HashMap<>(1);
    private List<Long> itemsFilter;
    private long deviceFilter;
    private long initial_deviceFilter;
    private String quickFilter;
    private List<String> quickFilterValues;
    private List<String> quickFilterItems;
    private SportActivityFilterBinding binding;
    private Context deviceIconContext;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle bundle = this.getIntent().getExtras();

        activityKindMap = (HashMap<String, ActivityKind>) bundle.getSerializable("activityKindMap");
        itemsFilter = (List<Long>) bundle.getSerializable("itemsFilter");
        activityFilter = bundle.getInt("activityFilter", 0);
        dateFromFilter = bundle.getLong("dateFromFilter", 0);
        dateToFilter = bundle.getLong("dateToFilter", 0);
        initial_deviceFilter = bundle.getLong("initial_deviceFilter", 0);
        deviceFilter = bundle.getLong("deviceFilter", 0);
        nameContainsFilter = bundle.getString("nameContainsFilter");

        binding = SportActivityFilterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final ArrayList<SpinnerWithIconItem> deviceItems = getAllDevices();
        final SpinnerWithIconAdapter deviceAdapter = new SpinnerWithIconAdapter(this,
            R.layout.item_dropdown_device, R.id.spinner_item_text, deviceItems);
        binding.filterDevice.setAdapter(deviceAdapter);
        deviceIconContext = new ContextThemeWrapper(this, R.style.ThemeOverlay_App_DropdownDeviceIcon);
        binding.filterDeviceLayout.setStartIconTintList(null);
        binding.filterDevice.setOnItemClickListener((parent, view, position, id) -> {
            deviceFilter = deviceItems.get(position).getId();
            update_filter_fields();
        });

        final ArrayList<SpinnerWithIconItem> kindItems = new ArrayList<>();
        for (final Map.Entry<String, ActivityKind> item : activityKindMap.entrySet()) {
            if (item.getValue() == ActivityKind.UNKNOWN)
                continue; //do not put here All devices, but we do need them in the array
            kindItems.add(new SpinnerWithIconItem(item.getKey(), (long) item.getValue().getCode(), item.getValue().getIcon()));
        }
        kindItems.sort((o1, o2) -> o1.getText().compareToIgnoreCase(o2.getText()));
        kindItems.add(0, new SpinnerWithIconItem(getString(R.string.activity_summaries_all_activities), (long) ActivityKind.UNKNOWN.getCode(), ActivityKind.UNKNOWN.getIcon()));
        final SpinnerWithIconAdapter kindAdapter = new SpinnerWithIconAdapter(this,
            R.layout.item_dropdown_icon, R.id.spinner_item_text, kindItems);
        binding.filterKind.setAdapter(kindAdapter);
        binding.filterKind.setOnItemClickListener((parent, view, position, id) -> {
            activityFilter = kindItems.get(position).getId().intValue();
            update_filter_fields();
        });

        quickFilterItems = Arrays.asList(getResources().getStringArray(R.array.activity_filter_quick_filter_period_items));
        quickFilterValues = Arrays.asList(getResources().getStringArray(R.array.activity_filter_quick_filter_period_values));
        binding.filterPeriod.setAdapter(new ArrayAdapter<>(this, R.layout.item_dropdown_text, quickFilterItems));
        binding.filterPeriod.setOnItemClickListener((parent, view, position, id) -> {
            quickFilter = quickFilterValues.get(position);
            final Pair<Long, Long> dateRange = WorkoutFilterUtils.getDateRangeForFilter(quickFilter);
            if (dateRange != null) {
                dateFromFilter = dateRange.first;
                dateToFilter = dateRange.second;
            }
            update_filter_fields();
        });
        quickFilter = GBApplication.getPrefs().getPreferences().getString(PREF_QUICK_FILTER, QUICK_FILTER_NONE);
        if (!quickFilterValues.contains(quickFilter)) {
            quickFilter = QUICK_FILTER_NONE;
        }

        binding.filterFrom.setOnClickListener(v -> pickDate(true));
        binding.filterTo.setOnClickListener(v -> pickDate(false));

        binding.filterName.setText(nameContainsFilter);
        binding.filterName.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
            }

            @Override
            public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            }

            @Override
            public void afterTextChanged(final Editable s) {
                nameContainsFilter = s.toString();
                update_filter_fields();
            }
        });

        binding.resetFilterButton.setOnClickListener(v -> {
            activityFilter = 0;
            dateFromFilter = 0;
            dateToFilter = 0;
            itemsFilter = null;
            deviceFilter = initial_deviceFilter;
            quickFilter = QUICK_FILTER_NONE;
            nameContainsFilter = "";
            binding.filterName.setText("");
            update_filter_fields();
        });

        binding.applyFilterButton.setOnClickListener(v -> {
            GBApplication.getPrefs().getPreferences().edit()
                .putString(PREF_QUICK_FILTER, quickFilter)
                .apply();
            final Intent intent = new Intent();
            final Bundle result = new Bundle();
            result.putInt("activityFilter", activityFilter);
            result.putSerializable("itemsFilter", (Serializable) itemsFilter);
            result.putLong("dateFromFilter", dateFromFilter);
            result.putLong("dateToFilter", dateToFilter);
            result.putLong("deviceFilter", deviceFilter);
            result.putString("nameContainsFilter", nameContainsFilter);
            intent.putExtras(result);
            setResult(1, intent);
            finish();
        });

        update_filter_fields();
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

    private void update_filter_fields() {
        final SpinnerWithIconAdapter deviceAdapter = (SpinnerWithIconAdapter) binding.filterDevice.getAdapter();
        setDropdownSelection(binding.filterDeviceLayout, binding.filterDevice, deviceAdapter, deviceFilter, deviceIconContext);

        final SpinnerWithIconAdapter kindAdapter = (SpinnerWithIconAdapter) binding.filterKind.getAdapter();
        setDropdownSelection(binding.filterKindLayout, binding.filterKind, kindAdapter, activityFilter, this);

        binding.filterPeriod.setText(quickFilterItems.get(quickFilterValues.indexOf(quickFilter)), false);

        binding.filterFrom.setText(dateFromFilter > 0
            ? DateTimeUtils.formatDate(new Date(dateFromFilter))
            : getString(R.string.activity_filter_from_placeholder));
        binding.filterTo.setText(dateToFilter > 0
            ? DateTimeUtils.formatDate(new Date(dateToFilter))
            : getString(R.string.activity_filter_to_placeholder));

        final boolean invalidRange = dateToFilter > 0 && dateToFilter < dateFromFilter;
        binding.filterToLayout.setError(invalidRange ? getString(R.string.activity_filter_to_before_from) : null);
        binding.applyFilterButton.setEnabled(!invalidRange);

        final boolean hasItems = itemsFilter != null && !itemsFilter.isEmpty();
        binding.filterItems.setVisibility(hasItems ? View.VISIBLE : View.GONE);
        binding.filterItemsCount.setText(hasItems ? String.valueOf(itemsFilter.size()) : "");

        final boolean filterActive = dateToFilter != 0 || dateFromFilter != 0 || activityFilter != 0
            || (nameContainsFilter != null && !nameContainsFilter.isEmpty())
            || hasItems || deviceFilter != initial_deviceFilter;
        binding.resetFilterButton.setEnabled(filterActive);
    }

    private static void setDropdownSelection(final TextInputLayout layout,
                                             final AutoCompleteTextView dropdown,
                                             final SpinnerWithIconAdapter adapter,
                                             final long id,
                                             final Context iconContext) {
        for (int i = 0; i < adapter.getCount(); i++) {
            final SpinnerWithIconItem item = adapter.getItem(i);
            if (item != null && item.getId() == id) {
                dropdown.setText(item.getText(), false);
                layout.setStartIconDrawable(AppCompatResources.getDrawable(iconContext, item.getImageId()));
                return;
            }
        }
        dropdown.setText("", false);
        layout.setStartIconDrawable(null);
    }

    private void pickDate(final boolean from) {
        final long currentMillis = from ? dateFromFilter : dateToFilter;
        final long initialMillis = currentMillis > 0 ? currentMillis : System.currentTimeMillis();
        final MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(from ? R.string.activity_filter_date_from : R.string.activity_filter_date_to)
            .setSelection(DateTimeUtils.toUtcDayStartMillis(initialMillis))
            .build();
        datePicker.addOnPositiveButtonClickListener(selection -> {
            final LocalDate selectedDate = DateTimeUtils.utcMillisToLocalDate(selection);
            final Calendar date = Calendar.getInstance();

            if (from) {
                date.set(selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth(), 0, 0);
                dateFromFilter = date.getTimeInMillis();
            } else {
                date.set(selectedDate.getYear(), selectedDate.getMonthValue() - 1, selectedDate.getDayOfMonth(), 23, 59);
                dateToFilter = date.getTimeInMillis();
            }
            quickFilter = QUICK_FILTER_NONE;
            update_filter_fields();
        });
        datePicker.show(getSupportFragmentManager(), "WORKOUT_FILTER_DATE_PICKER");
    }

    private ArrayList<SpinnerWithIconItem> getAllDevices() {
        final LinkedHashMap<String, SpinnerWithIconItem> items = new LinkedHashMap<>();
        final String allDevices = getString(R.string.activity_summaries_all_devices);
        items.put(allDevices, new SpinnerWithIconItem(allDevices, ALL_DEVICES, R.drawable.ic_device_default));

        final List<? extends GBDevice> devices = GBApplication.app().getDeviceManager().getDevices();
        try (DBHandler handler = GBApplication.acquireDbReadOnly()) {
            final DaoSession daoSession = handler.getDaoSession();
            for (final GBDevice device : devices) {
                final DeviceCoordinator coordinator = device.getDeviceCoordinator();
                final Device dbDevice = DBHelper.findDevice(device, daoSession);
                if (dbDevice != null && coordinator.supportsRecordedActivities(device)
                    && !items.containsKey(device.getAliasOrName())) {
                    items.put(device.getAliasOrName(), new SpinnerWithIconItem(device.getAliasOrName(),
                        dbDevice.getId(), coordinator.getDefaultIconResource()));
                }
            }
        } catch (final Exception e) {
            LOG.error("Error getting list of all devices", e);
        }
        return new ArrayList<>(items.values());
    }
}
