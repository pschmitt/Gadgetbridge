package nodomain.freeyourgadget.gadgetbridge.activities.dashboard;

import android.graphics.Color;
import android.util.TypedValue;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import nodomain.freeyourgadget.gadgetbridge.R;

/**
 * A gauge with a value, a label and a star when the goal is reached.
 * The container must include the activity_gauge layout.
 */
public class ActivityGauge {
    private static final int COLOR_START = Color.parseColor("#e74c3c");
    private static final int COLOR_END = Color.parseColor("#2ecc71");
    private static final float VALUE_TEXT_SCALE = 0.15f;
    private static final float LABEL_TEXT_SCALE = 0.11f;

    public final View container;
    private final ImageView ring;
    private final TextView value;
    private final TextView label;
    private final ImageView goal;

    public ActivityGauge(final View container) {
        this.container = container;
        ring = container.findViewById(R.id.activity_gauge_ring);
        value = container.findViewById(R.id.activity_gauge_value);
        label = container.findViewById(R.id.activity_gauge_label);
        goal = container.findViewById(R.id.activity_gauge_goal);

        final int size = ring.getLayoutParams().width;
        value.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * VALUE_TEXT_SCALE);
        label.setTextSize(TypedValue.COMPLEX_UNIT_PX, size * LABEL_TEXT_SCALE);
    }

    public void update(final GaugeDrawer gaugeDrawer, final float current, final float target,
                       final String valueText, final String labelText) {
        final float progress = Math.max(0, Math.min(1, current / target));
        gaugeDrawer.drawSimpleGauge(ring, interpolateColor(COLOR_START, COLOR_END, progress), progress);
        value.setText(valueText);
        label.setText(labelText);
        goal.setVisibility(progress == 1 ? View.VISIBLE : View.GONE);
    }

    @SuppressWarnings({"SpellCheckingInspection", "SameParameterValue"})
    private static int interpolateColor(final int a, final int b, final float proportion) {
        final float[] hsva = new float[3];
        final float[] hsvb = new float[3];
        Color.colorToHSV(a, hsva);
        Color.colorToHSV(b, hsvb);
        for (int i = 0; i < 3; i++) {
            hsvb[i] = hsva[i] + (hsvb[i] - hsva[i]) * proportion;
        }
        return Color.HSVToColor(hsvb);
    }
}
