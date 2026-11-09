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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.R;

public final class SoundcoreEqualizerPresets {
    public static final SoundcoreEqualizer SIGNATURE = new SoundcoreEqualizer(0x00, R.string.soundcore_equalizer_preset_signature, new int[]{0, 0, 0, 0, 0, 0, 0, 0}, new int[]{0, 0, 0, 0, 0, 0, 0, 0});
    public static final SoundcoreEqualizer ACOUSTIC = new SoundcoreEqualizer(0x01, R.string.soundcore_equalizer_preset_acoustic, new int[]{4, 1, 2, 2, 4, 4, 4, 2}, new int[]{5, -2, 3, 0, 4, 2, 4, 1});
    public static final SoundcoreEqualizer BASS_BOOST = new SoundcoreEqualizer(0x02, R.string.sony_equalizer_preset_bass_boost, new int[]{4, 3, 1, 0, 0, 0, 0, 0}, new int[]{3, 2, 0, 0, 0, 0, 0, 0});
    public static final SoundcoreEqualizer BASS_REDUCER = new SoundcoreEqualizer(0x03, R.string.soundcore_equalizer_preset_bass_reducer, new int[]{-4, -3, -1, 0, 0, 0, 0, 0}, new int[]{-3, -2, 0, 0, 0, 0, 0, 0});
    public static final SoundcoreEqualizer CLASSICAL = new SoundcoreEqualizer(0x04, R.string.nothing_equalizer_classical, new int[]{3, 3, -2, -2, 0, 2, 3, 4}, new int[]{2, 4, -4, -1, 0, 2, 1, 5});
    public static final SoundcoreEqualizer PODCAST = new SoundcoreEqualizer(0x05, R.string.soundcore_equalizer_preset_podcast, new int[]{-3, 2, 4, 4, 3, 2, 0, -2}, new int[]{-4, 3, 2, 3, 1, 2, 0, -3});
    public static final SoundcoreEqualizer DANCE = new SoundcoreEqualizer(0x06, R.string.soundcore_equalizer_preset_dance, new int[]{2, -3, -1, 1, 2, 2, 1, -3}, new int[]{4, -5, 0, 0, 2, 1, 3, -5});
    public static final SoundcoreEqualizer DEEP = new SoundcoreEqualizer(0x07, R.string.soundcore_equalizer_preset_deep, new int[]{2, 1, 3, 3, 2, -2, -4, -5}, new int[]{2, -1, 3, 1, 3, -2, -2, -5});
    public static final SoundcoreEqualizer ELECTRONIC = new SoundcoreEqualizer(0x08, R.string.nothing_equalizer_electronic, new int[]{3, 2, -2, 2, 1, 2, 3, 3}, new int[]{2, 3, -5, 5, -1, 2, 2, 3});
    public static final SoundcoreEqualizer FLAT = new SoundcoreEqualizer(0x09, R.string.soundcore_equalizer_preset_flat, new int[]{-2, -2, -1, 0, 0, 0, -2, -2}, new int[]{-2, -1, -1, 0, 0, 1, -2, -2});
    public static final SoundcoreEqualizer HIP_HOP = new SoundcoreEqualizer(0x0a, R.string.soundcore_equalizer_preset_hip_hop, new int[]{2, 3, -1, -1, 2, -1, 2, 3}, new int[]{1, 4, -2, -2, 5, -4, 3, 3});
    public static final SoundcoreEqualizer JAZZ = new SoundcoreEqualizer(0x0b, R.string.soundcore_equalizer_preset_jazz, new int[]{2, 2, -2, -2, 0, 2, 3, 4}, new int[]{1, 3, -3, -1, 0, 2, 1, 5});
    public static final SoundcoreEqualizer LATIN = new SoundcoreEqualizer(0x0c, R.string.soundcore_equalizer_preset_latin, new int[]{0, 0, -2, -2, -2, 0, 3, 5}, new int[]{0, 1, -2, -1, -2, 0, 2, 6});
    public static final SoundcoreEqualizer LOUNGE = new SoundcoreEqualizer(0x0d, R.string.soundcore_equalizer_preset_lounge, new int[]{-1, 2, 4, 3, 0, -2, 2, 1}, new int[]{-2, 2, 3, 2, 0, -4, 4, 0});
    public static final SoundcoreEqualizer PIANO = new SoundcoreEqualizer(0x0e, R.string.soundcore_equalizer_preset_piano, new int[]{0, 3, 3, 2, 4, 5, 3, 4}, new int[]{-1, 3, 2, 0, 3, 4, 0, 5});
    public static final SoundcoreEqualizer POP = new SoundcoreEqualizer(0x0f, R.string.nothing_equalizer_pop, new int[]{-1, 1, 3, 3, 1, -1, -2, -3}, new int[]{-1, 1, 2, 2, 1, -1, -1, -3});
    public static final SoundcoreEqualizer RNB = new SoundcoreEqualizer(0x10, R.string.soundcore_equalizer_preset_rnb, new int[]{6, 2, -2, -2, 2, 3, 3, 4}, new int[]{6, 1, -2, -3, 3, 2, 1, 5});
    public static final SoundcoreEqualizer ROCK = new SoundcoreEqualizer(0x11, R.string.nothing_equalizer_rock, new int[]{3, 2, -1, -1, 1, 3, 4, 5}, new int[]{2, 2, -2, -1, 1, 2, 2, 6});
    public static final SoundcoreEqualizer SMALL_SPEAKERS = new SoundcoreEqualizer(0x12, R.string.soundcore_equalizer_preset_small_speakers, new int[]{4, 3, 1, 0, -2, -3, -4, -4}, new int[]{3, 2, 0, 1, -2, -2, -3, -4});
    public static final SoundcoreEqualizer SPOKEN_WORD = new SoundcoreEqualizer(0x13, R.string.soundcore_equalizer_preset_spoken_word, new int[]{-3, -2, 1, 2, 2, 1, 0, -3}, new int[]{-2, -2, 2, 1, 2, 0, 1, -4});
    public static final SoundcoreEqualizer TREBLE_BOOST = new SoundcoreEqualizer(0x14, R.string.sony_equalizer_preset_treble_boost, new int[]{-2, -2, -2, -1, 1, 2, 2, 4}, new int[]{-2, -1, -1, -1, 1, 2, 0, 5});
    public static final SoundcoreEqualizer TREBLE_REDUCER = new SoundcoreEqualizer(0x15, R.string.soundcore_equalizer_preset_treble_reducer, new int[]{0, 0, 0, -2, -3, -4, -4, -6}, new int[]{0, 0, 1, -2, -1, -3, -1, -7});
    public static final SoundcoreEqualizer CUSTOM = new SoundcoreEqualizer(0xfe, R.string.custom);

