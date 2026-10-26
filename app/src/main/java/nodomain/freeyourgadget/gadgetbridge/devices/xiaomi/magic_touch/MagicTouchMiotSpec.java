/*  Copyright (C) 2026 Francesco Colasuonno

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
    along with this program.  If not, see <http://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.magic_touch;

/**
 * miot-spec identifiers for {@code xiaomi.magic_touch.jj02}
 * (urn:miot-spec-v2:device:massager:0000A083:xiaomi-jj02:1).
 *
 * <p>Source: https://home.miot-spec.com/spec/xiaomi.magic_touch.jj02
 * Service ids (siid) are the numbers after '#', property ids (piid) likewise.
 */
public final class MagicTouchMiotSpec {
    private MagicTouchMiotSpec() {
    }

    // Services
    public static final int SIID_MASSAGER = 2;
    public static final int SIID_BATTERY = 5;
    public static final int SIID_MASSAGER_CONTROL = 4;

    // massager (siid 2) properties
    public static final int PID_STATUS = 2; // 0 off, 1 on, 2 charging
    public static final int PID_BATTERY_LEVEL = 1003; // 0-100, read/notify

    // massager-control (siid 4) properties
    public static final int PID_MODE = 1; // 1 gentle, 2 relax, 3 stress relief, 4 custom
    public static final int PID_DEFAULT_MODE = 2;
    public static final int PID_TARGET_TIME = 3; // minutes
    public static final int PID_TARGET_TIME_MIN = 2;
    public static final int PID_TARGET_TIME_MAX = 20;
    public static final int PID_COUNTDOWN = 4; // minutes, 2-20
    public static final int PID_HEATING = 5; // bool
    public static final int PID_HEAT_LEVEL = 6; // 1 = 40C, 2 = 45C
    public static final int PID_DEFAULT_HEAT_MODE = 7; // 0 off, 1 = 40C, 2 = 45C
    public static final int PID_DEFAULT_BEEP_MODE = 8; // bool
    public static final int PID_MASSAGE_MANIPULATION = 9; // string (custom program)
    public static final int PID_ON = 10; // 0 off, 1 on, 2 charge, 3 full
    public static final int PID_CUSTOM_NAME = 11; // string
    public static final int PID_MASSAGE_HEAD_INIT = 12; // 0 reserve, 1 normal, 2 more-better
}
