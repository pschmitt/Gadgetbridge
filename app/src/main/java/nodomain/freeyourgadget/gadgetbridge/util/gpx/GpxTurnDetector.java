package nodomain.freeyourgadget.gadgetbridge.util.gpx;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import nodomain.freeyourgadget.gadgetbridge.model.GPSCoordinate;
import nodomain.freeyourgadget.gadgetbridge.model.NavigationInfoSpec;

/**
 * Detect turns in a track from the change of bearing between its points.
 * <p>
 * Ported from <a href="https://gitlab.com/nwholloway/fit-route">fit-route by Nick Holloway</a>.
 */
public final class GpxTurnDetector {
    private static final double ANGLE_SLIGHT = 30;
    private static final double ANGLE_REGULAR = 60;
    private static final double ANGLE_SHARP = 120;
    private static final double ANGLE_REVERSE = 150;

    public record Turn(int pointIndex, int action) {
    }

    private GpxTurnDetector() {
    }

    @Nullable
    private static Integer turnMapping(final double angle) {
        if (angle >= ANGLE_SLIGHT && angle <= 360 - ANGLE_SLIGHT) {
            if (angle < ANGLE_REGULAR) {
                return NavigationInfoSpec.ACTION_TURN_RIGHT_SLIGHTLY;
            } else if (angle < ANGLE_SHARP) {
                return NavigationInfoSpec.ACTION_TURN_RIGHT;
            } else if (angle < ANGLE_REVERSE) {
                return NavigationInfoSpec.ACTION_TURN_RIGHT_SHARPLY;
            } else if (angle <= 360 - ANGLE_REVERSE) {
                return NavigationInfoSpec.ACTION_UTURN_LEFT;
            } else if (angle <= 360 - ANGLE_SHARP) {
                return NavigationInfoSpec.ACTION_TURN_LEFT_SHARPLY;
            } else if (angle <= 360 - ANGLE_REGULAR) {
                return NavigationInfoSpec.ACTION_TURN_LEFT;
            } else {
                return NavigationInfoSpec.ACTION_TURN_LEFT_SLIGHTLY;
            }
        }
        return null;
    }

    private static double[] setDistance(final List<? extends GPSCoordinate> points) {
        final double[] distances = new double[points.size()];
        double distance = 0;

        for (int i = 1; i < points.size(); i++) {
            distance += GPSCoordinate.distanceHaversine(points.get(i - 1), points.get(i));
            distances[i] = distance;
        }
        return distances;
    }

    public static List<Turn> turnPoints(final List<? extends GPSCoordinate> points) {
        final List<Turn> turns = new ArrayList<>();
        final double[] distance = setDistance(points);

        int prevPoint = -1;
        int turnPoint = -1;
        double prevBearing = Double.NaN;

        for (int point = 0; point < points.size(); point++) {
            if (prevPoint >= 0 && distance[point] - distance[prevPoint] > 5) {
                if (turnPoint < 0) {
                    turnPoint = prevPoint;
                }
                final double turnDistance = distance[point] - distance[turnPoint];

                final double bearing = Math.round(GPSCoordinate.bearing(points.get(turnPoint), points.get(point)));

                final double angle = (bearing - prevBearing + 360) % 360;
                final boolean isTurning = angle >= 10 && angle <= 350 && turnDistance < 25;

                if (!isTurning) {
                    final Integer turn = turnMapping(angle);
                    if (turn != null) {
                        turns.add(new Turn(turnPoint, turn));
                    }

                    prevBearing = bearing;
                    turnPoint = -1;
                }
            }
            prevPoint = point;
        }

        return turns;
    }
}
