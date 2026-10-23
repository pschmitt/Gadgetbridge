/*  Copyright (C) 2026 David Giron

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.coros;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class CorosProtocol {
    static final byte[] QUERY_CAPABILITIES = {(byte) 0xb0, 0x00, 0x00, 0x01, 0x02, 0x03};
    static final byte[] FIND_PHONE = {0x04, 0x00, 0x00, 0x00};
    static final byte[] FIND_DEVICE_ACK = {(byte) 0xb4, 0x00, 0x01, 0x01};
    static final byte[] FIND_DEVICE_DISMISSED = {0x04, 0x00, 0x01, 0x01};

    private static final int COMMAND_NOTIFICATION = 0x79;
    private static final int COMMAND_DND = 0x86;
    private static final int COMMAND_FACTORY_RESET = 0x85;
    private static final int COMMAND_FIND_DEVICE = 0xb4;

    private CorosProtocol() {}

    static byte[] findDevice() {
        return new byte[]{(byte) COMMAND_FIND_DEVICE, 0};
    }

    static byte[] factoryReset() {
        return new byte[]{(byte) COMMAND_FACTORY_RESET, 0};
    }

    static byte[] notification(final String app, final String title, final String body, final int maxLength) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(COMMAND_NOTIFICATION);
        out.write(0);
        out.write(0xff);
        final String[] fields = {app, title, body};
        for (int i = 0; i < fields.length; i++) {
            final String field = fields[i] == null ? "" : fields[i];
            final byte[] bytes = field.getBytes(StandardCharsets.UTF_8);
            if (field.indexOf('\0') >= 0 || bytes.length > 255) {
                return null;
            }
            out.write(i << 4);
            out.write(bytes.length);
            out.write(bytes, 0, bytes.length);
        }
        out.write(0);
        // Fragmentation is not known. Never split a logical message into ATT writes.
        return out.size() <= maxLength ? out.toByteArray() : null;
    }

    static byte[] doNotDisturb(final boolean enabled, final int startHour, final int startMinute,
                               final int endHour, final int endMinute) {
        if (startHour < 0 || startHour > 23 || endHour < 0 || endHour > 23 ||
                startMinute < 0 || startMinute > 59 || endMinute < 0 || endMinute > 59) {
            throw new IllegalArgumentException("Invalid DND schedule");
        }
        final int mode = enabled ? 1 : 0;
        return new byte[]{(byte) COMMAND_DND, 0, (byte) mode, (byte) startHour, (byte) startMinute,
                (byte) endHour, (byte) endMinute,
                (byte) (mode + startHour + startMinute + endHour + endMinute)};
    }
}
