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
package nodomain.freeyourgadget.gadgetbridge.service.devices.oppo.commands;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry;

public enum AncConfigValue implements LabeledEntry {
    OFF(0x01, R.string.off, R.drawable.ic_hearing_disabled),
    TRANSPARENCY(0x02, R.string.prefs_active_noise_cancelling_transparency, R.drawable.ic_air),
    ON(0x08, R.string.prefs_active_noise_cancelling, R.string.sony_ambient_sound_noise_cancelling_short, R.drawable.ic_noise_control_on),
    ;

    private final int code;
    private final int label;
    private final int shortLabel;
    private final int icon;

    AncConfigValue(final int code, @StringRes final int label, @StringRes final int shortLabel, @DrawableRes int icon) {
        this.code = code;
        this.label = label;
        this.shortLabel = shortLabel;
        this.icon = icon;
    }

    AncConfigValue(final int code, @StringRes final int label, @DrawableRes int icon) {
        this.code = code;
        this.label = label;
        this.shortLabel = label;
        this.icon = icon;
    }

    @Nullable
    public static AncConfigValue fromCode(final int code) {
        for (final AncConfigValue param : AncConfigValue.values()) {
            if (param.getCode() == code) {
                return param;
            }
        }

        return null;
    }

    public static EnumSet<AncConfigValue> fromMask(int mask) {
        EnumSet<AncConfigValue> modes = EnumSet.noneOf(AncConfigValue.class);
        for (AncConfigValue mode : AncConfigValue.values()) {
            if ((mask & mode.getCode()) == mode.getCode()) {
                modes.add(mode);
            }
        }

        return modes;
    }

    @Nullable
    public static AncConfigValue fromPrefId(final String value) {
        for (final AncConfigValue param : AncConfigValue.values()) {
            if (param.name().equalsIgnoreCase(value)) {
                return param;
            }
        }

        return null;
    }

    public static EnumSet<AncConfigValue> fromPrefIds(@NonNull Set<String> values) {
        EnumSet<AncConfigValue> result = EnumSet.noneOf(AncConfigValue.class);
        for (String value : values) {
            AncConfigValue mode = fromPrefId(value);
            if (mode != null) {
                result.add(mode);
            }
        }
        return result;
    }

    public static Set<String> toPrefIds(final EnumSet<AncConfigValue> values) {
        return values.stream()
            .map(e -> e.name().toLowerCase())
            .collect(Collectors.toSet());
    }

    public static int toMask(Iterable<AncConfigValue> modes) {
        int mask = 0;
        for (AncConfigValue mode : modes) {
            mask |= mode.getCode();
        }
        return mask;
    }

    @Override
    @StringRes
    public int getLabel() {
        return label;
    }

    @StringRes
    public int getShortLabel() {
        return shortLabel;
    }

    @DrawableRes
    public int getIcon() {
        return icon;
    }

    public int getCode() {
        return code;
    }

    public String getPrefId() {
        return this.name().toLowerCase();
    }

    public enum Level implements LabeledEntry {
        HIGH(0x08, R.string.prefs_active_noise_cancelling_level_high),
        NORMAL(0x10, R.string.prefs_active_noise_cancelling_level_normal),
        LOW(0x04, R.string.prefs_active_noise_cancelling_level_low),
        DYNAMIC(0x20, R.string.prefs_active_noise_cancelling_level_dynamic),
        ;

        private final int code;
        private final int label;

        Level(final int code, @StringRes final int label) {
            this.code = code;
            this.label = label;
        }

        @Nullable
        public static Level fromCode(final int code) {
            for (final Level param : Level.values()) {
                if (param.getCode() == code) {
                    return param;
                }
            }

            return null;
        }

        @Nullable
        public static Level fromPrefId(final String value) {
            for (final Level param : Level.values()) {
                if (param.name().equalsIgnoreCase(value)) {
                    return param;
                }
            }

            return null;
        }

        @Override
        @StringRes
        public int getLabel() {
            return label;
        }

        public int getCode() {
            return code;
        }

        public String getPrefId() {
            return this.name().toLowerCase();
        }
    }
}
