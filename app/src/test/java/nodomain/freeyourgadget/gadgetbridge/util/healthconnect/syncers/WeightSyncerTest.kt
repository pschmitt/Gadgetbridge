/*  Copyright (C) 2026 Gadgetbridge contributors

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
package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import nodomain.freeyourgadget.gadgetbridge.entities.GenericWeightSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneOffset

class WeightSyncerTest {

    private val offset = ZoneOffset.UTC
    private val deviceName = "Test Scale"

    private fun metadata(): Metadata =
        Metadata.autoRecorded(
            Device(
                manufacturer = "Test",
                model = "TestModel",
                type = Device.TYPE_SCALE,
            )
        )

    private fun sample(weightKg: Float, timestamp: Long = 1_700_000_000_000L) =
        GenericWeightSample().apply {
            this.weightKg = weightKg
            this.timestamp = timestamp
        }

    // A stable 0.0 kg frame is a scale's step-off/tare event, not a real reading.
    // It must not become a Health Connect weight record, because consumers reject
    // non-positive weights ("must be a positive number").
    @Test
    fun zeroWeight_isSkipped() {
        assertNull(WeightSyncer.convertSample(sample(0.0f), offset, metadata(), deviceName))
    }

    @Test
    fun negativeWeight_isSkipped() {
        assertNull(WeightSyncer.convertSample(sample(-5.0f), offset, metadata(), deviceName))
    }

    @Test
    fun overMaxWeight_isSkipped() {
        assertNull(WeightSyncer.convertSample(sample(2000.0f), offset, metadata(), deviceName))
    }

    @Test
    fun validWeight_producesRecord() {
        val record = WeightSyncer.convertSample(sample(75.5f), offset, metadata(), deviceName)
        assertNotNull(record)
        assertEquals(Mass.kilograms(75.5), record!!.weight)
    }

    @Test
    fun upperBound_isInclusive() {
        assertNotNull(WeightSyncer.convertSample(sample(1000.0f), offset, metadata(), deviceName))
        assertNull(WeightSyncer.convertSample(sample(1000.1f), offset, metadata(), deviceName))
    }
}
