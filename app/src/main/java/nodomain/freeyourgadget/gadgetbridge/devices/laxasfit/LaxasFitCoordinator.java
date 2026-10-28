/*  Copyright (C) 2021-2024 a b, Damien Gaignon, Daniel Dakhno, José Rebelo,
    Petr Vaněk

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

package nodomain.freeyourgadget.gadgetbridge.devices.laxasfit;


import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.EnumSet;
import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractBLEDeviceCoordinator;
import nodomain.freeyourgadget.gadgetbridge.devices.SampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.ServiceDeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.laxasfit.LaxasFitDeviceSupport;

public class LaxasFitCoordinator extends AbstractBLEDeviceCoordinator {
    @Override
    public String getManufacturer() {
        return "LaxasFit";
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_laxasfit;
    }

    @Override
    public DeviceKind getDeviceKind(@NonNull GBDevice device) {
        return DeviceKind.WATCH;
    }

    @Override
    @DrawableRes
    public int getDefaultIconResource() {
        return R.drawable.ic_device_amazfit_bip;
    }

    @NonNull
    @Override
    public Class<? extends DeviceSupport> getDeviceSupportClass(@NonNull final GBDevice device) {
        return LaxasFitDeviceSupport.class;
    }

    @Override
    public EnumSet<ServiceDeviceSupport.Flags> getInitialFlags() {
        return EnumSet.of(ServiceDeviceSupport.Flags.THROTTLING, ServiceDeviceSupport.Flags.BUSY_CHECKING);
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^(Q11.*)$");
    }

    @Override
    public int getBondingStyle() {
        return BONDING_STYLE_LAZY;
    }

    @Override
    public boolean supportsDataFetching(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsActivityTracking(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsRecordedActivities(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public SampleProvider<? extends ActivitySample> getSampleProvider(GBDevice device, DaoSession session) {
        return new LaxasFitSampleProvider(device, session);
    }

    @Override
    public int getAlarmSlotCount(GBDevice device) {
        return 8;
    }

    @Override
    public boolean supportsHeartRateMeasurement(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsBloodPressureMeasurement(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsSpo2(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsRealtimeData(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsWeather(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsAppNotifications(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsPhoneCalls(@NonNull final GBDevice device) {
        return true;
    }

    @Override
    public boolean supportsFindDevice(@NonNull GBDevice device) {
        return true;
    }
    @Override
    public boolean supportsRemSleep(@NonNull GBDevice device) {
        return true;
    }

    @Override
    public int[] getSupportedDeviceSpecificSettings(GBDevice device) {
        return new int[]{
                R.xml.devicesettings_liftwrist_display_no_on,
                R.xml.devicesettings_inactivity_extended,
                R.xml.devicesettings_donotdisturb_no_auto,
                R.xml.devicesettings_sleep_time,
                R.xml.devicesettings_wearlocation,
                R.xml.devicesettings_autoheartrate,
                R.xml.devicesettings_vibrations_enable,
                R.xml.devicesettings_notifications_enable,
                R.xml.devicesettings_fitpro,
                R.xml.devicesettings_transliteration
        };
    }
}
