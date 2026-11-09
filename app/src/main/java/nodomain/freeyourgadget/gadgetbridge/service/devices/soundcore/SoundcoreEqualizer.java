/*  Copyright (C) 2026 David Giron

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
package nodomain.freeyourgadget.gadgetbridge.service.devices.soundcore;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import java.util.Arrays;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst;
import nodomain.freeyourgadget.gadgetbridge.util.StringUtils;

public final class SoundcoreEqualizer {
    public static final String[] EQUALIZER_PREFS_VALUE = new String[]{
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND1_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND2_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND3_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND4_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND5_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND6_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND7_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND8_VALUE,
            DeviceSettingsPreferenceConst.PREF_SOUNDCORE_EQUALIZER_BAND9_VALUE,
    };

    private static final int[] CUSTOM_TAIL_SCALE = new int[]{5, 3, 4, 4, 4, 4, 4, 6};
    private static final int CUSTOM_TAIL_DIVISOR = 6;

    private static final String PRESET_CHANNEL_SEPARATOR = "00";
    private static final String PRESET_SUFFIX = "0000";
    private static final String PRESET_COMMON_REPEATED_BLOCK = "9fa18a998a8077643c3c";
    private static final String PRESET_COMMON_PREFIX = "00000200";
    private static final String PRESET_COMMON_MIDDLE = "69d64e6a00";
    private static final String PRESET_COMMON_SECTION =
        PRESET_COMMON_PREFIX
            + PRESET_COMMON_REPEATED_BLOCK
            + PRESET_COMMON_REPEATED_BLOCK
            + PRESET_COMMON_MIDDLE
            + PRESET_COMMON_REPEATED_BLOCK
            + PRESET_COMMON_REPEATED_BLOCK;

    private final int index;
    @StringRes
    private final int label;
    // Band gains in dB; wire values use 0x78 + 10 * gain.
    @Nullable
    private final int[] bands;
    // Tail offsets use single wire steps, not dB. They are not a per-band gain transform.
    @Nullable
    private final int[] tails;

    public SoundcoreEqualizer(final int index, @StringRes final int label) {
        this(index, label, null, null);
    }

    public SoundcoreEqualizer(final int index, @StringRes final int label,
                              @Nullable final int[] bands, @Nullable final int[] tails) {
        this.index = index;
        this.label = label;
        this.bands = bands != null ? normalizeBands(bands) : null;
        this.tails = tails != null ? normalizeBands(tails) : null;
    }

    public int getIndex() {
        return index;
    }

    @StringRes
    public int getLabel() {
        return label;
    }

    /** Returns the actual signed dB gains, or null when this preset has no band definition. */
    @Nullable
    public int[] getBands() {
        return bands != null ? bands.clone() : null;
    }

    /** Returns nine signed tail offsets in wire steps, not dB. */
    @Nullable
    public int[] getTails() {
        return tails != null ? tails.clone() : null;
    }

    @Nullable
    public byte[] getEncodedBands() {
        return bands != null ? encodeBands(bands) : null;
    }

    @Nullable
    public byte[] getEncodedTails() {
        return tails != null ? encodeValues(tails, 1) : null;
    }

    // A complete payload requires both gains and explicit tail data.
    @Nullable
    public byte[] getPayload() {
        if (bands == null || tails == null) {
            return null;
        }
        return buildPayload(index, bands, tails);
    }

    @Nullable
    public byte[] getPayloadCompact() {
        if (bands == null || tails == null) {
            return null;
        }
        return buildPayloadCompact(index, bands, tails);
    }

    /** Encodes up to nine signed dB gains, defaulting missing bands to 0 dB. */
    public static byte[] encodeBands(final int[] bands) {
        return encodeValues(bands, 10);
    }

    private static int[] normalizeBands(final int[] values) {
        // copyOf fills missing bands with +0 dB (int). We expect at least 8 bands.
        return Arrays.copyOf(values, 9);
    }

    private static byte[] encodeValues(final int[] values, final int scale) {
        final int[] bands = normalizeBands(values);
        final byte[] encoded = new byte[bands.length];
        for (int i = 0; i < bands.length; i++) {
            final long value = 0x78L + (long) bands[i] * scale;
            if (value < 0 || value > 0xff) {
                throw new IllegalArgumentException("Equalizer band value is outside the wire range");
            }
            encoded[i] = (byte) value;
        }
        return encoded;
    }

    @Nullable
    public static SoundcoreEqualizer fromPayload(final byte[] payload) {
        return fromPayload(payload, 0, 4);
    }

    /**
     * Decodes nine band gains at the supplied offsets, or null if the fields are incomplete.
     * Tail data is not decoded and remains unavailable.
     */
    @Nullable
    public static SoundcoreEqualizer fromPayload(final byte[] payload, final int presetOffset, final int bandsOffset) {
        if (presetOffset < 0 || presetOffset >= payload.length ||
                bandsOffset < 0 || bandsOffset > payload.length - 9) {
            return null;
        }

        final int preset = Byte.toUnsignedInt(payload[presetOffset]);
        final int[] bands = new int[9];
        for (int i = 0; i < bands.length; i++) {
            final int rawBand = Byte.toUnsignedInt(payload[bandsOffset + i]);
            bands[i] = clamp(Math.round((rawBand - 0x78) / 10f), -6, 6);
        }
        final SoundcoreEqualizer knownPreset = SoundcoreEqualizerPresets.fromIndex(preset);
        final int label = knownPreset != null ? knownPreset.getLabel() : R.string.custom;
        return new SoundcoreEqualizer(preset, label, bands, null);
    }

    public static byte[] buildCustomPayload(final int[] bands) {
        return buildCustomPayload(bands, false);
    }

    /** Builds a custom payload from up to nine signed dB gains, padding missing bands with zero. */
    public static byte[] buildCustomPayload(final int[] bands, final boolean compact) {
        final int[] normalizedBands = normalizeBands(bands);

        // Preserve the custom tail approximation; named presets use their explicit tails.
        // The ninth tail has no known coefficient, so it remains neutral.
        final int[] tails = new int[normalizedBands.length];
        for (int i = 0; i < CUSTOM_TAIL_SCALE.length; i++) {
            tails[i] = Math.round((float) (normalizedBands[i] * CUSTOM_TAIL_SCALE[i]) / CUSTOM_TAIL_DIVISOR);
        }

        if (!compact) {
            return buildPayload(SoundcoreEqualizerPresets.CUSTOM.getIndex(), normalizedBands, tails);
        }
        return buildPayloadCompact(SoundcoreEqualizerPresets.CUSTOM.getIndex(), normalizedBands, tails);
    }

    /**
     * Builds a payload from up to nine signed dB gains and signed tail offsets in wire steps.
     * Missing values are padded with zero.
     */
    public static byte[] buildPayload(final int presetId, final int[] bands, final int[] tails) {
        return buildPayload(presetId,
                StringUtils.bytesToHex(encodeBands(bands)),
                StringUtils.bytesToHex(encodeValues(tails, 1)));
    }

    public static byte[] buildPayload(final int presetId, final String bands, final String tails) {
        return StringUtils.hexToBytes(
                toHexByte(presetId)
                        + "000000"
                        + bands
                        + PRESET_CHANNEL_SEPARATOR
                        + bands
                        + PRESET_COMMON_SECTION
                        + tails
                        + PRESET_CHANNEL_SEPARATOR
                        + tails
                        + PRESET_SUFFIX
        );
    }

    /**
     * Builds the compact payload for command 0x8302, not the full payload's 0x8703.
     * Gains are signed dB and tails are explicit signed wire offsets; missing values
     * are padded with zero. Compact custom tails must not use the full payload's
     * per-band approximation, since they can depend on neighboring bands.
     */
    public static byte[] buildPayloadCompact(final int presetId, final int[] bands, final int[] tails) {
        final boolean custom = (presetId & 0xff) == SoundcoreEqualizerPresets.CUSTOM.getIndex();
        return StringUtils.hexToBytes(
                toHexByte(presetId)
                        + (custom ? "fe" : "00")
                        + StringUtils.bytesToHex(encodeBands(bands))
                        + (custom ? "78" : "00")
                        + StringUtils.bytesToHex(encodeValues(tails, 1))
                        + "00"
        );
    }

    private static int clamp(final int value, final int min, final int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static String toHexByte(final int value) {
        final String hex = Integer.toHexString(value & 0xff);
        return hex.length() < 2 ? "0" + hex : hex;
    }
}
