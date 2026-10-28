/*  Copyright (C) 2026 Manuel Marcatili

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.messages;

import androidx.annotation.Nullable;

import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordDefinition;
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.RecordHeader;

/**
 * One minute of raw sleep data, sent by watches that leave sleep staging to Garmin's servers
 * (#4048). The row packs ten little-endian half-precision floats. Which of them carry the
 * heart rate and the movement was worked out from real files of such a watch.
 */
public class FitSleepDataRaw extends AbstractFitSleepDataRaw {
    static final int FEATURE_COUNT = 10;
    static final int MOVEMENT_INDEX = 8;
    static final int HEART_RATE_INDEX = 9;

    private static final float MIN_HEART_RATE = 25f;
    private static final float MAX_HEART_RATE = 250f;

    public FitSleepDataRaw(final RecordDefinition recordDefinition, final RecordHeader recordHeader) {
        super(recordDefinition, recordHeader);
    }

    /**
     * The ten features of this minute, in file order, or null when the row does not hold
     * exactly ten.
     */
    @Nullable
    public float[] getFeatures() {
        return decodeFeatures(getFieldByNumber(0));
    }

    /**
     * Beats per minute, or null when the row holds no plausible heart rate.
     */
    @Nullable
    public Float getHeartRate() {
        return heartRateOf(getFeatures());
    }

    /**
     * Movement during the minute, zero when still, or null when the row holds none.
     */
    @Nullable
    public Float getMovement() {
        return movementOf(getFeatures());
    }

    @Nullable
    static float[] decodeFeatures(@Nullable final Object field) {
        if (!(field instanceof Object[] bytes) || bytes.length != FEATURE_COUNT * 2) {
            return null;
        }
        final float[] features = new float[FEATURE_COUNT];
        for (int i = 0; i < FEATURE_COUNT; i++) {
            features[i] = halfToFloat(byteAt(bytes, 2 * i) | (byteAt(bytes, 2 * i + 1) << 8));
        }
        return features;
    }

    @Nullable
    static Float heartRateOf(@Nullable final float[] features) {
        if (features == null) {
            return null;
        }
        final float heartRate = features[HEART_RATE_INDEX];
        return heartRate >= MIN_HEART_RATE && heartRate <= MAX_HEART_RATE ? heartRate : null;
    }

    @Nullable
    static Float movementOf(@Nullable final float[] features) {
        if (features == null) {
            return null;
        }
        final float movement = features[MOVEMENT_INDEX];
        return Float.isFinite(movement) && movement >= 0 ? movement : null;
    }

    /**
     * The byte base type decodes 0xFF as an invalid value, but here it is just one byte of a float.
     */
    private static int byteAt(final Object[] bytes, final int index) {
        final Object value = bytes[index];
        return value instanceof Number number ? number.intValue() & 0xFF : 0xFF;
    }

    /**
     * IEEE 754 half precision to float.
     */
    static float halfToFloat(final int bits) {
        final int sign = (bits & 0x8000) << 16;
        final int exponent = (bits >>> 10) & 0x1F;
        final int mantissa = bits & 0x3FF;
        if (exponent == 0) {
            // Zero or subnormal: mantissa * 2^-24
            final float value = mantissa * 5.9604645E-8f;
            return sign != 0 ? -value : value;
        }
        if (exponent == 0x1F) {
            if (mantissa != 0) {
                return Float.NaN;
            }
            return sign != 0 ? Float.NEGATIVE_INFINITY : Float.POSITIVE_INFINITY;
        }
        // Rebias the exponent from 15 to 127
        return Float.intBitsToFloat(sign | ((exponent + 112) << 23) | (mantissa << 13));
    }
}
