package nodomain.freeyourgadget.gadgetbridge.activities.charts.marker;

import android.content.Context;
import android.graphics.Canvas;
import android.widget.TextView;

import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.CombinedData;
import com.github.mikephil.charting.data.DataSet;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.interfaces.datasets.IBarLineScatterCandleBubbleDataSet;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import nodomain.freeyourgadget.gadgetbridge.R;

public class ValueMarker extends MarkerView {
    private final TextView markerContent;
    private Map<String, IAxisValueFormatter> valueFormatters;
    private Map<String, String> valueUnits;
    private CombinedData lineData;

    public ValueMarker(Context context) {
        super(context, R.layout.value_marker);
        this.markerContent = findViewById(R.id.marker_content);
    }

    public ValueMarker(Context context, CombinedData lineData, Map<String, IAxisValueFormatter> valueFormatters, Map<String, String> valueUnits) {
        super(context, R.layout.value_marker);
        this.markerContent = findViewById(R.id.marker_content);
        this.valueFormatters = valueFormatters;
        this.lineData = lineData;
        this.valueUnits = valueUnits;
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        float xVal = e.getX();
        final StringBuilder content = new StringBuilder();
        // A metric split into gapped segments contributes consecutive datasets of which only the
        // first carries the label; the unlabelled ones that follow belong to the same series. Only
        // a segment whose x range covers xVal reports, since getEntryForXValue returns the closest
        // entry even when it lies far outside the segment.
        final Set<String> reportedLabels = new HashSet<>();
        String seriesLabel = null;
        for (int i = 0; i < lineData.getDataSetCount(); i++) {
            final IBarLineScatterCandleBubbleDataSet<?> dataSet = lineData.getDataSetByIndex(i);
            if (dataSet == null) {
                continue;
            }
            if (!dataSet.getLabel().isEmpty()) {
                seriesLabel = dataSet.getLabel();
            }
            final String label = seriesLabel;
            if (!dataSet.isVisible() || (label != null && reportedLabels.contains(label))) {
                continue;
            }
            if (xVal < dataSet.getXMin() || xVal > dataSet.getXMax()) {
                continue;
            }
            Entry entryForX = dataSet.getEntryForXValue(xVal, Float.NaN, DataSet.Rounding.CLOSEST);
            if (entryForX != null) {
                final IAxisValueFormatter formatter = valueFormatters.get(label);
                if (formatter != null) {
                    final YAxis axis = ((BarLineChartBase<?>) getChartView()).getAxis(dataSet.getAxisDependency());
                    content.append(formatter.getFormattedValue(entryForX.getY(), axis));
                } else {
                    content.append(entryForX.getY());
                }
                final String unit = valueUnits.get(label);
                if (unit != null) {
                    content.append(" ");
                    content.append(unit);
                }
                content.append("\n");
                if (label != null) {
                    reportedLabels.add(label);
                }
            }
        }
        markerContent.setText(content.toString().trim());
        super.refreshContent(e, highlight);
    }

    @Override
    public void draw(Canvas canvas, float posX, float posY) {
        // Stick the value marker box to the top.
        MPPointF offset = getOffset();
        float markerWidth = getWidth();
        float offsetX = offset.x;

        if ((posX + offsetX) < 0) {
            posX = -offsetX;
        } else if ((posX + offsetX + markerWidth) > canvas.getWidth()) {
            posX = canvas.getWidth() - markerWidth - offsetX;
        }

        float fixedTopY = 10f;
        canvas.translate(posX + offsetX, fixedTopY);
        draw(canvas);
        canvas.translate(-posX - offsetX, -fixedTopY);
    }

    @Override
    public MPPointF getOffset() {
        // Center horizontally and place above the marker point
        return new MPPointF(-(getWidth() / 2f), -getHeight());
    }

}
