package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.watchapps;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Objects;
import java.util.UUID;

import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport;

public class GarminNavigationApp extends GarminWatchApp {
    public static final UUID APP_UUID = UUID.fromString("0730e73c-f977-47b3-84e1-cc030f0aefb6");

    private JsonObject lastMessage;

    public GarminNavigationApp(final GarminSupport support) {
        super(support, APP_UUID);
    }

    /**
     * Sends the navigation info to the watch app. Returns false if the watch app is not available.
     */
    public boolean onSetNavigationInfo(final NavigationInfoSpec navigationInfoSpec) {
        if (!isAvailable()) {
            return false;
        }

        final JsonObject message = new JsonObject();
        message.addProperty("action", navigationInfoSpec.getNextAction());
        if (navigationInfoSpec.getInstruction() != null) {
            message.addProperty("text", navigationInfoSpec.getInstruction());
        }
        if (navigationInfoSpec.getDistanceToTurn() != null) {
            message.addProperty("distance", navigationInfoSpec.getDistanceToTurn());
        }
        if (navigationInfoSpec.getETA() != null) {
            message.addProperty("eta", navigationInfoSpec.getETA());
        }
        message.addProperty("progress", navigationInfoSpec.getCompletionPercent());
        message.addProperty("alert", lastMessage == null
            || !Objects.equals(lastMessage.get("action"), message.get("action"))
            || !Objects.equals(lastMessage.get("text"), message.get("text")));

        lastMessage = message;
        sendMessage(message, true, null);
        return true;
    }

    @Override
    public void onMessage(final JsonElement message) {
        if (lastMessage == null) {
            return;
        }
        if (message.isJsonPrimitive() && "ready".equals(message.getAsString())) {
            final JsonObject repeat = lastMessage.deepCopy();
            repeat.addProperty("alert", false);
            sendMessage(repeat, true, null);
        }
    }
}
