/*  Copyright (C) 2024 José Rebelo

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
package nodomain.freeyourgadget.gadgetbridge.devices.test.samples;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.devices.test.TestDeviceRand;
import nodomain.freeyourgadget.gadgetbridge.model.HrvValueSample;

public class TestHrvValueSampleProvider extends AbstractTestSampleProvider<HrvValueSample> {
    private static final long HOUR_MILLIS = 60 * 60 * 1000L;
    private static final long INTERVAL_MILLIS = 5 * 60 * 1000L;
    private static final long NIGHT_START_MILLIS = -HOUR_MILLIS;
    private static final long NIGHT_END_MILLIS = 7 * HOUR_MILLIS;

    /**
     * Returns the average HRV of the night that ends on the given day.
     */
    static int nightlyAverage(final long day) {
        final long dayIndex = Math.round(startOfDay(day) / (double) DAY_MILLIS);
        final double trend = 8 * Math.sin(2 * Math.PI * dayIndex / 30.0);
        return (int) Math.round(70 + trend + TestDeviceRand.randInt(dayIndex * DAY_MILLIS, -6, 6));
    }

    @NonNull
    @Override
    protected List<HrvValueSample> generateSamples(final long timestampFrom, final long timestampTo) {
        final List<HrvValueSample> samples = new ArrayList<>();

        final long firstTs = (timestampFrom + INTERVAL_MILLIS - 1) / INTERVAL_MILLIS * INTERVAL_MILLIS;
        for (long ts = firstTs; ts < timestampTo; ts += INTERVAL_MILLIS) {
            // The night belongs to the day on which it ends
            final long day = startOfDay(ts - NIGHT_START_MILLIS);
            final long sinceNightStart = ts - (day + NIGHT_START_MILLIS);
            if (sinceNightStart >= NIGHT_END_MILLIS - NIGHT_START_MILLIS) {
                continue;
            }

            // Increase HRV during the night
            final float progress = (float) sinceNightStart / (NIGHT_END_MILLIS - NIGHT_START_MILLIS);
            final int hrv = nightlyAverage(day) + Math.round((progress - 0.5f) * 16) + TestDeviceRand.randInt(ts, -4, 4);
            samples.add(new TestHrvValueSample(ts, clamp(hrv, 30, 110)));
        }

        return samples;
    }

    @Nullable
    @Override
    public HrvValueSample getLatestSample() {
        return getLatestSample(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public HrvValueSample getFirstSample() {
        return new TestHrvValueSample(
            TestDeviceRand.BASE_TIMESTAMP,
            nightlyAverage(TestDeviceRand.BASE_TIMESTAMP)
        );
    }

    protected static class TestHrvValueSample implements HrvValueSample {
        private final long timestamp;
        private final int hrv;

        public TestHrvValueSample(final long timestamp, final int hrv) {
            this.timestamp = timestamp;
            this.hrv = hrv;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public int getValue() {
            return hrv;
        }
    }
}
