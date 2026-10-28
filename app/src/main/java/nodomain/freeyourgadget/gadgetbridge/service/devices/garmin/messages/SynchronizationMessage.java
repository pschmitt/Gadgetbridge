/*  Copyright (C) 2024-2026 Daniele Gobbetti, Thomas Kuehne

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.messages;

import org.apache.commons.lang3.EnumUtils;

import java.util.EnumSet;


public class SynchronizationMessage extends GFDIMessage {
    private final SynchronizationType synchronizationType;
    private final EnumSet<FileType> syncBitmask;

    public SynchronizationMessage(GarminMessage garminMessage, SynchronizationType synchronizationType, EnumSet<FileType> bitmask) {
        this.garminMessage = garminMessage;
        this.synchronizationType = synchronizationType;
        this.syncBitmask = bitmask;

        this.statusMessage = super.getStatusMessage();

        LOG.debug("type: {}, bitmask: {}", synchronizationType, bitmask);
    }

    public static SynchronizationMessage parseIncoming(MessageReader reader, GarminMessage garminMessage) {
        final int type = reader.readByte();
        final SynchronizationType synchronizationType = SynchronizationType.fromCode(type);
        final int size = reader.readByte();
        final long bitmask;
        if (size == 8) {
            bitmask = reader.readLong();
        } else if (size == 4) {
            bitmask = reader.readInt();
        } else {
            LOG.warn("SynchronizationMessage bitmask size unexpected, was: {}", size);
            return null;
        }
        final EnumSet<FileType> syncBitmask = EnumUtils.processBitVector(FileType.class, bitmask);
        return new SynchronizationMessage(garminMessage, synchronizationType, syncBitmask);
    }

    public boolean shouldProceed() {
        return syncBitmask.contains(FileType.WORKOUTS) || syncBitmask.contains(FileType.ACTIVITIES)
                || syncBitmask.contains(FileType.ACTIVITY_SUMMARY) || syncBitmask.contains(FileType.SLEEP)
                || synchronizationType == SynchronizationType.MANUAL;
    }

    @Override
    protected boolean generateOutgoing() {
        return false;
    }

    public enum SynchronizationType {
        MANUAL,
        AUTOMATIC,
        TYPE_2,
        ;

        public static SynchronizationType fromCode(final int code) {
            for (final SynchronizationType type : SynchronizationType.values()) {
                if (type.ordinal() == code) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown synchronization type " + code);
        }
    }

    public enum FileType {
        SCHEDULES,
        SETTINGS,
        GOALS,
        WORKOUTS,
        COURSES,
        ACTIVITIES,
        PERSONAL_RECORDS,
        unk_7,
        SOFTWARE_UPDATE,
        DEVICE_SETTINGS,
        LANGUAGE_SETTINGS,
        USER_PROFILE,
        SPORTS,
        SEGMENT_LEADERS,
        GOLF_CLUB,
        WELLNESS_DEVICE_INFO,
        WELLNESS_DEVICE_CCF,
        INSTALL_APP,
        CHECK_BACK,
        TRUE_UP,
        SETTINGS_CHANGE,
        ACTIVITY_SUMMARY,
        METRICS_FILE,
        PACE_BAND,
        SPORT_BACKUP,
        ULF_CONFIG,
        SLEEP,
        EXERCISE_BENCHMARK,
        POWER_GUIDANCE,
        SPORTING_EVENT,
        ACTUAL_STEP_RECORDING_CONFIGURATION,
        LHA_BACKUP,
        BBI_RECORDING_CONFIGURATION,
        unk_33,
        unk_34,
        unk_35,
        HRV_STATUS,
        unk_37,
        unk_38,
        UNINSTALL_APP,
        unk_40,
        unk_41,
        unk_42,
        unk_43,
        unk_44,
        unk_45,
        unk_46,
        unk_47,
        unk_48,
        unk_49,
        unk_50,
        unk_51,
        unk_52,
        unk_53,
        unk_54,
        unk_55,
        unk_56,
        unk_57,
        unk_58,
        unk_59,
        unk_60,
        unk_61,
        unk_62,
        unk_63,
        ;
    }
}
