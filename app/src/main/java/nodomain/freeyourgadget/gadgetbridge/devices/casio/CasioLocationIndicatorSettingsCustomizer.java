/*  Copyright (C) 2026 Davide Gessa

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

package nodomain.freeyourgadget.gadgetbridge.devices.casio;

import android.content.Intent;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.preference.Preference;

import java.util.Collections;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.activities.casio.CasioLocationIndicatorActivity;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsCustomizer;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSpecificSettingsHandler;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.util.Prefs;

public class CasioLocationIndicatorSettingsCustomizer implements DeviceSpecificSettingsCustomizer {
    private final GBDevice device;

    public CasioLocationIndicatorSettingsCustomizer(final GBDevice device) {
        this.device = device;
    }

    @Override
    public void onPreferenceChange(final Preference preference, final DeviceSpecificSettingsHandler handler) {
    }

    @Override
    public void customizeSettings(final DeviceSpecificSettingsHandler handler, final Prefs prefs, final String rootKey) {
        final Preference pref = handler.findPreference("pref_casio_location_indicator");
        if (pref != null) {
            pref.setOnPreferenceClickListener(preference -> {
                final Intent intent = new Intent(handler.getContext(), CasioLocationIndicatorActivity.class);
                intent.putExtra(GBDevice.EXTRA_DEVICE, device);
                handler.getContext().startActivity(intent);
                return true;
            });
        }
    }

    @Override
    public Set<String> getPreferenceKeysWithSummary() {
        return Collections.emptySet();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(final Parcel dest, final int flags) {
        dest.writeParcelable(device, flags);
    }

    public static final Creator<CasioLocationIndicatorSettingsCustomizer> CREATOR = new Creator<>() {
        @Override
        public CasioLocationIndicatorSettingsCustomizer createFromParcel(final Parcel in) {
            final GBDevice device = in.readParcelable(CasioLocationIndicatorSettingsCustomizer.class.getClassLoader());
            return new CasioLocationIndicatorSettingsCustomizer(device);
        }

        @Override
        public CasioLocationIndicatorSettingsCustomizer[] newArray(final int size) {
            return new CasioLocationIndicatorSettingsCustomizer[size];
        }
    };
}
