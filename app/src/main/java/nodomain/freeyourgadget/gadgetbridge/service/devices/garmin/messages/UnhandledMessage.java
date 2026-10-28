package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages.status.GenericStatusMessage;

public class UnhandledMessage extends GFDIMessage {

    private final int messageType;

    public UnhandledMessage(int messageType) {
        this.messageType = messageType;

        this.statusMessage = new GenericStatusMessage(messageType, Status.UNSUPPORTED);
    }

    public UnhandledMessage(GarminMessage garminMessage) {
        this.messageType = garminMessage.getId();
        this.garminMessage = garminMessage;
        this.statusMessage = new GenericStatusMessage(garminMessage, Status.UNSUPPORTED, false);
    }

    @Override
    protected boolean generateOutgoing() {
        return false;
    }
}
