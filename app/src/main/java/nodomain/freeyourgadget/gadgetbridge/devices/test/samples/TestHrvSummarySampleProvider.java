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
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample;

public class TestHrvSummarySampleProvider extends AbstractTestSampleProvider<HrvSummarySample> {
    private static final long WAKE_TIME_MILLIS = 7 * 60 * 60 * 1000L;

    @NonNull
    @Override
    protected List<HrvSummarySample> generateSamples(final long timestampFrom, final long timestampTo) {
        final List<HrvSummarySample> samples = new ArrayList<>();

        for (long day = startOfDay(timestampFrom); day < timestampTo; day += DAY_MILLIS) {
            final long ts = day + WAKE_TIME_MILLIS;
            if (ts < timestampFrom || ts >= timestampTo) {
                continue;
            }
            samples.add(new TestHrvSummarySample(ts));
        }

        return samples;
    }

    @Nullable
    @Override
    public HrvSummarySample getLatestSample() {
        return getLatestSample(System.currentTimeMillis());
    }

    @Nullable
    @Override
    public HrvSummarySample getFirstSample() {
        return new TestHrvSummarySample(startOfDay(TestDeviceRand.BASE_TIMESTAMP) + WAKE_TIME_MILLIS);
    }

    protected static class TestHrvSummarySample implements HrvSummarySample {
        private final long timestamp;
        private final int lastNightAverage;
        private final int weeklyAverage;

        public TestHrvSummarySample(final long timestamp) {
            this.timestamp = timestamp;
            this.lastNightAverage = TestHrvValueSampleProvider.nightlyAverage(timestamp);

            int sum = 0;
            for (int i = 0; i < 7; i++) {
                sum += TestHrvValueSampleProvider.nightlyAverage(timestamp - i * DAY_MILLIS);
            }
            this.weeklyAverage = Math.round(sum / 7f);
        }

        @Override
        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public Integer getWeeklyAverage() {
            return weeklyAverage;
        }

        @Override
        public Integer getLastNightAverage() {
            return lastNightAverage;
        }

        @Override
        public Integer getLastNight5MinHigh() {
            return lastNightAverage + TestDeviceRand.randInt(timestamp, 10, 16);
        }

        @Override
        public Integer getBaselineLowUpper() {
            return 50;
        }

        @Override
        public Integer getBaselineBalancedLower() {
            return 65;
        }

        @Override
        public Integer getBaselineBalancedUpper() {
            return 80;
        }

        @Override
        public Status getStatus() {
            if (getWeeklyAverage() > getBaselineBalancedUpper()) {
                return Status.UNBALANCED;
            } else if (getWeeklyAverage() < getBaselineLowUpper()) {
                return Status.POOR;
            } else if (getWeeklyAverage() < getBaselineBalancedLower()) {
                return Status.LOW;
            }

            return Status.BALANCED;
        }
    }
}
