/*  Copyright (C) 2024 José Rebelo
    Copyright (C) 2026 NTeditor, badcpp

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.modules;

import android.content.Context;

import androidx.annotation.NonNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.OppoUtils;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoCommand;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.OppoMessage;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.EarbudsStatusSide;
import nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands.EarbudsStatusValue;

public class EarbudsStatusModule extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(EarbudsStatusModule.class);

    public EarbudsStatusModule(@NonNull final Context context) {
        super(context);
    }

    @NonNull
    public OppoMessage encodeReq() {
        return new OppoMessage(OppoCommand.EARBUDS_STATUS_REQ, new byte[0]);
    }

    public Map<EarbudsStatusSide, EarbudsStatusValue> decodeRet(@NonNull final byte[] payload) {
        final ByteBuffer buf = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);
        final int zero = buf.get();

        final Map<EarbudsStatusSide, EarbudsStatusValue> map = new HashMap<>();
        final int sidesNum = buf.get() & 0xff;
        for (int i = 0; i < sidesNum; i++) {
            final int sideCode = buf.get() & 0xff;
            final int valueCode = buf.get() & 0xff;

            final EarbudsStatusSide side = EarbudsStatusSide.fromCode(sideCode);
            if (side == null) {
                LOG.warn("Unknown EarbudsStatusSide code 0x{}", OppoUtils.numberToHex(sideCode, 2));
                continue;
            }

            final EarbudsStatusValue value = EarbudsStatusValue.fromCode(valueCode);
            if (value == null) {
                LOG.warn("Unknown EarbudsStatusValue code 0x{}", OppoUtils.numberToHex(valueCode, 2));
                continue;
            }

            LOG.warn("Got earbuds status for {} = {}", side, value);
            map.put(side, value);
        }
        return map;
    }
}
