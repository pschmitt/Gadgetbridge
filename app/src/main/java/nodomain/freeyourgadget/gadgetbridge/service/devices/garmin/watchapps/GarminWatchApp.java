package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.watchapps;

import androidx.annotation.Nullable;

import com.google.gson.JsonElement;

import java.util.UUID;
import java.util.function.Consumer;

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.communicator.GarminWatchAppCommunicator;

public abstract class GarminWatchApp {
    private final GarminSupport support;
    private final UUID uuid;

    protected GarminWatchApp(final GarminSupport support, final UUID uuid) {
        this.support = support;
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    protected GarminSupport getSupport() {
        return support;
    }

    public abstract void onMessage(JsonElement message);

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    protected boolean isAvailable() {
        return support.getWatchAppCommunicator() != null
            && support.getCoordinator().isAppInstalled(support.getDevice(), uuid);
    }

    /**
     * Queues a message to the watch app. If replace is set, the queued messages for this app are removed.
     * The onResult callback receives true when the watch acknowledges the message, false when the transfer fails.
     */
    protected void sendMessage(final JsonElement message,
                               final boolean replace,
                               @Nullable final Consumer<Boolean> onResult) {
        final GarminWatchAppCommunicator communicator = support.getWatchAppCommunicator();
        if (communicator != null) {
            communicator.sendMessage(uuid, message, replace, onResult);
        }
    }
}
