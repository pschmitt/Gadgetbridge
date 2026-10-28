package nodomain.freeyourgadget.gadgetbridge.activities.charts;

import androidx.annotation.ColorRes;

import nodomain.freeyourgadget.gadgetbridge.R;

public enum ChartsHeartRateColor {
    ORANGE(R.color.chart_heartrate),
    RED(R.color.chart_heartrate_alternative),
    ;

    @ColorRes
    private final int colorRes;

    ChartsHeartRateColor(@ColorRes final int colorRes) {
        this.colorRes = colorRes;
    }

    @ColorRes
    public int getColorRes() {
        return colorRes;
    }
}
