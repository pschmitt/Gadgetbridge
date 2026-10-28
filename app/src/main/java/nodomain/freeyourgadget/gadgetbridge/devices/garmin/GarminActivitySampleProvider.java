/*  Copyright (C) 2024-2026 José Rebelo, Martin.JM, Thomas Kuehne, trentsuzuki

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
package nodomain.freeyourgadget.gadgetbridge.devices.garmin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.GarminActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

public class GarminActivitySampleProvider extends AbstractSampleProvider<GarminActivitySample> {
    private static final Logger LOG = LoggerFactory.getLogger(GarminActivitySampleProvider.class);

    public GarminActivitySampleProvider(final GBDevice device, final DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<GarminActivitySample, ?> getSampleDao() {
        return getSession().getGarminActivitySampleDao();
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return GarminActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return GarminActivitySampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return GarminActivitySampleDao.Properties.DeviceId;
    }

    @Override
    public ActivityKind normalizeType(final int rawType) {
        return ActivityKind.fromCode(rawType);
    }

    @Override
    public int toRawActivityKind(final ActivityKind activityKind) {
        return activityKind.getCode();
    }

    @Override
    public float normalizeIntensity(final int rawIntensity) {
        return rawIntensity / 100f;
    }

    @Override
    public GarminActivitySample createActivitySample() {
        return new GarminActivitySample();
    }

    @Override
    protected List<GarminActivitySample> getGBActivitySamples(final int timestamp_from, final int timestamp_to) {
        LOG.trace(
                "Getting Garmin activity samples between {} and {}",
                timestamp_from,
                timestamp_to
        );

        final long nanoStart = System.nanoTime();

        // Each Garmin sample contains the cumulative value measured up until that specific timestamp. For example the
        // sample at midnight will actually contain the number of steps taken in the entire previous day.
        // This goes against what Gb expects (each sample actually corresponds to the value at the end of the minute).
        // Therefore, we fetch the data with an offset and then adjust by 1 minute
        final List<GarminActivitySample> samples = fillGaps(
                super.getGBActivitySamples(timestamp_from + 60, timestamp_to + 60),
                timestamp_from + 60,
                timestamp_to + 60
        );

        samples.forEach(s -> s.setTimestamp(s.getTimestamp() - 60));

        if (!samples.isEmpty()) {
            convertCumulativeSteps(samples, GarminActivitySampleDao.Properties.Steps, -60);
        }

        final GarminSleepSessionProvider sleepSessionProvider = new GarminSleepSessionProvider(getDevice(), getSession());

        convertCalories(samples);
        overlaySleep(sleepSessionProvider, samples, timestamp_from, timestamp_to);

        final long nanoEnd = System.nanoTime();

        final long executionTime = (nanoEnd - nanoStart) / 1000000;

        LOG.trace("Getting Garmin samples took {}ms", executionTime);

        return samples;
    }

    /**
     * Converts the calories from kcal to cal
     */
    private void convertCalories(List<GarminActivitySample> samples) {
        for (GarminActivitySample sample : samples) {
            sample.setActiveCalories(sample.getActiveCalories() * 1000);
        }
    }
}
