/*  Copyright (C) 2023-2024 José Rebelo

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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import java.util.Collections;
import java.util.Date;
import java.util.List;

import de.greenrobot.dao.AbstractDao;
import de.greenrobot.dao.Property;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.devices.AbstractSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.devices.GenericDurationSampleProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericDurationSample;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.entities.XiaomiSleepStageSample;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.util.RangeMap;

public class XiaomiSampleProvider extends AbstractSampleProvider<XiaomiActivitySample> {

    /** {@link GenericDurationSample} type of a span a Sleep as Android synthetic workout covered. */
    public static final int DURATION_TYPE_SLEEP_AS_ANDROID = 1;

    public XiaomiSampleProvider(final GBDevice device, final DaoSession session) {
        super(device, session);
    }

    @Override
    public AbstractDao<XiaomiActivitySample, ?> getSampleDao() {
        return getSession().getXiaomiActivitySampleDao();
    }

    @Nullable
    @Override
    protected Property getRawKindSampleProperty() {
        return XiaomiActivitySampleDao.Properties.RawKind;
    }

    @NonNull
    @Override
    protected Property getTimestampSampleProperty() {
        return XiaomiActivitySampleDao.Properties.Timestamp;
    }

    @NonNull
    @Override
    protected Property getDeviceIdentifierSampleProperty() {
        return XiaomiActivitySampleDao.Properties.DeviceId;
    }

    @Override
    public ActivityKind normalizeType(final int rawType) {
        // TODO
        return ActivityKind.fromCode(rawType);
    }

    @Override
    public int toRawActivityKind(final ActivityKind activityKind) {
        // TODO
        return activityKind.getCode();
    }

    @Override
    public float normalizeIntensity(final int rawIntensity) {
        return rawIntensity / 100f;
    }

    @Override
    public XiaomiActivitySample createActivitySample() {
        return new XiaomiActivitySample();
    }

    @Override
    protected List<XiaomiActivitySample> getGBActivitySamples(final int timestamp_from, final int timestamp_to) {
        final List<XiaomiActivitySample> samples = super.getGBActivitySamples(timestamp_from, timestamp_to);

        convertCalories(samples);
        dropSleepAsAndroidCalories(samples, timestamp_from, timestamp_to);
        final XiaomiSleepSessionProvider sleepSessionProvider = new XiaomiSleepSessionProvider(getDevice(), getSession());
        overlaySleep(sleepSessionProvider, samples, timestamp_from, timestamp_to);

        return samples;
    }

    private void convertCalories(final List<XiaomiActivitySample> samples) {
        for (XiaomiActivitySample sample : samples) {
            sample.setActiveCalories(sample.getActiveCalories() * 1000);
        }
    }

    /**
     * Sleep as Android needs the band's raw accelerometer stream, which only runs while a workout
     * is open, so Gadgetbridge opens a synthetic one for the night. The band bills those minutes as
     * exercise at a rate its sport type fixes, regardless of heart rate or movement, and the
     * per-minute figures it later hands over carry that bill. Nobody burnt them, so they read as
     * zero.
     */
    private void dropSleepAsAndroidCalories(final List<XiaomiActivitySample> samples,
                                            final int timestamp_from,
                                            final int timestamp_to) {
        final List<GenericDurationSample> windows = new GenericDurationSampleProvider(getDevice(), getSession())
                .getSamplesOverlapping(DURATION_TYPE_SLEEP_AS_ANDROID, timestamp_from * 1000L, timestamp_to * 1000L);
        if (windows.isEmpty()) {
            return;
        }

        final RangeMap<Long, Boolean> synthetic = new RangeMap<>(RangeMap.Mode.LOWER_BOUND);
        for (final GenericDurationSample window : windows) {
            synthetic.put(window.getTimestamp(), Boolean.TRUE);
            synthetic.put(window.getEndTimestamp() + 1, Boolean.FALSE);
        }

        // A window can outlast the sleep it covered: a session the band never closed is only closed
        // on the next connection, which can be hours later. A workout the wearer really did in the
        // meantime keeps the calories it earned.
        final RangeMap<Long, Boolean> workouts = new RangeMap<>(RangeMap.Mode.LOWER_BOUND);
        for (final BaseActivitySummary workout : workoutsOverlapping(timestamp_from, timestamp_to)) {
            workouts.put(workout.getStartTime().getTime(), Boolean.TRUE);
            workouts.put(workout.getEndTime().getTime() + 1, Boolean.FALSE);
        }

        for (final XiaomiActivitySample sample : samples) {
            final long timestamp = sample.getTimestamp() * 1000L;
            if (Boolean.TRUE.equals(synthetic.get(timestamp))
                    && !Boolean.TRUE.equals(workouts.get(timestamp))) {
                sample.setActiveCalories(0);
            }
        }
    }

    private List<BaseActivitySummary> workoutsOverlapping(final int timestamp_from, final int timestamp_to) {
        final Device device = DBHelper.findDevice(getDevice(), getSession());
        if (device == null) {
            return Collections.emptyList();
        }

        return getSession().getBaseActivitySummaryDao().queryBuilder()
                .where(
                        BaseActivitySummaryDao.Properties.DeviceId.eq(device.getId()),
                        BaseActivitySummaryDao.Properties.StartTime.le(new Date(timestamp_to * 1000L)),
                        BaseActivitySummaryDao.Properties.EndTime.ge(new Date(timestamp_from * 1000L))
                )
                .build().list();
    }

    /**
     * See {@link nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.activity.impl.SleepDetailsParser}
     */
    public static ActivityKind getActivityKindForSample(final XiaomiSleepStageSample sample) {
        return switch (sample.getStage()) {
            case 2 -> ActivityKind.DEEP_SLEEP;
            case 3 -> ActivityKind.LIGHT_SLEEP;
            case 4 -> ActivityKind.REM_SLEEP;
            case 5 -> ActivityKind.AWAKE_SLEEP;
            default -> ActivityKind.UNKNOWN;
        };
    }
}