    public static final SoundcoreEqualizer EXTRA_BASS = new SoundcoreEqualizer(0x01, R.string.soundcore_equalizer_preset_xtra_bass);
    public static final SoundcoreEqualizer VOICE = new SoundcoreEqualizer(0x02, R.string.soundcore_equalizer_preset_voice);
    public static final SoundcoreEqualizer BALANCED = new SoundcoreEqualizer(0x03, R.string.soundcore_equalizer_preset_balanced);

    public static final List<SoundcoreEqualizer> PRESETS = Collections.unmodifiableList(Arrays.asList(
            SIGNATURE, ACOUSTIC, BASS_BOOST, BASS_REDUCER, CLASSICAL, PODCAST,
            DANCE, DEEP, ELECTRONIC, FLAT, HIP_HOP, JAZZ, LATIN, LOUNGE,
            PIANO, POP, RNB, ROCK, SMALL_SPEAKERS, SPOKEN_WORD,
            TREBLE_BOOST, TREBLE_REDUCER, CUSTOM
    ));

    private SoundcoreEqualizerPresets() {
    }

    @Nullable
    public static SoundcoreEqualizer fromIndex(final int index) {
        for (final SoundcoreEqualizer preset : PRESETS) {
            if (preset.getIndex() == index) {
                return preset;
            }
        }
        return null;
    }
}
