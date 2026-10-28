package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import org.apache.commons.lang3.EnumUtils;

import java.util.EnumSet;

public class SetFileFlagsMessage extends GFDIMessage {

    private final int fileIndex;
    private final FileFlags flags;

    public SetFileFlagsMessage(int fileIndex, FileFlags flags) {
        this.garminMessage = GarminMessage.SET_FILE_FLAG;
        this.fileIndex = fileIndex;
        this.flags = flags;
    }

    @Override
    protected boolean generateOutgoing() {
        final MessageWriter writer = new MessageWriter(response);
        writer.writeShort(0); // packet size will be filled below
        writer.writeShort(this.garminMessage.getId());
        writer.writeShort(this.fileIndex);
        writer.writeByte((int) EnumUtils.generateBitVector(FileFlags.class, this.flags));
        return true;
    }

    public enum FileFlags {
        UNK_00000001,
        UNK_00000010,
        CRYPTO, // 4 - 0x04 - 0000_0100
        APPEND, // 8 - 0x08 - 0000_1000
        ARCHIVE, // 16 - 0x10 - 0001_0000
        ERASE, // 32 - 0x20 - 0010_0000
        WRITE, // 64 - 0x40 - 0100_0000
        READ, // 128 - 0x80 - 1000_0000
        ;

        public static EnumSet<FileFlags> fromBitMask(final int code) {
            return EnumUtils.processBitVector(FileFlags.class, code);
        }

    }
}
