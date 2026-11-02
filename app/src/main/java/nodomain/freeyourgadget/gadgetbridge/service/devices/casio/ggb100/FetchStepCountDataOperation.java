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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Calendar;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.entities.CasioGBX100ActivitySample;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.AbstractCasioConvoyFetchOperation;
import nodomain.freeyourgadget.gadgetbridge.service.devices.casio.Casio2C2DSupport;
import nodomain.freeyourgadget.gadgetbridge.util.GB;


public class FetchStepCountDataOperation extends AbstractCasioConvoyFetchOperation<CasioGGB100DeviceSupport> {
    private static final Logger LOG = LoggerFactory.getLogger(FetchStepCountDataOperation.class);
    private static final int LIFE_LOG_LENGTH = 160;
    private static final int NO_DATA16 = 0xfffe;
    private static final long NO_DATA32 = 0xfffffffeL;

    public FetchStepCountDataOperation(CasioGGB100DeviceSupport support) {
        super(support, Casio2C2DSupport.FEATURE_LIFE_LOG, LIFE_LOG_LENGTH, R.string.busy_task_fetch_steps);
    }

    @Override
    protected void parsePayload(byte[] data) {
        parseLifeLog(getSupport(), data);
    }

    @Override
    protected void onFetchFinished() {
        getSupport().onStepCountFetchFinished();
    }

    public static void parseLifeLog(Casio2C2DSupport support, byte[] data) {
        if (data.length < LIFE_LOG_LENGTH) {
            LOG.warn("Life log too short: {} bytes", data.length);
            return;
        }

        LOG.debug("Life log payload:\n{}", GB.hexdump(data));

        final ArrayList<CasioGBX100ActivitySample> samples = new ArrayList<>();
        final ArrayList<CasioGBX100ActivitySample> binSamples = new ArrayList<>();

        final Calendar now = Calendar.getInstance();

        final Calendar dayStart = (Calendar) now.clone();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        final Calendar hourStart = (Calendar) now.clone();
        hourStart.set(Calendar.MINUTE, 0);
        hourStart.set(Calendar.SECOND, 0);
        hourStart.set(Calendar.MILLISECOND, 0);

        // Hourly bins, most recent first: bin 0 = last completed hour
        for (int i = 0; i < 24; i++) {
            final int steps = (data[i * 2] & 0xff) | ((data[i * 2 + 1] & 0xff) << 8);
            final int kcal = (data[48 + i * 2] & 0xff) | ((data[48 + i * 2 + 1] & 0xff) << 8);
            if (steps == NO_DATA16 && kcal == NO_DATA16) continue;

            final Calendar h = (Calendar) hourStart.clone();
            h.add(Calendar.HOUR, -(i + 1));
            final int ts = (int) (h.getTimeInMillis() / 1000);

            final CasioGBX100ActivitySample sample = new CasioGBX100ActivitySample();
            sample.setSteps(steps == NO_DATA16 ? 0 : steps);
            sample.setCalories(kcal == NO_DATA16 ? 0 : kcal);
            sample.setTimestamp(ts);
            sample.setRawKind(ActivityKind.ACTIVITY.getCode());
            samples.add(sample);
            binSamples.add(sample);
        }

        final long totalSteps = (data[96] & 0xffL) | ((data[97] & 0xffL) << 8)
                | ((data[98] & 0xffL) << 16) | ((data[99] & 0xffL) << 24);
        final long totalKcal = (data[100] & 0xffL) | ((data[101] & 0xffL) << 8)
                | ((data[102] & 0xffL) << 16) | ((data[103] & 0xffL) << 24);

        // Residual steps after bin subtracting; this is for a long term debug
        // of bucket behaviour
        int residualSteps = (int) totalSteps;
        int residualKcal = (int) totalKcal;
        for (final CasioGBX100ActivitySample s : binSamples) {
            residualSteps -= s.getSteps();
            residualKcal -= s.getCalories();
        }

        // Daily totals of previous days, most recent first: slot 0 = yesterday
        // Update the days for only the part not vcovered by bins
        for (int j = 0; j < 7; j++) {
            final int base = 104 + j * 8;
            final long steps = (data[base] & 0xffL) | ((data[base + 1] & 0xffL) << 8)
                    | ((data[base + 2] & 0xffL) << 16) | ((data[base + 3] & 0xffL) << 24);
            final long kcal = (data[base + 4] & 0xffL) | ((data[base + 5] & 0xffL) << 8)
                    | ((data[base + 6] & 0xffL) << 16) | ((data[base + 7] & 0xffL) << 24);
            if (steps == NO_DATA32) continue;

            final Calendar d = (Calendar) dayStart.clone();
            d.add(Calendar.DAY_OF_YEAR, -(j + 1));
            final int ts = (int) (d.getTimeInMillis() / 1000);
            final Calendar e = (Calendar) d.clone();
            e.add(Calendar.DAY_OF_YEAR, 1);
            final int tsEnd = (int) (e.getTimeInMillis() / 1000);

            final CasioGBX100ActivitySample stored = support.getSumWithinRange(ts, tsEnd);
            int fetchedSteps = 0;
            int fetchedKcal = 0;
            for (final CasioGBX100ActivitySample s : binSamples) {
                if (s.getTimestamp() >= ts && s.getTimestamp() <= tsEnd) {
                    fetchedSteps += s.getSteps();
                    fetchedKcal += s.getCalories();
                }
            }

            final int diffSteps = (int) steps - stored.getSteps() - fetchedSteps;
            final int diffKcal = (int) kcal - stored.getCalories() - fetchedKcal;
            if (diffSteps <= 0 && diffKcal <= 0) continue;

            final CasioGBX100ActivitySample sample = new CasioGBX100ActivitySample();
            sample.setSteps(Math.max(diffSteps, 0));
            sample.setCalories(Math.max(diffKcal, 0));
            sample.setTimestamp(ts);
            sample.setRawKind(ActivityKind.ACTIVITY.getCode());
            samples.add(sample);
            if (diffSteps > 0) residualSteps -= diffSteps;
            if (diffKcal > 0) residualKcal -= diffKcal;
        }

        LOG.debug("Life log: rolling24h={} steps / {} kcal, bins={} samples, unplaceable residual={} steps / {} kcal",
                totalSteps, totalKcal, binSamples.size(), residualSteps, residualKcal);

        support.stepCountDataFetched((int) totalSteps, (int) totalKcal, samples);
    }
}
