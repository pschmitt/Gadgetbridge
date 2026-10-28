/*  Copyright (C) 2015-2024 Andreas Shimokawa, Carsten Pfeiffer

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
package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import android.content.Context;
import android.util.AttributeSet;

import com.github.mikephil.charting.animation.Easing;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.data.Entry;

import kotlin.Unit;

/**
 * A BarChart with some specific customization, like
 * <li>allowing to animate a single entry's values without going over 0</li>
 */
public class CustomBarChart extends BarChart {

    private Entry<?> entry = null;
    private float nextValue;

    public CustomBarChart(Context context) {
        super(context);
    }

    public CustomBarChart(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CustomBarChart(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setSinglAnimationEntry(final Entry<?> entry) {
        this.entry = entry;
        if (entry != null) {
            nextValue = entry.getY();
        }
    }

    /**
     * Call this to set the next value for the Entry to be animated.
     * Call animateSingleEntry() when ready to do that.
     *
     * @param nextValue
     */
    public void setSingleEntryYValue(final float nextValue) {
        this.nextValue = nextValue;
    }

    public void animateSingleEntry(final int durationMillis) {
        if (entry != null) {
            animateValue(entry, nextValue, durationMillis, Easing.INSTANCE.getLinear(), () -> Unit.INSTANCE);
        }
    }
}
