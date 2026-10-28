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
package nodomain.freeyourgadget.gadgetbridge.activities.charts.marker;

import static org.junit.Assert.assertEquals;

import android.view.ContextThemeWrapper;
import android.widget.TextView;

import com.github.mikephil.charting.charts.CombinedChart;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.test.TestBase;

public class ValueMarkerTest extends TestBase {
    private static List<Entry> run(final int fromX, final int toX, final float y) {
        final List<Entry> entries = new ArrayList<>();
        for (int x = fromX; x <= toX; x++) {
            entries.add(new Entry<>(x, y, null, null));
        }
        return entries;
    }

    private String markerTextAt(final float x) {
        // A gapped HR series: only the first segment is labelled.
        final List<ILineDataSet<?>> dataSets = new ArrayList<>();
        dataSets.add(new LineDataSet(run(0, 10, 120), "HR"));
        dataSets.add(new LineDataSet(run(20, 30, 140), ""));
        final CombinedData data = new CombinedData();
        data.setLineData(new LineData(dataSets));

        final Map<String, IAxisValueFormatter> formatters = Collections.singletonMap("HR", (value, axis) -> String.valueOf((int) value));
        final ContextThemeWrapper context = new ContextThemeWrapper(getContext(), R.style.GadgetbridgeTheme);
        final CombinedChart chart = new CombinedChart(context);
        chart.setData(data);
        final ValueMarker marker = new ValueMarker(context, data, formatters, Collections.singletonMap("HR", "bpm"));
        chart.setMarker(marker);
        marker.refreshContent(new Entry<>(x, 0, null, null), new Highlight(x, 0, 0));
        return ((TextView) marker.findViewById(R.id.marker_content)).getText().toString();
    }

    @Test
    public void laterGapSegmentUsesTheSeriesFormatterAndUnit() {
        assertEquals("140 bpm", markerTextAt(25));
    }

    @Test
    public void firstSegmentReportsOnce() {
        assertEquals("120 bpm", markerTextAt(5));
    }

    @Test
    public void nothingIsReportedInsideAGap() {
        assertEquals("", markerTextAt(15));
    }
}
