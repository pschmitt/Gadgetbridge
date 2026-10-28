/*  Copyright (C) 2026 Francesco Colasuonno

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
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches;

import java.util.regex.Pattern;

import nodomain.freeyourgadget.gadgetbridge.R;

/**
 * Xiaomi Smart Band 11.
 *
 * <p>Built on top of {@link MiBand10Coordinator}, which is the closest known relative.
 * The band advertises itself as {@code Xiaomi Smart Band 11 XXXX} — exactly the same
 * naming scheme as the band 10 — and belongs to the same Xiaomi protobuf device family,
 * so only the advertised-name pattern has to differ.
 *
 * <p>Observed advertisement: {@code Xiaomi Smart Band 11 3C8D}.
 */
public class MiBand11Coordinator extends MiBand10Coordinator {
    @Override
    public int getDeviceNameResource() {
        return R.string.devicetype_miband11;
    }

    @Override
    protected Pattern getSupportedDeviceName() {
        return Pattern.compile("^Xiaomi Smart Band 11 [0-9A-F]{4}$");
    }

    @Override
    public boolean isExperimental() {
        return true;
    }
}
