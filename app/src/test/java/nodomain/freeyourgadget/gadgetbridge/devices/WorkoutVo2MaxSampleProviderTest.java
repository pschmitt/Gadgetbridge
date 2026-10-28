/*  Copyright (C) 2026 Ludwig Ettner

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
package nodomain.freeyourgadget.gadgetbridge.devices;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import java.util.Base64;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType;
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

/**
 * Xiaomi devices persist only the raw summary bytes, and no summary json or one with just the
 * GPS flag, so the VO2 max is only known after re-parsing them.
 */
public class WorkoutVo2MaxSampleProviderTest extends TestBase {
    /** Mi Watch Color Sport outdoor walking v1 summary v3, 3 Oct 2026 - VO2 max 49. */
    private static final String WALKING_VO2MAX_49 =
            "yefAaggDiQD////wyefAamoLwWqeIwAALyMAALcBJAMAALUEAAD4RBNBFTkAAKgAVn43m5mIQzYzVUO9UNxDZoYF"
                    + "RACAnEMzMzM/MQ8AAAAAAAAAAAAAAAAAAJUAAAD7BQAAbgIAAAAAAAAAAOLlh3k=";

    /** Mi Watch Color Sport outdoor cycling v2 summary v3, 4 Oct 2026 - VO2 max 50. */
    private static final String CYCLING_VO2MAX_50 =
            "oTrCaggDmQD////AoTrCavBgwmpmGQAAqkYAAEoCxwAAAMwCAACLKwtCesY7nNnmQzUz4kPZ5u5DZmYYRGaml0MA"
                    + "AEBAMhceAFUAAAAiAQAAEwYAAFQHAACnBwAA2QIAAAAAAAAAAOyp/+M=";

    private static final String GPS_ONLY_JSON;

    static {
        final ActivitySummaryData gpsOnly = new ActivitySummaryData();
        gpsOnly.setHasGps(true);
        GPS_ONLY_JSON = gpsOnly.toString();
    }

    private static final long WALK_START = 1791027145000L;
    private static final long RIDE_START = 1791113889000L;

    private GBDevice device;
    private WorkoutVo2MaxSampleProvider provider;

    @Before
    public void setUpProvider() {
        device = new GBDevice("00:00:00:00:00:22", "Mi Watch Color Sport", null, null, DeviceType.MIWATCHCOLORSPORT);
        final Device dbDevice = DBHelper.getDevice(device, daoSession);
        final User user = DBHelper.getUser(daoSession);

        insertSummary(dbDevice, user, WALK_START, ActivityKind.WALKING, WALKING_VO2MAX_49, null);
        // WorkoutGpsParser stores a json holding only the GPS flag
        insertSummary(dbDevice, user, RIDE_START, ActivityKind.OUTDOOR_CYCLING, CYCLING_VO2MAX_50, GPS_ONLY_JSON);
        // A later workout without raw data, and so without a VO2 max
        insertSummary(dbDevice, user, RIDE_START + 86_400_000L, ActivityKind.EXERCISE, null, GPS_ONLY_JSON);

        provider = new WorkoutVo2MaxSampleProvider(device, daoSession);
    }

    private void insertSummary(final Device dbDevice, final User user, final long start,
                               final ActivityKind kind, final String rawBase64, final String json) {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setDevice(dbDevice);
        summary.setUser(user);
        summary.setStartTime(new Date(start));
        summary.setEndTime(new Date(start));
        summary.setActivityKind(kind.getCode());
        if (rawBase64 != null) {
            summary.setRawSummaryData(Base64.getDecoder().decode(rawBase64));
        }
        summary.setSummaryData(json);
        daoSession.getBaseActivitySummaryDao().insert(summary);
    }

    @Test
    public void findsVo2MaxInSummariesWithoutJson() {
        final List<Vo2MaxSample> samples = provider.getAllSamples(0, Long.MAX_VALUE);
        assertEquals(2, samples.size());
        assertEquals(49f, samples.get(0).getValue(), 0f);
        assertEquals(50f, samples.get(1).getValue(), 0f);
        assertEquals(Vo2MaxSample.Type.CYCLING, samples.get(1).getType());
    }

    @Test
    public void latestSampleSkipsWorkoutsWithoutVo2Max() {
        final Vo2MaxSample latest = provider.getLatestSample();
        assertNotNull(latest);
        assertEquals(RIDE_START, latest.getTimestamp());
        assertEquals(50f, latest.getValue(), 0f);

        final Vo2MaxSample beforeRide = provider.getLatestSample(Vo2MaxSample.Type.ANY, RIDE_START - 1);
        assertNotNull(beforeRide);
        assertEquals(49f, beforeRide.getValue(), 0f);

        assertNull(provider.getLatestSample(Vo2MaxSample.Type.ANY, WALK_START - 1));
    }

    @Test
    public void firstSample() {
        final Vo2MaxSample first = provider.getFirstSample();
        assertNotNull(first);
        assertEquals(WALK_START, first.getTimestamp());
    }

    @Test
    public void reparsedJsonIsNotPersisted() {
        provider.getAllSamples(0, Long.MAX_VALUE);
        provider.getLatestSample();
        provider.getFirstSample();
        daoSession.clear();
        for (final BaseActivitySummary summary : daoSession.getBaseActivitySummaryDao().loadAll()) {
            final String json = summary.getSummaryData();
            if (summary.getStartTime().getTime() == WALK_START) {
                assertNull(json);
            } else {
                assertEquals(GPS_ONLY_JSON, json);
            }
        }
    }
}
