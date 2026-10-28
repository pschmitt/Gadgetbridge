/*  Copyright (C) 2026 Abhishek

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
package nodomain.freeyourgadget.gadgetbridge.devices.moyoung;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;

/**
 * Fire-Boltt Ninja Phantom (model BGS001, firmware MOY-9K54). Advertises as "FB BGS001".
 * Same MoYoung platform as the Fire-Boltt 069; it also streams live heart rate on 0x2A37.
 */
public class FireBolttNinjaPhantomCoordinator extends AbstractMoyoungDeviceCoordinator {
    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^FB BGS001$");
    }

    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_fireboltt_ninja_phantom;
    }

    @Override
    @DrawableRes
    public int getDefaultIconResource() {
        return R.drawable.ic_device_miwatch;
    }

    @Override
    public String getManufacturer() {
        return "Fire-Boltt";
    }

    @Override
    public int getMtu() {
        return 508;
    }

    @Override
    public int getAlarmSlotCount(GBDevice device) {
        return 8;
    }

    @Override
    public boolean supportsHeartRateStreaming() {
        return true;
    }

    @Override
    public boolean supportsRemSleep(@NonNull GBDevice device) {
        return true;
    }
}
