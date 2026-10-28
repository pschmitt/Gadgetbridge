package nodomain.freeyourgadget.gadgetbridge.activities.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.BitmapDrawable;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.ColorInt;

import com.google.android.material.color.MaterialColors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.R;

public class GaugeDrawer {
    private static final Logger LOG = LoggerFactory.getLogger(GaugeDrawer.class);
    protected @ColorInt int color_unknown = Color.argb(25, 128, 128, 128);

    private static final float RING_DEFAULT_SIZE_DP = 150f;
    private static final float RING_DESIGN_SIZE = 220f;
    private static final float RING_RADIUS = 92f;
    private static final float RING_STROKE = RING_DESIGN_SIZE / 15;
    private static final float RING_MARKER_RADIUS = 10f;
    private static final float RING_MARKER_GAP = 3f;
    private static final float RING_SEGMENT_GAP = 0.01f;

    public enum Shape {
        RING(135f, 270f),
        HALF_CIRCLE(180f, 180f);

        private final float startDegrees;
        private final float sweepDegrees;

        Shape(final float startDegrees, final float sweepDegrees) {
            this.startDegrees = startDegrees;
            this.sweepDegrees = sweepDegrees;
        }
    }

    private final Shape shape;

    public GaugeDrawer() {
        this(Shape.RING);
    }

    public GaugeDrawer(final Shape shape) {
        this.shape = shape;
    }

    /**
     * Draws a simple gauge, filled from the start up to the value.
     *
     * @param color the gauge color
     * @param value the gauge value. Range: [0, 1], or -1 for an empty gauge
     */
    public void drawSimpleGauge(final ImageView gaugeBar, final int color, final float value) {
        final Ring ring = new Ring(gaugeBar, shape);
        ring.drawTrack(color_unknown);
        if (value > 0) {
            ring.drawSection(0, Math.min(value, 1), color);
        }
        ring.show(gaugeBar);
    }

