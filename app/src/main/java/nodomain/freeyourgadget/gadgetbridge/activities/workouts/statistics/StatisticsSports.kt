package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind

private val NOT_SPORTS = setOf(
    ActivityKind.UNKNOWN,
    ActivityKind.ACTIVITY,
    ActivityKind.NOT_WORN,
    ActivityKind.HEALTH_SNAPSHOT,
    ActivityKind.TRANSITION,
    ActivityKind.VIVOMOVE_HR_TRANSITION,
    ActivityKind.NAVIGATE,
    ActivityKind.MAP,
    ActivityKind.TRACK_ME,
    ActivityKind.ANCHOR,
    ActivityKind.STOP_WATCH,
    ActivityKind.APNEA_TEST,
)

fun isStatisticsSport(kind: ActivityKind): Boolean = kind !in NOT_SPORTS && !ActivityKind.isSleep(kind)
