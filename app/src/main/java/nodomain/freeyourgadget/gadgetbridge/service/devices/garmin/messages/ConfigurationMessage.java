/*  Copyright (C) 2024-2026 Daniele Gobbetti, José Rebelo, Thomas Kuehne

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

import java.util.List;
import java.util.SortedSet;

import nodomain.freeyourgadget.gadgetbridge.deviceevents.GBDeviceEvent;
import nodomain.freeyourgadget.gadgetbridge.devices.garmin.GarminCapability;


public class ConfigurationMessage extends GFDIMessage {
    private final byte[] incomingConfigurationPayload;
    private final SortedSet<GarminCapability> capabilities;
    private final byte[] ourConfigurationPayload = GarminCapability.setToBinary(GarminCapability.OUR_CAPABILITIES);

    public ConfigurationMessage(GarminMessage garminMessage, byte[] configurationPayload) {
        this.garminMessage = garminMessage;
        if (configurationPayload.length > 255)
            throw new IllegalArgumentException("Too long payload");
        this.incomingConfigurationPayload = configurationPayload;
        this.capabilities = GarminCapability.setFromBinary(configurationPayload);
        LOG.info("Received configuration message; capabilities: {}", GarminCapability.setToString(capabilities));

        this.statusMessage = this.getStatusMessage();
    }

    public static ConfigurationMessage parseIncoming(MessageReader reader, GarminMessage garminMessage) {
        final int numBytes = reader.readByte();
        return new ConfigurationMessage(garminMessage, reader.readBytes(numBytes));
    }

    @Override
    public List<GBDeviceEvent> getGBDeviceEvent() {
        return GarminCapability.getGBDeviceEvent(capabilities);
    }

    @Override
    protected boolean generateOutgoing() {
        final MessageWriter writer = new MessageWriter(response);
        writer.writeShort(0); // placeholder for packet size
        writer.writeShort(garminMessage.getId());
        writer.writeByte(ourConfigurationPayload.length);
        writer.writeBytes(ourConfigurationPayload);
        return true;
    }

}
