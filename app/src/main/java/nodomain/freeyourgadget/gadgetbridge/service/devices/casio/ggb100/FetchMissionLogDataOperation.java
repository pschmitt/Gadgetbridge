/*  Copyright (C) 2026 Davide Gessa

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.casio.ggb100;

import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler;
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.Device;
import nodomain.freeyourgadget.gadgetbridge.entities.GenericAltitudeSample;
import nodomain.freeyourgadget.gadgetbridge.entities.User;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.AbstractCasioConvoyFetchOperation;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.Casio2C2DSupport;
import nodomain.freeyourgadget.gadgetbridge.util.BcdUtil;
import nodomain.freeyourgadget.gadgetbridge.util.GB;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryTableRowEntry;
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryValue;

import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.ACTIVE_SECONDS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.ALTITUDE_AVG;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.ALTITUDE_MAX;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.ALTITUDE_MIN;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.ELEVATION_GAIN;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_METERS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_SECONDS;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_MINUTES;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.UNIT_NONE;
import static nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries.GROUP_LAPS;


public class FetchMissionLogDataOperation extends AbstractCasioConvoyFetchOperation<CasioGGB100DeviceSupport> {
    private static final Logger LOG = LoggerFactory.getLogger(FetchMissionLogDataOperation.class);
    private static final int MISSION_LOG_LENGTH = 294;
    private static final int SERIES_SAMPLES = 60;
    private static final int SERIES_INTERVAL_SECONDS = 120;
    private static final int RECORD_COUNT = 14;
    private static final int RECORD_STRIDE = 12;
    private static final int RECORDS_OFFSET = 126;
    private static final short NO_DATA_ALTITUDE = 0x7fff;
    private static final long MAX_CHAIN_GAP_SECONDS = 12 * 3600;

    // NEW_DATA (0x37) flags
    public static final int NEW_DATA_UNKNOWN = -1;
    public static final int NEW_DATA_START = 0x01;
    public static final int NEW_DATA_GOAL = 0x03;

    public FetchMissionLogDataOperation(CasioGGB100DeviceSupport support) {
        super(support, Casio2C2DSupport.FEATURE_MISSION_LOG_DATA, MISSION_LOG_LENGTH,
                R.string.busy_task_fetch_activity_data);
    }

    @Override
    protected void parsePayload(byte[] data) {
        parseMissionLog(getSupport(), data, NEW_DATA_UNKNOWN);
    }

    public static void parseMissionLog(Casio2C2DSupport support, byte[] data, int newDataFlags) {
        if (data.length < MISSION_LOG_LENGTH) {
            LOG.warn("Mission log too short: {} bytes", data.length);
            return;
        }

        parseAltitudeRecords(support, data);
        parseAndSaveSeries(support, data, newDataFlags);
    }

    private static void parseAltitudeRecords(Casio2C2DSupport support, byte[] data) {
        LOG.info("Parsing altitude records from mission log");
        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            final DaoSession session = dbHandler.getDaoSession();
            final Device device = DBHelper.getDevice(support.getDevice(), session);
            final User user = DBHelper.getUser(session);

            int stored = 0;
            int skipped = 0;
            for (int i = 0; i < RECORD_COUNT; i++) {
                final int base = RECORDS_OFFSET + i * RECORD_STRIDE;
                if (data[base + 2] == (byte) 0xff) { skipped++; continue; } // empty slot

                final short alt = (short) ((data[base] & 0xff) | (data[base + 1] << 8));
                if (alt == -1) { skipped++; continue; } // measurement failed (0xffff)

                final Date ts = parseBcdUtc(data, base + 2, true);

                // Records are not cleared by ACK, so using insertOrReplace
                final GenericAltitudeSample sample = new GenericAltitudeSample();
                sample.setTimestamp(ts.getTime());
                sample.setAltitude(alt);
                sample.setDevice(device);
                sample.setUser(user);
                session.getGenericAltitudeSampleDao().insertOrReplace(sample);
                stored++;
                LOG.info("Altitude record {}: {} m at {}", i, alt, ts);
            }
            LOG.info("Altitude records: stored {}, skipped {} (empty or failed)", stored, skipped);
        } catch (Exception e) {
            LOG.info("Error saving altitude records: {}", e);
            GB.toast(support.getContext(), "Error saving altitude records: " + e.getLocalizedMessage(),
                    Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    private static void parseAndSaveSeries(Casio2C2DSupport support, byte[] data, int newDataFlags) {
        if (data[0] == (byte) 0xff) {
            LOG.debug("No altitude series stored");
            return;
        }

        final long windowStartSec = parseBcdUtc(data, 0, false).getTime() / 1000L;
        final int count = Math.min(data[5] & 0xff, SERIES_SAMPLES);

        try (DBHandler dbHandler = GBApplication.acquireDB()) {
            final DaoSession session = dbHandler.getDaoSession();
            final Device device = DBHelper.getDevice(support.getDevice(), session);
            final User user = DBHelper.getUser(session);
            BaseActivitySummary summary = null;
            ActivitySummaryData sd = null;
            long lastTs = -1;
            int prevAlt = Integer.MIN_VALUE;
            int storedRows = 0;

            // If there is an on-going session, we grab it and append on it
            if (newDataFlags != NEW_DATA_START) {
                final List<BaseActivitySummary> latest = session.getBaseActivitySummaryDao()
                        .queryBuilder()
                        .where(
                                BaseActivitySummaryDao.Properties.DeviceId.eq(device.getId()),
                                BaseActivitySummaryDao.Properties.Name.eq("Mission"))
                        .orderDesc(BaseActivitySummaryDao.Properties.StartTime)
                        .limit(1)
                        .list();
                if (!latest.isEmpty()) {
                    final BaseActivitySummary candidate = latest.get(0);
                    final ActivitySummaryData csd = ActivitySummaryData.fromJson(candidate.getSummaryData());
                    final long candidateStartSec = candidate.getStartTime().getTime() / 1000L;
                    long candidateLastTs = candidate.getEndTime().getTime() / 1000L - SERIES_INTERVAL_SECONDS;
                    int candidatePrevAlt = Integer.MIN_VALUE;
                    int candidateRows = 0;
                    while (csd.has("sample_" + (candidateRows + 1))) {
                        candidateRows++;
                    }
                    if (csd.get("sample_" + candidateRows) instanceof ActivitySummaryTableRowEntry row
                            && row.getColumns().size() >= 3
                            && row.getColumns().get(1).value() instanceof Number minutes
                            && row.getColumns().get(2).value() instanceof Number alt) {
                        candidateLastTs = candidateStartSec + (long) (minutes.doubleValue() * 60);
                        candidatePrevAlt = alt.intValue();
                    }
                    if (!csd.has("internalMissionClosed")
                            && windowStartSec >= candidateStartSec
                            && windowStartSec - candidateLastTs <= MAX_CHAIN_GAP_SECONDS) {
                        summary = candidate;
                        sd = csd;
                        lastTs = candidateLastTs;
                        prevAlt = candidatePrevAlt;
                        storedRows = candidateRows;
                    }
                }
            }

            if (summary == null) {
                // This is the case of a redelivery, we need to skip it
                final List<BaseActivitySummary> sameStart = session.getBaseActivitySummaryDao()
                        .queryBuilder()
                        .where(
                                BaseActivitySummaryDao.Properties.DeviceId.eq(device.getId()),
                                BaseActivitySummaryDao.Properties.StartTime.eq(new Date(windowStartSec * 1000L)))
                        .list();
                if (!sameStart.isEmpty()) {
                    LOG.info("Mission at {} already in database - skipping", new Date(windowStartSec * 1000L));
                    return;
                }
                summary = new BaseActivitySummary();
                summary.setName("Mission");
                summary.setStartTime(new Date(windowStartSec * 1000L));
                summary.setActivityKind(ActivityKind.ACTIVITY.getCode());
                summary.setDevice(device);
                summary.setUser(user);
                sd = new ActivitySummaryData();
                sd.add("elevation_header", new ActivitySummaryTableRowEntry(GROUP_LAPS,
                        Arrays.asList(new ActivitySummaryValue("workout_lap"),
                                new ActivitySummaryValue("lap_time"),
                                new ActivitySummaryValue("Elevation")),
                        true, true));
            }

            final long baseTs = summary.getStartTime().getTime() / 1000L;
            int min = Integer.MAX_VALUE;
            int max = Integer.MIN_VALUE;
            long sum = 0;
            int elevationGain = 0;
            int added = 0;

            for (int i = 0; i < count; i++) {
                final short alt = (short) ((data[6 + i * 2] & 0xff) | (data[6 + i * 2 + 1] << 8));
                if (alt == NO_DATA_ALTITUDE) continue;
                final long ts = windowStartSec + (long) i * SERIES_INTERVAL_SECONDS;
                if (ts <= lastTs) continue;

                min = Math.min(min, alt);
                max = Math.max(max, alt);
                sum += alt;
                if (prevAlt != Integer.MIN_VALUE && alt > prevAlt) {
                    elevationGain += alt - prevAlt;
                }
                prevAlt = alt;
                added++;
                lastTs = ts;
                sd.add("sample_" + (storedRows + added), new ActivitySummaryTableRowEntry(GROUP_LAPS,
                        Arrays.asList(new ActivitySummaryValue(storedRows + added, UNIT_NONE),
                                new ActivitySummaryValue((ts - baseTs) / 60, UNIT_MINUTES),
                                new ActivitySummaryValue(alt, UNIT_METERS)),
                        false, true));
            }

            if (added == 0 && (newDataFlags != NEW_DATA_GOAL || storedRows == 0)) {
                LOG.debug("No new samples in this window");
                return;
            }

            if (added > 0) {
                if (storedRows > 0) {
                    min = Math.min(min, sd.getNumber(ALTITUDE_MIN, min).intValue());
                    max = Math.max(max, sd.getNumber(ALTITUDE_MAX, max).intValue());
                    elevationGain += sd.getNumber(ELEVATION_GAIN, 0).intValue();
                    sum += (long) (sd.getNumber(ALTITUDE_AVG, 0).doubleValue() * storedRows);
                }
                final int total = storedRows + added;
                sd.add(ACTIVE_SECONDS, (int) (lastTs - baseTs) + SERIES_INTERVAL_SECONDS, UNIT_SECONDS);
                sd.add(ALTITUDE_MIN, min, UNIT_METERS);
                sd.add(ALTITUDE_MAX, max, UNIT_METERS);
                sd.add(ALTITUDE_AVG, sum / (double) total, UNIT_METERS);
                if (elevationGain > 0) {
                    sd.add(ELEVATION_GAIN, elevationGain, UNIT_METERS);
                }
                summary.setEndTime(new Date((lastTs + SERIES_INTERVAL_SECONDS) * 1000L));
            }
            if (newDataFlags == NEW_DATA_GOAL) {
                sd.add("internalMissionClosed", "true");
            }

            summary.setSummaryData(sd.toString());
            session.getBaseActivitySummaryDao().insertOrReplace(summary);
            LOG.info("Saved mission starting {}: +{} samples ({} total), min={} max={}",
                    summary.getStartTime(), added, storedRows + added, min, max);
        } catch (Exception e) {
            GB.toast(support.getContext(), "Error saving mission log: " + e.getLocalizedMessage(),
                    Toast.LENGTH_LONG, GB.ERROR, e);
        }
    }

    /** BCD timestamp, yy mm dd hh mm [ss], UTC */
    private static Date parseBcdUtc(byte[] p, int offset, boolean withSeconds) {
        final int year = 2000 + BcdUtil.fromBcd8(p[offset]);
        final int month = BcdUtil.fromBcd8(p[offset + 1]) - 1; // 0-based
        final int day = BcdUtil.fromBcd8(p[offset + 2]);
        final int hour = BcdUtil.fromBcd8(p[offset + 3]);
        final int min = BcdUtil.fromBcd8(p[offset + 4]);
        final int sec = withSeconds ? BcdUtil.fromBcd8(p[offset + 5]) : 0;
        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(year, month, day, hour, min, sec);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }
}
