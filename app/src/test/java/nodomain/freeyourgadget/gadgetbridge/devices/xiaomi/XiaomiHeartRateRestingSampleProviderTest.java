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
package nodomain.freeyourgadget.gadgetbridge.devices.xiaomi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.XiaomiDailySummarySampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiDailySummarySample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.HeartRateSample;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * The band writes a daily summary before it has computed that day's resting heart rate, so the
 * newest (or oldest) summary may carry none. First and latest must skip those days.
 */
public class XiaomiHeartRateRestingSampleProviderTest extends TestBase {
    private static final long DAY = 86_400_000L;
    private static final long DAY1 = 1_780_000_000_000L;
    private static final long DAY2 = DAY1 + DAY;
    private static final long DAY3 = DAY2 + DAY;

    private GBDevice device;
    private XiaomiHeartRateRestingSampleProvider provider;

    @Before
    public void setUpProvider() {
        device = createDummyGDevice("00:00:00:00:00:31");
        DBHelper.getDevice(device, daoSession);
        provider = new XiaomiHeartRateRestingSampleProvider(device, daoSession);
    }

    private void store(final XiaomiDailySummarySample... samples) {
        new XiaomiDailySummarySampleProvider(device, daoSession).persistSamples(Arrays.asList(samples), getContext());
    }

    private static XiaomiDailySummarySample summary(final long timestamp, final Integer hrResting) {
        final XiaomiDailySummarySample sample = new XiaomiDailySummarySample();
        sample.setTimestamp(timestamp);
        sample.setSteps(1000);
        sample.setHrResting(hrResting);
        return sample;
    }

    @Test
    public void latestSkipsANewerSummaryWithoutRestingHeartRate() {
        store(summary(DAY1, 58), summary(DAY2, null));

        final HeartRateSample latest = provider.getLatestSample();
        assertNotNull(latest);
        assertEquals(DAY1, latest.getTimestamp());
        assertEquals(58, latest.getHeartRate());
    }

    @Test
    public void firstSkipsAnOlderSummaryWithoutRestingHeartRate() {
        store(summary(DAY1, null), summary(DAY2, 61));

        final HeartRateSample first = provider.getFirstSample();
        assertNotNull(first);
        assertEquals(DAY2, first.getTimestamp());
        assertEquals(61, first.getHeartRate());
    }

    @Test
    public void latestUntilSkipsSummariesWithoutRestingHeartRate() {
        store(summary(DAY1, 58), summary(DAY2, null), summary(DAY3, 60));

        final HeartRateSample latest = provider.getLatestSample(DAY2);
        assertNotNull(latest);
        assertEquals(DAY1, latest.getTimestamp());
    }

    @Test
    public void allSamplesHoldOnlyDaysWithRestingHeartRate() {
        store(summary(DAY1, null), summary(DAY2, 58), summary(DAY3, null));

        final List<HeartRateSample> samples = provider.getAllSamples(DAY1, DAY3);
        assertEquals(1, samples.size());
        assertEquals(DAY2, samples.get(0).getTimestamp());
    }

    @Test
    public void noRestingHeartRateAtAllGivesNoSample() {
        store(summary(DAY1, null), summary(DAY2, null));

        assertNull(provider.getLatestSample());
        assertNull(provider.getFirstSample());
    }
}
