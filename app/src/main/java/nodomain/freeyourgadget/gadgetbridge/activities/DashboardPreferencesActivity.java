/*  Copyright (C) 2024-2026 Arjan Schrijver, José Rebelo

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.activities;

import android.os.Bundle;

import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.data.DashboardPreferences;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetRegistry;

public class DashboardPreferencesActivity extends AbstractSettingsActivityV2 {
    @Override
    protected PreferenceFragmentCompat newFragment() {
        return new DashboardPreferencesFragment();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    public static class DashboardPreferencesFragment extends AbstractPreferenceFragment {
        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            setPreferencesFromResource(R.xml.dashboard_preferences, rootKey);

            final MultiSelectListPreference dashboardDevices = findPreference("dashboard_devices_multiselect");
            if (dashboardDevices != null) {
                final Set<String> selected = dashboardDevices.getValues();
                final List<GBDevice> devices = new ArrayList<>(GBApplication.app().getDeviceManager().getDevices());
                devices.removeIf(dev -> !WidgetRegistry.supportsAnyWidget(dev));
                devices.sort(Comparator
                    .comparing((GBDevice dev) -> !selected.contains(dev.getAddress()))
                    .thenComparing(GBDevice::getAliasOrName, String.CASE_INSENSITIVE_ORDER));
                final List<String> deviceMACs = new ArrayList<>();
                final List<String> deviceNames = new ArrayList<>();
                for (final GBDevice dev : devices) {
                    deviceMACs.add(dev.getAddress());
                    deviceNames.add(dev.getAliasOrName());
                }
                dashboardDevices.setEntryValues(deviceMACs.toArray(new String[0]));
                dashboardDevices.setEntries(deviceNames.toArray(new String[0]));
            }

            final Preference manageWidgets = findPreference("pref_dashboard_manage_widgets");
            if (manageWidgets != null) {
                manageWidgets.setOnPreferenceClickListener(preference -> {
                    new DashboardPreferences().requestStartEditing();
                    requireActivity().finish();
                    return true;
                });
            }
        }
    }
}
