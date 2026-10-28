package nodomain.freeyourgadget.gadgetbridge.model.workout

import nodomain.freeyourgadget.gadgetbridge.activities.charts.IntervalChartUtils
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryData

data class Workout @JvmOverloads constructor(
    val summary: BaseActivitySummary,
    val data: ActivitySummaryData,
    val charts: List<WorkoutChart> = emptyList(),
    // Derived from the activity track's segments; drives the interval lines on the charts.
    val intervals: List<IntervalChartUtils.IntervalRow> = emptyList()
)
