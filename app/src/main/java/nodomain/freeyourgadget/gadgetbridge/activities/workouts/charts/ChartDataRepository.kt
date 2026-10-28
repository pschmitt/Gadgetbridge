package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts

import nodomain.freeyourgadget.gadgetbridge.activities.charts.IntervalChartUtils
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart

object ChartDataRepository {
    var chartData: List<WorkoutChart>? = null

    // Workout-global interval rows, carried so the compare/overlay view can draw interval splits.
    var intervals: List<IntervalChartUtils.IntervalRow>? = null

    fun clear() {
        chartData = null
        intervals = null
    }
}
