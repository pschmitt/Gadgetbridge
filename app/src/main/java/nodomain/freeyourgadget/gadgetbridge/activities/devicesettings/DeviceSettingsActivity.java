/*  Copyright (C) 2019-2024 Andreas Shimokawa, José Rebelo, Petr Vaněk

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
package nodomain.freeyourgadget.gadgetbridge.activities.devicesettings;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceFragmentCompat;

import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import nodomain.freeyourgadget.gadgetbridge.activities.AbstractSettingsActivityV2;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec;
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

public class DeviceSettingsActivity extends AbstractSettingsActivityV2 {
    private static final Logger LOG = LoggerFactory.getLogger(DeviceSettingsActivity.class);

    public static final String MENU_ENTRY_POINT = "MENU_ENTRY_POINT";

    public enum MENU_ENTRY_POINTS {
        DEVICE_SETTINGS,
        AUTH_SETTINGS,
        APPLICATION_SETTINGS
    }

    /**
     * Opens the device-specific settings of a device.
     */
    public static void start(@NonNull final Context context, @NonNull final GBDevice device) {
        context.startActivity(newIntent(context, device));
    }

    /**
     * Opens the device-specific settings of a device, on the screen that contains {@code prefKey},
     * and highlights that preference.
     */
    public static void start(@NonNull final Context context, @NonNull final GBDevice device, @NonNull final String prefKey) {
        final Intent intent = newIntent(context, device);
        final DeviceSettingsSpec spec = device.getDeviceCoordinator().getDeviceSettings(device);
        if (spec != null) {
            intent.putExtra(EXTRA_PREF_SCREEN, spec.findScreenKeyForPreference(prefKey));
        }
        intent.putExtra(EXTRA_PREF_HIGHLIGHT, prefKey);
        context.startActivity(intent);
    }

    private static Intent newIntent(@NonNull final Context context, @NonNull final GBDevice device) {
        final Intent intent = new Intent(context, DeviceSettingsActivity.class);
        intent.putExtra(GBDevice.EXTRA_DEVICE, device);
        intent.putExtra(MENU_ENTRY_POINT, MENU_ENTRY_POINTS.DEVICE_SETTINGS);
        return intent;
    }

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        if (getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE) == null) {
            LOG.error("Started without a device");
            finish();
        }
        super.onCreate(savedInstanceState);
    }

    @Override
    protected PreferenceFragmentCompat newFragment() {
        final GBDevice device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        final MENU_ENTRY_POINTS menu_entry = (MENU_ENTRY_POINTS) getIntent().getSerializableExtra(MENU_ENTRY_POINT);

        return DeviceSpecificSettingsFragment.newInstance(device, menu_entry);
    }

    @Override
    public void onSearchResultClicked(final SearchPreferenceResult result) {
        final GBDevice device = getIntent().getParcelableExtra(GBDevice.EXTRA_DEVICE);
        final MENU_ENTRY_POINTS menu_entry = (MENU_ENTRY_POINTS) getIntent().getSerializableExtra(MENU_ENTRY_POINT);
        final DeviceCoordinator coordinator = device.getDeviceCoordinator();
        final DeviceSettingsSpec modelSpec = coordinator.getDeviceSettings(device);

        if (modelSpec != null && modelSpec.containsKey(result.getKey())) {
            // A preference indexed programmatically by DeviceSettingsIndexer - resolve the screen
            // that holds it directly from the model, since it has no XML-derived result.getScreen().
            navigateToSearchResult(result, modelSpec.findScreenKeyForPreference(result.getKey()));
            return;
        }

        if (result.getScreen() != null) {
            // A nested PreferenceScreen inside an indexed XML file - the library already resolved it.
            navigateToSearchResult(result, result.getScreen());
            return;
        }

        final DeviceSpecificSettings deviceSpecificSettings = DeviceSpecificSettingsFragment.buildDeviceSpecificSettings(device, menu_entry);
        final String rootScreenForSubScreen = deviceSpecificSettings.getRootScreenForSubScreen(result.getResourceFile());
        if (rootScreenForSubScreen != null) {
            navigateToSearchResult(result, rootScreenForSubScreen);
            return;
        }

        super.onSearchResultClicked(result);
    }
}
