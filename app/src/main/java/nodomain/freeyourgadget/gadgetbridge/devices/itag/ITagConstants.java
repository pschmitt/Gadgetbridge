/*  Copyright (C) 2020-2024 Taavi Eomäe

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
package nodomain.freeyourgadget.gadgetbridge.devices.itag;

import java.util.UUID;

public final class ITagConstants {
    /** Contains information about the button state */
    public static final UUID UUID_SERVICE_BUTTON = UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb");
    /** Contains information about the button state */
    public static final UUID UUID_LINK_LOSS_ALERT_LEVEL = UUID.fromString("00002a06-0000-1000-8000-00805f9b34fb");
    /** Serial service (nRF-style) that contains the button (ffe1) and link-loss (ffe2) characteristics */
    public static final UUID UUID_SERVICE_SERIAL_NRF = UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb");
    /** Writing 0x01/0x00 enables/disables the beep on disconnect */
    public static final UUID UUID_CHAR_LINKLOSS_FFE2 = UUID.fromString("0000ffe2-0000-1000-8000-00805f9b34fb");

    public static final String PREF_ALERT_LEVEL = "itag_alert_level";
    public static final byte ALERT_LEVEL_MILD = 0x01;
    public static final byte ALERT_LEVEL_HIGH = 0x02;

    public static final String PREF_BEEP_ON_DISCONNECT = "itag_beep_on_disconnect";

    public static final String PREF_BUTTON_EVENT = "itag_button_event";

}
