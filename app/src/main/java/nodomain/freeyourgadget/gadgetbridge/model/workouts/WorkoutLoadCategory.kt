package nodomain.freeyourgadget.gadgetbridge.model.workouts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.LabeledEntry

/**
 * The weight of a [WorkoutWeightType.LOAD_CATEGORY] step. The database stores the ordinal, so the
 * order of the entries must not change.
 */
enum class WorkoutLoadCategory(override val label: Int) : LabeledEntry {
    LIGHT(R.string.pref_theme_light),
    MODERATE(R.string.training_readiness_zone_moderate),
    HEAVY(R.string.workout_load_heavy),
    VERY_HEAVY(R.string.workout_load_very_heavy),
}
