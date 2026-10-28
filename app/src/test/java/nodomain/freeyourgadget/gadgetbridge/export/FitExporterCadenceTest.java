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
package nodomain.freeyourgadget.gadgetbridge.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.FitFile;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordData;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitLap;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitRecord;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages.FitSession;

/**
 * Track and summary cadence are steps/min; the exported FIT counts step sports per leg
 * (strides/min, total_cycles = strides) and keeps rpm for cycling.
 */
public class FitExporterCadenceTest {
    private static final long START = 1777656743L;
    private static final long ELAPSED = 60L;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void running_isWrittenPerLeg() throws Exception {
        final ActivitySummaryData data = new ActivitySummaryData();
        data.add(ActivitySummaryEntries.CADENCE_AVG, 163, ActivitySummaryEntries.UNIT_SPM);
        data.add(ActivitySummaryEntries.CADENCE_MAX, 176, ActivitySummaryEntries.UNIT_SPM);
        data.add(ActivitySummaryEntries.STEPS, 9785, ActivitySummaryEntries.UNIT_STEPS);

        final FitFile fit = export(ActivityKind.TREADMILL, 164, data);

        final List<FitRecord> records = records(fit);
        assertEquals(Integer.valueOf(82), records.get(0).getCadence());
        assertNull(records.get(0).getFractionalCadence());
        assertEquals(Integer.valueOf(82), records.get(1).getCadence());
        assertEquals(0.5f, records.get(1).getFractionalCadence(), 0.01f);

        final FitSession session = onlySession(fit);
        assertEquals(Integer.valueOf(81), session.getAvgCadence());
        assertEquals(0.5f, session.getAvgFractionalCadence(), 0.01f);
        assertEquals(Integer.valueOf(88), session.getMaxCadence());
        assertNull(session.getMaxFractionalCadence());
        assertEquals(Long.valueOf(4892), session.getTotalCycles());
        assertEquals(0.5f, session.getTotalFractionalCycles(), 0.01f);

        final FitLap lap = onlyLap(fit);
        assertEquals(Integer.valueOf(81), lap.getAvgCadence());
        assertEquals(Integer.valueOf(88), lap.getMaxCadence());
    }

    @Test
    public void cycling_isKept() throws Exception {
        final ActivitySummaryData data = new ActivitySummaryData();
        data.add(ActivitySummaryEntries.CADENCE_AVG, 85, ActivitySummaryEntries.UNIT_REVS_PER_MINUTE);

        final FitFile fit = export(ActivityKind.CYCLING, 85, data);

        final FitRecord record = records(fit).get(1);
        assertEquals(Integer.valueOf(86), record.getCadence());
        assertNull(record.getFractionalCadence());
        assertEquals(Integer.valueOf(85), onlySession(fit).getAvgCadence());
        assertNull(onlySession(fit).getAvgFractionalCadence());
    }

    /** Exports one point per second, alternating {@code cadence} and {@code cadence + 1}. */
    private FitFile export(final ActivityKind kind, final int cadence, final ActivitySummaryData data) throws Exception {
        final BaseActivitySummary summary = new BaseActivitySummary();
        summary.setStartTime(new Date(START * 1000L));
        summary.setEndTime(new Date((START + ELAPSED) * 1000L));
        summary.setActivityKind(kind.getCode());

        final ActivityTrack track = new ActivityTrack();
        for (int i = 0; i < ELAPSED; i++) {
            final ActivityPoint p = new ActivityPoint(new Date((START + i) * 1000L));
            p.setHeartRate(150);
            p.setCadence(cadence + i % 2);
            track.addTrackPoint(p);
        }

        final File out = tmp.newFile(kind.name() + ".fit");
        new FitExporter().performExport(track, summary, data, out);
        return FitFile.parseIncoming(out);
    }

    private static List<FitRecord> records(final FitFile fit) {
        final List<FitRecord> out = new ArrayList<>();
        for (final RecordData r : fit.getRecords()) {
            if (r instanceof FitRecord) out.add((FitRecord) r);
        }
        assertEquals(ELAPSED, out.size());
        return out;
    }

    private static FitSession onlySession(final FitFile fit) {
        FitSession s = null;
        for (final RecordData r : fit.getRecords()) {
            if (r instanceof FitSession) s = (FitSession) r;
        }
        assertNotNull("no session record", s);
        return s;
    }

    private static FitLap onlyLap(final FitFile fit) {
        final List<FitLap> out = new ArrayList<>();
        for (final RecordData r : fit.getRecords()) {
            if (r instanceof FitLap) out.add((FitLap) r);
        }
        assertEquals(1, out.size());
        return out.get(0);
    }
}
