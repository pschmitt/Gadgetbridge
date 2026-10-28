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

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.GenericHeartRateSample
import nodomain.freeyourgadget.gadgetbridge.entities.GenericSleepStageSample
import nodomain.freeyourgadget.gadgetbridge.entities.Stratos4ProStepsSample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Test

class Stratos4ProActivitySampleProviderTest : TestBase() {
    @Test
    fun mergesStepsHeartRateAndSleep() {
        val device = createDummyGDevice("00:00:00:00:00:31")
        val deviceId = DBHelper.getDevice(device, daoSession).id!!
        val userId = DBHelper.getUser(daoSession).id!!
        val t = 1_700_000_000
        daoSession.stratos4ProStepsSampleDao.insert(Stratos4ProStepsSample().apply {
            timestamp = t * 1000L; this.deviceId = deviceId; this.userId = userId
            steps = 12; distanceCm = 900; activeCalories = 4
        })
        for ((ts, bpm) in listOf(t to 70, t + 60 to 55)) {
            daoSession.genericHeartRateSampleDao.insert(GenericHeartRateSample().apply {
                timestamp = ts * 1000L; this.deviceId = deviceId; this.userId = userId; heartRate = bpm
            })
        }
        // Deep sleep from t+30 for 2 minutes: covers the HR-only minute.
        daoSession.genericSleepStageSampleDao.insert(GenericSleepStageSample().apply {
            timestamp = (t + 30) * 1000L; this.deviceId = deviceId; this.userId = userId
            duration = 2; stage = 1
        })
        val provider = Stratos4ProActivitySampleProvider(device, daoSession)

        val samples = provider.getAllActivitySamples(t, t + 120)
        assertEquals(listOf(t, t + 60), samples.map { it.timestamp })
        with(samples[0]) {
            assertEquals(listOf(12, 900, 4, 70), listOf(steps, distanceCm, activeCalories, heartRate))
            assertEquals(ActivityKind.NOT_MEASURED, kind)
        }
        with(samples[1]) {
            assertEquals(listOf(ActivitySample.NOT_MEASURED, 55), listOf(steps, heartRate))
            assertEquals(ActivityKind.DEEP_SLEEP, kind)
        }
        // A stage that started before the range still applies.
        assertEquals(ActivityKind.DEEP_SLEEP, provider.getAllActivitySamples(t + 60, t + 60).single().kind)

        assertEquals(t, provider.firstActivitySample!!.timestamp)
        assertEquals(t + 60, provider.getLatestActivitySample(t + 120)!!.timestamp)
    }
}
