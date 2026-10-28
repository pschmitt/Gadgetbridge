/*  Copyright (C) 2024-2026 Daniele Gobbetti, Thomas Kuehne

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

import androidx.annotation.NonNull;

public class FilterMessage extends GFDIMessage {

    private final FilterType filterType;

    public FilterMessage(@NonNull FilterType filterType) {
        this.garminMessage = GarminMessage.FILTER;
        this.filterType = filterType;
    }

    @Override
    protected boolean generateOutgoing() {
        final MessageWriter writer = new MessageWriter(response);
        writer.writeShort(0); // packet size will be filled below
        writer.writeShort(this.garminMessage.getId());
        writer.writeByte(filterType.ordinal());

        return true;
    }

    public enum FilterType {
        ALL_FILES_0,
        DEFAULT,
        UNK_2,
        ONLY_NEW_3
    }
}