    /**
     * Draws a segmented gauge, with a marker at the value.
     *
     * @param colors             the colors of each segment
     * @param segments           the size of each segment. The sum of all segments should be at most 1
     * @param value              the gauge value, in range [0, 1], or -1 for no value and only segments
     * @param fadeOutsideDot     whether to fade out colors outside the dot value
     * @param gapBetweenSegments whether to introduce a small gap between the segments
     */
    public void drawSegmentedGauge(final ImageView gaugeBar,
                                   final int[] colors,
                                   final float[] segments,
                                   final float value,
                                   final boolean fadeOutsideDot,
                                   final boolean gapBetweenSegments) {
        if (colors.length != segments.length) {
            LOG.error("Colors length {} differs from segments length {}", colors.length, segments.length);
            return;
        }

        final Ring ring = new Ring(gaugeBar, shape);
        ring.drawTrack(color_unknown);
        final float halfGap = gapBetweenSegments ? RING_SEGMENT_GAP / 2 : 0;

        int firstSegment = -1;
        int lastSegment = -1;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i] > 0) {
                if (firstSegment < 0) {
                    firstSegment = i;
                }
                lastSegment = i;
            }
        }

        int markerColor = MaterialColors.getColor(gaugeBar.getContext(), R.attr.textColorPrimary, Color.WHITE);
        float segmentStart = 0;
        for (int i = 0; i < segments.length; i++) {
            final float segmentEnd = segmentStart + segments[i];
            if (segments[i] > 0) {
                final boolean containsValue = value >= segmentStart && Math.min(value, 1) <= segmentEnd;
                if (containsValue) {
                    markerColor = colors[i];
                }
                final int color = fadeOutsideDot && value >= 0 && !containsValue ? colors[i] - 0xB0000000 : colors[i];
                // A sum just under 1 is a rounding error, so the last segment still reaches the end
                final float end = i == lastSegment && segmentEnd >= 1 - RING_SEGMENT_GAP ? 1 : segmentEnd;
                ring.drawSection(
                    i == firstSegment ? segmentStart : segmentStart + halfGap,
                    i == lastSegment ? Math.min(end, 1) : segmentEnd - halfGap,
                    color,
                    i == firstSegment,
                    i == lastSegment
                );
            }
            segmentStart = segmentEnd;
        }

        if (value >= 0) {
            ring.drawMarker(Math.min(value, 1), markerColor);
        }

        ring.show(gaugeBar);
    }

    /**
     * An arc of a {@link Shape} sized to the gauge view's width, drawn into a reused bitmap.
     */
    private static class Ring {
        private final Shape shape;
        private final Bitmap bitmap;
        private final Canvas canvas;
        private final boolean reused;
        private final float scale;
        private final float center;
        private final float radius;
        private final RectF oval;

        Ring(final ImageView gaugeBar, final Shape shape) {
            this.shape = shape;
            final ViewGroup.LayoutParams params = gaugeBar.getLayoutParams();
            final int size;
            if (params != null && params.width > 0) {
                size = params.width;
            } else if (gaugeBar.getWidth() > 0) {
                size = gaugeBar.getWidth();
            } else {
                size = Math.round(RING_DEFAULT_SIZE_DP * gaugeBar.getResources().getDisplayMetrics().density);
            }
            scale = size / RING_DESIGN_SIZE;
            center = size / 2f;
            radius = RING_RADIUS * scale;
            oval = new RectF(center - radius, center - radius, center + radius, center + radius);
            final int height = shape == Shape.HALF_CIRCLE
                ? (int) Math.ceil(center + (RING_MARKER_RADIUS + RING_MARKER_GAP) * scale)
                : size;

            Bitmap previous = null;
            if (gaugeBar.getDrawable() instanceof BitmapDrawable drawable) {
                previous = drawable.getBitmap();
            }
            reused = previous != null && previous.isMutable() && previous.getWidth() == size && previous.getHeight() == height;
            if (reused) {
                bitmap = previous;
                bitmap.eraseColor(Color.TRANSPARENT);
            } else {
                bitmap = Bitmap.createBitmap(size, height, Bitmap.Config.ARGB_8888);
            }
            canvas = new Canvas(bitmap);
        }

        void drawTrack(@ColorInt final int color) {
            drawSection(0, 1, color);
        }

        void drawSection(final float start, final float end, @ColorInt final int color) {
            drawSection(start, end, color, true, true);
        }

        /**
         * Draws the part of the arc between two fractions of the sweep, with rounded ends where asked.
         */
        void drawSection(final float start, final float end, @ColorInt final int color, final boolean roundStart, final boolean roundEnd) {
            if (end <= start) {
                return;
            }
            final Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStrokeWidth(RING_STROKE * scale);
            paint.setColor(color);
            canvas.drawArc(oval, shape.startDegrees + shape.sweepDegrees * start, shape.sweepDegrees * (end - start), false, paint);
            if (roundStart) {
                drawCap(start, true, color);
            }
            if (roundEnd) {
                drawCap(end, false, color);
            }
        }

        /**
         * Rounds off the arc's start or end at a fraction of the sweep with a half disc, which does not
         * overlap the arc itself.
         */
        private void drawCap(final float at, final boolean start, @ColorInt final int color) {
            final float degrees = shape.startDegrees + shape.sweepDegrees * at;
            final double angle = Math.toRadians(degrees);
            final float x = center + radius * (float) Math.cos(angle);
            final float y = center + radius * (float) Math.sin(angle);
            final float capRadius = RING_STROKE * scale / 2;

            final Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawArc(x - capRadius, y - capRadius, x + capRadius, y + capRadius,
                start ? degrees - 180 : degrees, 180, true, paint);
        }

        void drawMarker(final float value, @ColorInt final int color) {
            final double angle = Math.toRadians(shape.startDegrees + shape.sweepDegrees * value);
            final float x = center + radius * (float) Math.cos(angle);
            final float y = center + radius * (float) Math.sin(angle);

            final Paint paint = new Paint();
            paint.setAntiAlias(true);
            paint.setStyle(Paint.Style.FILL);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
            canvas.drawCircle(x, y, (RING_MARKER_RADIUS + RING_MARKER_GAP) * scale, paint);

            paint.setXfermode(null);
            paint.setColor(color);
            canvas.drawCircle(x, y, RING_MARKER_RADIUS * scale, paint);
        }

        void show(final ImageView gaugeBar) {
            if (reused) {
                gaugeBar.invalidate();
            } else {
                gaugeBar.setImageBitmap(bitmap);
            }
        }
    }

    public static Bitmap drawCircleGaugeSegmented(int width,
                                                  int barWidth,
                                                  final int[] colors,
                                                  final float[] segments,
                                                  final boolean gapBetweenSegments,
                                                  String text,
                                                  String lowerText,
                                                  Context context) {
        int TEXT_COLOR = GBApplication.getTextColor(context);
        int SUBTEXT_COLOR = GBApplication.getSecondaryTextColor(context);
        int height = width;
        int barMargin = (int) Math.ceil(barWidth / 2f);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.BUTT);
        paint.setStrokeWidth(barWidth);
        paint.setColor(MaterialColors.getColor(context, R.attr.gauge_track, context.getResources().getColor(R.color.gauge_line_color)));
        canvas.drawArc(
            barMargin,
            barMargin,
            width - barMargin,
            width - barMargin,
            90,
            360,
            false,
            paint);
        paint.setStrokeWidth(barWidth);

        float remainingAngle = 360;
        float gapDegree = 1f;
        if (gapBetweenSegments) {
            int validSegments = segments.length;
            for (int i = 0; i < segments.length; i++) {
                if (segments[i] == 0) {
                    validSegments--;
                }
            }

            remainingAngle = 360 - (validSegments * gapDegree);
        }

        float angleSum = 0;
        for (int i = 0; i < segments.length; i++) {
            if (segments[i] == 0) {
                continue;
            }

            paint.setColor(colors[i]);
            paint.setStrokeWidth(barWidth);

            float startAngleDegrees = 270 + (angleSum * remainingAngle);
            float sweepAngleDegrees = segments[i] * remainingAngle;

            canvas.drawArc(
                barMargin,
                barMargin,
                width - barMargin,
                height - barMargin,
                startAngleDegrees,
                sweepAngleDegrees,
                false,
                paint
            );
            angleSum += segments[i];
            if (gapBetweenSegments) {
                angleSum += (gapDegree / 360f);
            }
        }

        Paint textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        float textPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.06f, context.getResources().getDisplayMetrics());
        textPaint.setTextSize(textPixels);
        textPaint.setTextAlign(Paint.Align.CENTER);
        int yPos = (int) ((float) height / 2 - ((textPaint.descent() + textPaint.ascent()) / 2));
        canvas.drawText(String.valueOf(text), width / 2f, yPos, textPaint);
        Paint textLowerPaint = new Paint();
        textLowerPaint.setColor(SUBTEXT_COLOR);
        textLowerPaint.setTextAlign(Paint.Align.CENTER);
        float textLowerPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.025f, context.getResources().getDisplayMetrics());
        textLowerPaint.setTextSize(textLowerPixels);
        int yPosLowerText = (int) ((float) height / 2 - textPaint.ascent());
        canvas.drawText(String.valueOf(lowerText), width / 2f, yPosLowerText, textLowerPaint);

        return bitmap;
    }

    public static Bitmap drawCircleGauge(int width,
                                         int barWidth,
                                         @ColorInt int filledColor,
                                         int value,
                                         int maxValue,
                                         Context context) {
        int TEXT_COLOR = GBApplication.getTextColor(context);
        int SUB_TEXT_COLOR = GBApplication.getSecondaryTextColor(context);
        int height = width;
        int barMargin = (int) Math.ceil(barWidth / 2f);
        float filledFactor = (float) value / maxValue;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(barWidth);
        paint.setColor(MaterialColors.getColor(context, R.attr.gauge_track, context.getResources().getColor(R.color.gauge_line_color)));
        canvas.drawArc(
            barMargin,
            barMargin,
            width - barMargin,
            width - barMargin,
            90,
            360,
            false,
            paint);
        paint.setStrokeWidth(barWidth);
        paint.setColor(filledColor);
        canvas.drawArc(
            barMargin,
            barMargin,
            width - barMargin,
            height - barMargin,
            270,
            360 * filledFactor,
            false,
            paint
        );

        Paint textPaint = new Paint();
        textPaint.setColor(TEXT_COLOR);
        float textPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.06f, context.getResources().getDisplayMetrics());
        textPaint.setTextSize(textPixels);
        textPaint.setTextAlign(Paint.Align.CENTER);
        int yPos = (int) ((float) height / 2 - ((textPaint.descent() + textPaint.ascent()) / 2));
        canvas.drawText(NumberFormat.getInstance().format(value), width / 2f, yPos, textPaint);
        Paint textLowerPaint = new Paint();
        textLowerPaint.setColor(SUB_TEXT_COLOR);
        textLowerPaint.setTextAlign(Paint.Align.CENTER);
        float textLowerPixels = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, width * 0.025f, context.getResources().getDisplayMetrics());
        textLowerPaint.setTextSize(textLowerPixels);
        int yPosLowerText = (int) ((float) height / 2 - textPaint.ascent());
        canvas.drawText(NumberFormat.getInstance().format(maxValue), width / 2f, yPosLowerText, textLowerPaint);

        return bitmap;
    }

    public static double normalize(final double value, final double min, final double max) {
        return normalize(value, min, max, 0, 1);
    }

    public static double normalize(final double value, final double minSource, final double maxSource, final double minTarget, final double maxTarget) {
        return ((value - minSource) * (maxTarget - minTarget)) / (maxSource - minSource) + minTarget;
    }

}
