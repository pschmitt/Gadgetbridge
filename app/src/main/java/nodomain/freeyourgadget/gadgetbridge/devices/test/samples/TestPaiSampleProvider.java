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
import nodomain.freeyourgadget.gadgetbridge.model.PaiSample;

/**
 * One sample per hour.
 * The PAI for the day increases during waking hours.
 * The total is the sum of the previous 6 days and the current day.
 */
public class TestPaiSampleProvider extends AbstractTestSampleProvider<PaiSample> {
    private static final long HOUR_MILLIS = 60 * 60 * 1000L;
    private static final int WAKE_HOUR = 7;
    private static final int SLEEP_HOUR = 22;

    @NonNull
    @Override
    public List<PaiSample> getAllSamples(final long timestampFrom, final long timestampTo) {
        final List<PaiSample> samples = new ArrayList<>();
        final long now = System.currentTimeMillis();

        final long firstTs = (timestampFrom + HOUR_MILLIS - 1) / HOUR_MILLIS * HOUR_MILLIS;
        for (long ts = firstTs; ts < timestampTo && ts <= now; ts += HOUR_MILLIS) {
            samples.add(createSample(ts));
        }

        return samples;
    }

    @Nullable
    @Override
    public PaiSample getLatestSample() {
        final long now = System.currentTimeMillis();
        return createSample(now / HOUR_MILLIS * HOUR_MILLIS);
    }

    @Nullable
    @Override
    public PaiSample getFirstSample() {
        return createSample(TestDeviceRand.BASE_TIMESTAMP / HOUR_MILLIS * HOUR_MILLIS);
    }

    private static TestPaiSample createSample(final long timestamp) {
        final long day = startOfDay(timestamp);
        final float progress = clamp((float) (hourOfDay(timestamp) - WAKE_HOUR) / (SLEEP_HOUR - WAKE_HOUR), 0f, 1f);

        final int low = Math.round(paiLow(day) * progress);
        final int moderate = Math.round(paiModerate(day) * progress);
        final int high = Math.round(paiHigh(day) * progress);

        int total = low + moderate + high;
        for (int i = 1; i < 7; i++) {
            final long previousDay = startOfDay(day - i * DAY_MILLIS + HOUR_MILLIS);
            total += paiLow(previousDay) + paiModerate(previousDay) + paiHigh(previousDay);
        }

        return new TestPaiSample(timestamp, low, moderate, high, total);
    }

    private static int paiLow(final long day) {
        return TestDeviceRand.randInt(day, 0, 5);
    }

    private static int paiModerate(final long day) {
        return TestDeviceRand.randInt(day + 1, 0, 10);
    }

    private static int paiHigh(final long day) {
        return TestDeviceRand.randInt(day + 2, 0, 15);
    }

    public static class TestPaiSample implements PaiSample {
        private final long timestamp;
        private final int paiLow;
        private final int paiModerate;
        private final int paiHigh;
        private final int paiTotal;

        public TestPaiSample(final long timestamp,
                             final int low,
                             final int moderate,
                             final int high,
                             final int total) {
            this.timestamp = timestamp;
            this.paiLow = low;
            this.paiModerate = moderate;
            this.paiHigh = high;
            this.paiTotal = total;
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public float getPaiLow() {
            return paiLow;
        }

        @Override
        public float getPaiModerate() {
            return paiModerate;
        }

        @Override
        public float getPaiHigh() {
            return paiHigh;
        }

        @Override
        public int getTimeLow() {
            return paiLow;
        }

        @Override
        public int getTimeModerate() {
            return paiModerate;
        }

        @Override
        public int getTimeHigh() {
            return paiHigh;
        }

        @Override
        public float getPaiToday() {
            return paiLow + paiModerate + paiHigh;
        }

        @Override
        public float getPaiTotal() {
            return paiTotal;
        }
    }
}
