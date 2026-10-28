/*  Copyright (C) 2026 Dany Mestas

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.concept2.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PM5ErgKindTest {
    @Test
    fun nameGivesTheErg() {
        assertEquals(PM5ErgKind.ROWER, PM5ErgKind.fromDeviceName("PM5 430070439 Row"))
        assertEquals(PM5ErgKind.SKI, PM5ErgKind.fromDeviceName("PM5 430070439 Ski"))
        assertEquals(PM5ErgKind.BIKE, PM5ErgKind.fromDeviceName("PM5 430070439 bike"))
    }

    @Test
    fun nameWithoutErg() {
        assertNull(PM5ErgKind.fromDeviceName("PM5 430070439"))
        assertNull(PM5ErgKind.fromDeviceName(null))
    }

    @Test
    fun reportedMachineTypeWinsOverName() {
        assertEquals(PM5ErgKind.BIKE, PM5ErgKind.of(192, PM5ErgKind.ROWER))
    }

    @Test
    fun nameStandsInForMissingOrUnknownMachineType() {
        assertEquals(PM5ErgKind.SKI, PM5ErgKind.of(null, PM5ErgKind.SKI))
        assertEquals(PM5ErgKind.BIKE, PM5ErgKind.of(100, PM5ErgKind.BIKE))
    }

    @Test
    fun rowerWhenNothingIsKnown() {
        assertEquals(PM5ErgKind.ROWER, PM5ErgKind.of(null, null))
        assertEquals(PM5ErgKind.UNKNOWN, PM5ErgKind.of(100, null))
    }
}
