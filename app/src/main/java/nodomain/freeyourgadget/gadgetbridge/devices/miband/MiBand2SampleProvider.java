/*  Copyright (C) 2016-2024 Andreas Shimokawa, Carsten Pfeiffer, Daniele
    Gobbetti

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
package nodomain.freeyourgadget.gadgetbridge.devices.miband;

import java.util.List;

import de.greenrobot.dao.query.QueryBuilder;
import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst;
import nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiEstimatedSleepSessionProvider;
import nodomain.freeyourgadget.gadgetbridge.entities.DaoSession;
import nodomain.freeyourgadget.gadgetbridge.entities.MiBandActivitySample;
import nodomain.freeyourgadget.gadgetbridge.entities.MiBandActivitySampleDao;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind;

import static nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst.PREF_SLEEP_STAGE_ESTIMATION;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.TYPE_DEEP_SLEEP;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.TYPE_IGNORE;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.TYPE_LIGHT_SLEEP;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.TYPE_NO_CHANGE;
import static nodomain.freeyourgadget.gadgetbridge.devices.huami.HuamiConst.TYPE_UNSET;

public class MiBand2SampleProvider extends AbstractMiBandSampleProvider {
    // Only in-memory, from HuamiEstimatedSleepSessionProvider. The device never reports these.
    private static final int TYPE_CUSTOM_REM_SLEEP = 0x100;
    private static final int TYPE_CUSTOM_AWAKE_SLEEP = 0x101;

    public MiBand2SampleProvider(GBDevice device, DaoSession session) {
        super(device, session);
    }

    @Override
    protected List<MiBandActivitySample> getGBActivitySamples(int timestamp_from, int timestamp_to) {
        final List<MiBandActivitySample> samples = getDeviceSamples(timestamp_from, timestamp_to);
        if (GBApplication.getPrefs().experimentalSettings() && GBApplication.getDevicePrefs(getDevice()).getBoolean(PREF_SLEEP_STAGE_ESTIMATION, false)) {
            // If sleep estimation is enabled, remove device-reported sleep and replace it with the estimates
            for (final MiBandActivitySample sample : samples) {
                if (sample.getRawKind() == TYPE_LIGHT_SLEEP || sample.getRawKind() == TYPE_DEEP_SLEEP) {
                    sample.setRawKind(TYPE_UNSET);
                }
            }
            overlaySleep(new HuamiEstimatedSleepSessionProvider(getDevice(), getSession()), samples, timestamp_from, timestamp_to, true);
        }
        return samples;
    }

    /**
     * The samples with the activity kinds reported by the device, without the overlay of estimated sleep.
     */
    public List<MiBandActivitySample> getDeviceSamples(final int timestamp_from, final int timestamp_to) {
        final List<MiBandActivitySample> samples = super.getGBActivitySamples(timestamp_from, timestamp_to);
        postprocess(samples);
        return samples;
    }

    /**
     * "Temporary" runtime post-processing of activity kinds.
     */
    private void postprocess(List<MiBandActivitySample> samples) {
        if (samples.isEmpty()) {
            return;
        }

        int lastValidKind = determinePreviousValidActivityType(samples.get(0));
        for (MiBandActivitySample sample : samples) {
            int rawKind = sample.getRawKind();
            if (rawKind != TYPE_UNSET) {
                rawKind &= 0xf;
                sample.setRawKind(rawKind);
            }

            switch (rawKind) {
                case TYPE_IGNORE:
                case TYPE_NO_CHANGE:
                    if (lastValidKind != TYPE_UNSET) {
                        sample.setRawKind(lastValidKind);
                    }
                    break;
                default:
                    lastValidKind = rawKind;
                    break;
            }
        }
    }

    private int determinePreviousValidActivityType(MiBandActivitySample sample) {
        QueryBuilder<MiBandActivitySample> qb = getSampleDao().queryBuilder();
        qb.where(MiBandActivitySampleDao.Properties.DeviceId.eq(sample.getDeviceId()),
                MiBandActivitySampleDao.Properties.UserId.eq(sample.getUserId()),
                MiBandActivitySampleDao.Properties.Timestamp.lt(sample.getTimestamp()),
                MiBandActivitySampleDao.Properties.RawKind.notIn(TYPE_NO_CHANGE, TYPE_IGNORE, TYPE_UNSET, 16, 80, 96, 112)); // all I ever had that are 0 when doing &=0xf
        qb.orderDesc(MiBandActivitySampleDao.Properties.Timestamp);
        qb.limit(1);
        List<MiBandActivitySample> result = qb.build().list();
        if (!result.isEmpty()) {
            return result.get(0).getRawKind() & 0xf;
        }
        return TYPE_UNSET;
    }

    @Override
    public ActivityKind normalizeType(int rawType) {
        return switch (rawType) {
            case TYPE_CUSTOM_REM_SLEEP -> ActivityKind.REM_SLEEP;
            case TYPE_CUSTOM_AWAKE_SLEEP -> ActivityKind.AWAKE_SLEEP;
            default -> HuamiConst.toActivityKind(rawType);
        };
    }

    @Override
    public int toRawActivityKind(ActivityKind activityKind) {
        return switch (activityKind) {
            case REM_SLEEP -> TYPE_CUSTOM_REM_SLEEP;
            case AWAKE_SLEEP -> TYPE_CUSTOM_AWAKE_SLEEP;
            default -> HuamiConst.toRawActivityType(activityKind);
        };
    }
}
