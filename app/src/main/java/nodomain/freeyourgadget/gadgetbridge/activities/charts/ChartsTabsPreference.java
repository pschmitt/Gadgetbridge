package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.PreferenceDataStore;

import com.mobeta.android.dslv.DragSortListPreference;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceManager;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * Setup of the preference that selects and sorts the chart tabs of a device.
 */
public final class ChartsTabsPreference {
    private ChartsTabsPreference() {
    }

    /**
     * Creates the preference, stored in the device-specific preferences of the device.
     */
    public static DragSortListPreference create(final Context context, final GBDevice device) {
        final DragSortListPreference pref = new DragSortListPreference(context, null);
        pref.setKey(DeviceSettingsPreferenceConst.PREFS_DEVICE_CHARTS_TABS);
        pref.setPreferenceDataStore(new DeviceDataStore(GBApplication.getDeviceSpecificSharedPrefs(device.getAddress())));
        pref.setIcon(R.drawable.ic_tabs);
        pref.setTitle(R.string.prefs_charts_tabs);
        pref.setSummary(R.string.prefs_charts_tabs_summary);
        pref.setDialogTitle(R.string.mi2_prefs_display_items);
        return pref;
    }

    /**
     * Sets the entries to the charts that the device supports. Call it after the preference
     * created by {@link #create}is added to the hierarchy.
     */
    public static void setup(final DragSortListPreference pref, final GBDevice device) {
        final Context context = pref.getContext();
        final DeviceChartsProvider provider = device.getDeviceCoordinator().getChartsProvider();
        final List<String> supportedCharts = provider.getSupportedCharts(device);

        final CharSequence[] entries = new CharSequence[supportedCharts.size()];
        for (int i = 0; i < supportedCharts.size(); i++) {
            entries[i] = provider.getChartLabel(context, device, supportedCharts.get(i));
        }
        pref.setEntries(entries);
        pref.setEntryValues(supportedCharts.toArray(new CharSequence[0]));

        if (StringUtils.isBlank(pref.getValue())) {
            pref.setValue(String.join(",", supportedCharts));
        }

        pref.setOnPreferenceChangeListener((preference, newValue) -> {
            LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(DeviceManager.ACTION_REFRESH_DEVICELIST));
            return true;
        });
    }

    private static class DeviceDataStore extends PreferenceDataStore {
        private final SharedPreferences sharedPreferences;

        private DeviceDataStore(final SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public void putString(final String key, @Nullable final String value) {
            sharedPreferences.edit().putString(key, value).apply();
        }

        @Nullable
        @Override
        public String getString(final String key, @Nullable final String defValue) {
            return sharedPreferences.getString(key, defValue);
        }
    }
}
