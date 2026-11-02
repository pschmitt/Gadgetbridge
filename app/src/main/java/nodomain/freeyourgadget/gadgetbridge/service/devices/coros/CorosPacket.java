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

import java.util.Arrays;

/** A complete incoming control packet, with its checksum removed from the payload. */
final class CorosPacket {
    private final int opcode;
    private final byte[] payload;

    private CorosPacket(final byte[] frame) {
        opcode = frame[0] & 0xff;
        payload = Arrays.copyOfRange(frame, 2, frame.length - 1);
    }

    static CorosPacket parse(final byte[] frame) {
        // Incoming packets need a header and checksum. Header-only outgoing requests
        // such as find-device use a different format and must not enter this parser.
        if (frame.length < 3) {
            throw new IllegalArgumentException("Packet is too short");
        }
        if (frame[1] != 0) {
            throw new IllegalArgumentException("Fragmented packets are not supported");
        }
        int checksum = 0;
        // Opcode and the second header byte are excluded from the body checksum.
        for (int i = 2; i < frame.length - 1; i++) {
            checksum += frame[i] & 0xff;
        }
        if ((checksum & 0xff) != (frame[frame.length - 1] & 0xff)) {
            throw new IllegalArgumentException("Invalid checksum");
        }
        return new CorosPacket(frame);
    }

    int getOpcode() {
        return opcode;
    }

    byte[] getPayload() {
        return payload.clone();
    }
}
