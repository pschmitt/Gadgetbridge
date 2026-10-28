package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.deviceevents.WeatherRequestDeviceEvent;

public class WeatherRequestMessage extends GFDIMessage {
    private final WeatherRequestDeviceEvent weatherRequestDeviceEvent;
    public WeatherRequestMessage(int format, int latitude, int longitude, int hoursOfForecast, GarminMessage garminMessage) {

        this.garminMessage = garminMessage;
        weatherRequestDeviceEvent = new WeatherRequestDeviceEvent(format, latitude, longitude, hoursOfForecast);
        this.statusMessage = this.getStatusMessage();

    }

    public static WeatherRequestMessage parseIncoming(MessageReader reader, GarminMessage garminMessage) {
        final int format = reader.readByte();
        final int latitude = reader.readInt();
        final int longitude = reader.readInt();
        final int hoursOfForecast = reader.readByte();

        return new WeatherRequestMessage(format, latitude, longitude, hoursOfForecast, garminMessage);
    }

    @Override
    public List<GBDeviceEvent> getGBDeviceEvent() {
        return Collections.singletonList(weatherRequestDeviceEvent);
    }

    @Override
    protected boolean generateOutgoing() {
        return false;
    }

}
