package nodomain.freeyourgadget.gadgetbridge.model.workout

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartPoint
import nodomain.freeyourgadget.gadgetbridge.model.heartratezones.HeartRateZones

/**
 * One metric of a workout over time. [labeler] formats its values, and [minimum] / [maximum] fix the y axis.
 */
data class WorkoutChart @JvmOverloads constructor(
    val id: String,
    val title: String,
    val group: String,
    val series: Series,
    val labeler: ((Double) -> String)? = null,
    var unitString: String? = null,
    val minimum: Double? = null,
    val maximum: Double? = null,
    // Time-in-zone (index 0..5) and the resolved thresholds for the HR-zone overlay; null on non-HR charts.
    var secondsInZone: IntArray? = null,
    var zoneThresholds: HeartRateZones? = null
) {
    /**
     * Points with x in seconds since the workout started, broken where neighbours are more than [maxGap] apart.
     */
    data class Series @JvmOverloads constructor(
        val points: List<ChartPoint>,
        val color: Int,
        val maxGap: Double? = null,
        val dots: Boolean = false,
    )
}
