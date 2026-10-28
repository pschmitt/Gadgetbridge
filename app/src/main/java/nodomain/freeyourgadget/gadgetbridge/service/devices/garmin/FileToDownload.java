package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.proto.garmin.GdiFileAccess;

public class FileToDownload {
    private final FileTransferHandler.DirectoryEntry directoryEntry;
    private final GdiFileAccess.ItemReference syncFile;

    public FileToDownload(final FileTransferHandler.DirectoryEntry directoryEntry) {
        this.directoryEntry = directoryEntry;
        this.syncFile = null;
    }

    public FileToDownload(final GdiFileAccess.ItemReference syncFile) {
        this.directoryEntry = null;
        this.syncFile = syncFile;
    }

    @Nullable
    public FileTransferHandler.DirectoryEntry getDirectoryEntry() {
        return directoryEntry;
    }

    @Nullable
    public GdiFileAccess.ItemReference getSyncFile() {
        return syncFile;
    }

    public long getSize() {
        return directoryEntry != null ? directoryEntry.getFileSize() :
                syncFile != null ? syncFile.getDataSize() : -1;
    }
}
