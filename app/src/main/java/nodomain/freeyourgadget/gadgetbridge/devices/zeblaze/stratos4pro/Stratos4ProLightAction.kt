/*  Copyright (C) 2026 Vladimir Tasic

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
package nodomain.freeyourgadget.gadgetbridge.devices.zeblaze.stratos4pro

import android.content.Context
import android.graphics.Color
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCardAction
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProLights
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProLights.Light
import nodomain.freeyourgadget.gadgetbridge.service.devices.zeblaze.stratos4pro.Stratos4ProLights.Mode

/**
 * Card button for one LED. Tap: off -> on (or strobe, if set), on/strobe -> off; the label shows
 * which.
 */
class Stratos4ProLightAction(private val light: Light) : DeviceCardAction {
    private fun mode(device: GBDevice): Mode = Stratos4ProLights.state(device.address).mode(light)

    override fun getId(): String = when (light) {
        Light.WHITE -> "stratos4pro_light_white"
        Light.RED -> "stratos4pro_light_red"
    }

    override fun getIcon(device: GBDevice): Int =
        if (mode(device) == Mode.OFF) R.drawable.ic_flashlight_off else R.drawable.ic_flashlight_on

    override fun getIconTint(device: GBDevice, context: Context): Int? =
        if (light == Light.RED) RED else null

    override fun getDescription(device: GBDevice, context: Context): String =
        context.getString(
            when (light) {
                Light.WHITE -> R.string.stratos4pro_light_white
                Light.RED -> R.string.stratos4pro_light_red
            }
        )

    override fun getLabel(device: GBDevice, context: Context): String? =
        when (mode(device)) {
            Mode.OFF -> null
            Mode.ON -> context.getString(R.string.on)
            Mode.STROBE -> context.getString(R.string.stratos4pro_light_strobe)
        }

    override fun isVisible(device: GBDevice): Boolean {
        val coordinator = device.deviceCoordinator as? Stratos4ProCoordinator ?: return false
        return device.isInitialized &&
                coordinator.supportsLights(device) &&
                Stratos4ProLights.state(device.address).serviceFound
    }

    override fun onClick(device: GBDevice, context: Context) {
        GBApplication.deviceService(device).onSendConfiguration(
            when (light) {
                Light.WHITE -> Stratos4ProLights.PREF_WHITE_TOGGLE
                Light.RED -> Stratos4ProLights.PREF_RED_TOGGLE
            }
        )
    }

    private companion object {
        /** Material red 500. */
        val RED = Color.rgb(0xf4, 0x43, 0x36)
    }
}
