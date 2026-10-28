/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * Sequence numbers and unacked frames of the data packets sent over SPP V2. Sequence numbers are
 * 8 bit and wrap. Acks are cumulative: acking a sequence number releases every frame sent up to
 * and including it.
 */
final class XiaomiSppV2SendWindow {
    /// Half the sequence space, so a sequence number identifies at most one tracked frame.
    static final int MAX_TRACKED_FRAMES = 128;

    private static final class Frame {
        final int sequenceNumber;
        final byte[] bytes;

        Frame(final int sequenceNumber, final byte[] bytes) {
            this.sequenceNumber = sequenceNumber;
            this.bytes = bytes;
        }
    }

    private final ArrayDeque<Frame> unacked = new ArrayDeque<>();
    private int nextSequenceNumber = 0;

    synchronized int nextSequenceNumber() {
        final int sequenceNumber = nextSequenceNumber;
        nextSequenceNumber = (nextSequenceNumber + 1) & 0xff;
        return sequenceNumber;
    }

    /**
     * Tracks a written frame until it is acked. Past {@link #MAX_TRACKED_FRAMES} the oldest frame
     * is forgotten and can no longer be resent.
     */
    synchronized void onSent(final int sequenceNumber, final byte[] frame) {
        unacked.addLast(new Frame(sequenceNumber, frame));
        while (unacked.size() > MAX_TRACKED_FRAMES) {
            unacked.removeFirst();
        }
    }

    /**
     * @return the number of frames released, 0 if the sequence number matches no unacked frame
     */
    synchronized int onAck(final int sequenceNumber) {
        boolean found = false;
        for (final Frame frame : unacked) {
            if (frame.sequenceNumber == sequenceNumber) {
                found = true;
                break;
            }
        }
        if (!found) {
            return 0;
        }

        int released = 0;
        Frame frame;
        do {
            frame = unacked.removeFirst();
            released++;
        } while (frame.sequenceNumber != sequenceNumber);
        return released;
    }

    /**
     * @return the sequence number of the oldest unacked frame, or -1 if every frame was acked
     */
    synchronized int getOldestUnacked() {
        final Frame oldest = unacked.peekFirst();
        return oldest != null ? oldest.sequenceNumber : -1;
    }

    synchronized boolean isEmpty() {
        return unacked.isEmpty();
    }

    /**
     * @return the unacked frames, oldest first, as they were written
     */
    synchronized List<byte[]> getUnackedFrames() {
        final List<byte[]> frames = new ArrayList<>(unacked.size());
        for (final Frame frame : unacked) {
            frames.add(frame.bytes);
        }
        return frames;
    }

    synchronized void clear() {
        unacked.clear();
        nextSequenceNumber = 0;
    }
}
