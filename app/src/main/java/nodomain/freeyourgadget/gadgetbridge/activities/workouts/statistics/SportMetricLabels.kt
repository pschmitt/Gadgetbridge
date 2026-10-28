package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import androidx.annotation.StringRes
import nodomain.freeyourgadget.gadgetbridge.R

@StringRes
fun SportMetric.labelRes(): Int = when (this) {
    SportMetric.DISTANCE -> R.string.distance
    SportMetric.DURATION -> R.string.activity_detail_duration_label
    SportMetric.AVG_PACE -> R.string.averageKMPaceSeconds
    SportMetric.AVG_SPEED -> R.string.avg_speed
    SportMetric.MAX_PACE -> R.string.maxPace
    SportMetric.MAX_SPEED -> R.string.max_speed
    SportMetric.AVG_HEART_RATE -> R.string.averageHR
    SportMetric.MAX_HEART_RATE -> R.string.maxHR
    SportMetric.AVG_POWER -> R.string.avg_power
    SportMetric.MAX_POWER -> R.string.max_power
    SportMetric.AVG_CADENCE -> R.string.avg_cadence
    SportMetric.MAX_CADENCE -> R.string.maxCadence
    SportMetric.AVG_SWOLF -> R.string.avg_swolf
    SportMetric.CALORIES -> R.string.calories
    SportMetric.ASCENT -> R.string.workout_ascent
    SportMetric.DESCENT -> R.string.workout_descent
    SportMetric.STEPS -> R.string.steps
    SportMetric.SETS -> R.string.workoutSets
    SportMetric.TRAINING_LOAD -> R.string.training_load
    SportMetric.AVG_AEROBIC_EFFECT -> R.string.avg_aerobic_effect
    SportMetric.AVG_ANAEROBIC_EFFECT -> R.string.avg_anaerobic_effect
}
