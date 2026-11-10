/*  Copyright (C) 2026 Pedro

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.devices.soundpeats

import nodomain.freeyourgadget.gadgetbridge.devices.DeviceCoordinator
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundpeatsQ3ProCoordinatorTest {
    private val coordinator = SoundpeatsQ3ProCoordinator()
    private val device = GBDevice(
        "00:11:22:33:44:55",
        "SOUNDPEATS Q3 Pro",
        null,
        null,
        DeviceType.SOUNDPEATS_Q3_PRO,
    )

    @Test
    fun testBatteryConfigCoversBothEarbudsAndCase() {
        val configs = coordinator.getBatteryConfig(device)

        assertEquals(3, coordinator.getBatteryCount(device))
        assertEquals(3, configs.size)
        assertEquals(listOf(0, 1, 2), configs.map { it.batteryIndex })
        assertTrue(coordinator.supportsOSBatteryLevel(device))
    }

    @Test
    fun testDeviceKindAndManufacturer() {
        assertEquals(DeviceCoordinator.DeviceKind.EARBUDS, coordinator.getDeviceKind(device))
        assertEquals("Soundpeats", coordinator.manufacturer)
        assertEquals(DeviceCoordinator.BONDING_STYLE_NONE, coordinator.bondingStyle)
        assertTrue(coordinator.isExperimental())
    }

    @Test
    fun testFindDeviceAndBatteryPollingAreIntegrated() {
        assertTrue(coordinator.supportsFindDevice(device))
        assertTrue(coordinator.supportsFindDevicePerEarbud(device))
        assertTrue(coordinator.addBatteryPollingSettings())
    }

    @Test
    fun testDeviceSupportIsCreated() {
        assertNotNull(coordinator.createDeviceSupport(device))
    }
}
