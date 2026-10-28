package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status;

public class DownloadRequestStatusMessage extends GFDIStatusMessage {
    private final Status status;
    private final DownloadStatus downloadStatus;
    private final int maxFileSize;

    public DownloadRequestStatusMessage(GarminMessage garminMessage, Status status, DownloadStatus downloadStatus, int maxFileSize) {
        this.garminMessage = garminMessage;
        this.status = status;
        this.downloadStatus = downloadStatus;
        this.maxFileSize = maxFileSize;
    }

    public static DownloadRequestStatusMessage parseIncoming(MessageReader reader, GarminMessage garminMessage) {
        final Status status = Status.fromCode(reader.readByte());

        if (!status.equals(Status.ACK)) {
            return null;
        }
        final DownloadStatus downloadStatus = DownloadStatus.fromId(reader.readByte());
        final int maxFileSize = reader.readInt();

        if (!DownloadStatus.OK.equals(downloadStatus)) {
            LOG.warn("Received {} / {} for message {}", status, downloadStatus, garminMessage);
        } else {
            LOG.info("Received {} / {} for message {}", status, downloadStatus, garminMessage);
        }
        return new DownloadRequestStatusMessage(garminMessage, status, downloadStatus, maxFileSize);
    }

    public int getMaxFileSize() {
        return maxFileSize;
    }

    public boolean canProceed() {
        return status.equals(Status.ACK) && downloadStatus.equals(DownloadStatus.OK);
    }

    public enum DownloadStatus { //was DownloadRequestResponseMessage
        OK,
        INDEX_NOT_FOUND,
        NOT_READABLE,
        NOT_READY,
        INVALID_REQUEST,
        CRC_MISMATCH,
        RANGE_EXCEEDS_FILE,
        ;

        public static DownloadStatus fromId(int id) {
            for (DownloadStatus downloadStatus :
                    DownloadStatus.values()) {
                if (downloadStatus.ordinal() == id) {
                    return downloadStatus;
                }
            }
            return null;
        }
    }
}
