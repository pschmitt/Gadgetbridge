/*  Copyright (C) 2024-2026 Daniele Gobbetti, beautiful_orca, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

public class DownloadRequestMessage extends GFDIMessage {

    private final int fileIndex;
    private final REQUEST_TYPE requestType;
    private final int crcSeed;
    private final int dataSize;

    private final int dataOffset;

    // 0x01 = use compression
    private final int requestFlags;

    public DownloadRequestMessage(GarminMessage garminMessage, int fileIndex, int size, REQUEST_TYPE requestType, int crcSeed, int dataSize, int dataOffset, int requestFlags) {
        this.requestType = requestType;
        this.crcSeed = crcSeed;
        this.dataSize = dataSize;
        this.dataOffset = dataOffset;
        this.garminMessage = garminMessage;
        this.fileIndex = fileIndex;
        this.statusMessage = this.getStatusMessage();
        this.requestFlags = requestFlags;
    }

    public DownloadRequestMessage(int fileIndex, int dataSize, REQUEST_TYPE requestType, int crcSeed, int dataOffset, int requestFlags) {
        this(GarminMessage.DOWNLOAD_REQUEST, fileIndex, dataSize, requestType, crcSeed, dataSize, dataOffset, requestFlags);
    }

    @Override
    protected boolean generateOutgoing() {
        final MessageWriter writer = new MessageWriter(response);
        writer.writeShort(0); // packet size will be filled below
        writer.writeShort(this.garminMessage.getId());
        writer.writeShort(this.fileIndex);
        writer.writeInt(this.dataOffset);
        writer.writeByte(this.requestType.ordinal());
        writer.writeShort(this.crcSeed);
        writer.writeInt(this.dataSize);
        writer.writeByte(this.requestFlags);

        return true;
    }

    public enum REQUEST_TYPE {
        CONTINUE,
        NEW,
    }

}
