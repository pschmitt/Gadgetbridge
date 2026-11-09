/*  Copyright (C) 2026 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.activities.quicksettings


/**
 * Ten numbered Quick Settings tile slots. Each class is a thin subclass of
 * [AbstractDeviceTileService] that simply identifies its slot index. All ten are declared in the
 * manifest so the user can add any subset to the Quick Settings panel and assign each one
 * independently to a device setting via [QuickSettingsTilesActivity].
 */

class DeviceTileService0 : AbstractDeviceTileService() {
    override fun tileIndex() = 0
}

class DeviceTileService1 : AbstractDeviceTileService() {
    override fun tileIndex() = 1
}

class DeviceTileService2 : AbstractDeviceTileService() {
    override fun tileIndex() = 2
}

class DeviceTileService3 : AbstractDeviceTileService() {
    override fun tileIndex() = 3
}

class DeviceTileService4 : AbstractDeviceTileService() {
    override fun tileIndex() = 4
}

class DeviceTileService5 : AbstractDeviceTileService() {
    override fun tileIndex() = 5
}

class DeviceTileService6 : AbstractDeviceTileService() {
    override fun tileIndex() = 6
}

class DeviceTileService7 : AbstractDeviceTileService() {
    override fun tileIndex() = 7
}

class DeviceTileService8 : AbstractDeviceTileService() {
    override fun tileIndex() = 8
}

class DeviceTileService9 : AbstractDeviceTileService() {
    override fun tileIndex() = 9
}
