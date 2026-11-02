package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutMeasurementSystem
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.roundToLong

class ZeppOsWorkoutTemplateImportTest : TestBase() {
    @Test
    fun exportThenImportEqualsOriginal() {
        listOf(running(), cycling(), rowing(), jumpRope(), strength()).forEach { original ->
            val file = ZeppOsWorkoutTemplateUploader.export(original)
            assertNotNull(original.name, file)
            val imported = ZeppOsWorkoutTemplateDecoder.import(file!!.bytes)
            assertNotNull(original.name, imported)
            assertEquals(original.name, comparable(original), comparable(imported!!))
        }
    }

    @Test
    fun importRejectsInvalidFile() {
        assertNull(ZeppOsWorkoutTemplateDecoder.import(byteArrayOf()))
        assertNull(ZeppOsWorkoutTemplateDecoder.import("{}".toByteArray(Charsets.UTF_8)))
        assertNull(ZeppOsWorkoutTemplateDecoder.import("not json".toByteArray(Charsets.UTF_8)))
    }

    private fun running() = template(
        "Outdoor Running",
        ActivityKind.OUTDOOR_RUNNING,
        step(WorkoutStepType.WARMUP, time(45), noAlerts, note = "Easy"),
        step(WorkoutStepType.ACTIVE, distanceM(500), pace(330, 390)),
        step(WorkoutStepType.ACTIVE, calories(620), cadence(150, 165)),
        step(WorkoutStepType.ACTIVE, hrAbove(100), hrRange(110, 140)),
        step(
            WorkoutStepType.RECOVER,
            distanceM(15_200),
            pace(480, 570),
            measurementSystem = WorkoutMeasurementSystem.IMPERIAL,
        ),
        step(WorkoutStepType.REST, skipButton, noAlerts),
        repeat(
            3,
            step(WorkoutStepType.ACTIVE, distanceM(500), cadence(170, 190)),
            step(WorkoutStepType.RECOVER, time(45), noAlerts),
        ),
        step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
    )

    private fun cycling() = template(
        "Outdoor Cycling",
        ActivityKind.OUTDOOR_CYCLING,
        step(WorkoutStepType.WARMUP, time(45), noAlerts),
        step(WorkoutStepType.ACTIVE, distanceM(500), speedKmh(10, 15)),
        step(WorkoutStepType.ACTIVE, hrBelow(150), speedKmh(30, 40)),
        step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
    )

    private fun rowing() = template(
        "Rowing",
        ActivityKind.ROWING_MACHINE,
        step(WorkoutStepType.WARMUP, time(45), noAlerts),
        step(WorkoutStepType.ACTIVE, strokes(40), strokeRate(22, 28)),
        step(WorkoutStepType.REST, skipButton, noAlerts),
        step(WorkoutStepType.COOLDOWN, calories(20), noAlerts),
    )

    private fun jumpRope() = template(
        "Jump Rope",
        ActivityKind.JUMP_ROPING,
        step(WorkoutStepType.WARMUP, time(45), noAlerts),
        step(WorkoutStepType.ACTIVE, reps(150), hrRange(100, 130)),
        step(WorkoutStepType.COOLDOWN, time(30), noAlerts),
    )

    private fun strength() = template(
        "Strength",
        ActivityKind.STRENGTH_TRAINING,
        step(WorkoutStepType.WARMUP, time(45), noAlerts),
        step(
            WorkoutStepType.ACTIVE,
            reps(8),
            noAlerts,
            exerciseId = "bench_press",
            weightType = WorkoutWeightType.MANUAL,
            weightValue = 22_500,
        ),
        step(
            WorkoutStepType.ACTIVE,
            time(90),
            noAlerts,
            exerciseId = "eccentric_pull_up",
            weightType = WorkoutWeightType.BODY_WEIGHT,
        ),
        step(
            WorkoutStepType.ACTIVE,
            calories(45),
            hrRange(110, 140),
            exerciseId = "dumbbell_lunge_swing",
            weightType = WorkoutWeightType.RM,
            weightValue = 8,
        ),
        repeat(
            5,
            step(
                WorkoutStepType.ACTIVE,
                reps(8),
                noAlerts,
                exerciseId = "barbell_squats",
                weightType = WorkoutWeightType.MANUAL,
                weightValue = 87_500,
            ),
            step(WorkoutStepType.REST, skipButton, noAlerts),
        ),
        step(WorkoutStepType.COOLDOWN, time(45), noAlerts),
    )

    private fun comparable(template: WorkoutTemplate): WorkoutTemplate = template.copy(
        id = null,
        createdAt = 0,
        updatedAt = 0,
        steps = template.steps.mapTo(mutableListOf()) { comparable(it) },
    )

    private fun comparable(node: WorkoutStepNode): WorkoutStepNode = node.copy(
        localId = 0,
        children = node.children.mapTo(mutableListOf()) { comparable(it) },
    )

    private fun template(name: String, activityKind: ActivityKind, vararg steps: WorkoutStepNode) =
        WorkoutTemplate(
            vendorId = "zeppos",
            name = name,
            activityKind = activityKind,
            steps = steps.toMutableList(),
        )

    private fun step(
        type: WorkoutStepType,
        duration: WorkoutDuration,
        target: WorkoutTarget,
        exerciseId: String? = null,
        weightType: WorkoutWeightType? = null,
        weightValue: Int? = null,
        measurementSystem: WorkoutMeasurementSystem = WorkoutMeasurementSystem.METRIC,
        note: String? = null,
    ): WorkoutStepNode = WorkoutStepNode.newStep(type).apply {
        this.duration = duration
        this.target = target
        this.exerciseId = exerciseId
        this.weightType = weightType
        this.weightValue = weightValue
        this.measurementSystem = measurementSystem
        this.note = note
    }

    private fun repeat(count: Int, vararg children: WorkoutStepNode): WorkoutStepNode =
        WorkoutStepNode.newRepeat(count).apply { this.children = children.toMutableList() }

    private fun time(seconds: Long) = WorkoutDuration(WorkoutDurationType.TIME, seconds * 1000)
    private fun distanceM(meters: Long) = WorkoutDuration(WorkoutDurationType.DISTANCE, meters * 100)
    private fun calories(kcal: Long) = WorkoutDuration(WorkoutDurationType.CALORIES, kcal)
    private fun hrBelow(bpm: Long) = WorkoutDuration(WorkoutDurationType.HR_BELOW, bpm)
    private fun hrAbove(bpm: Long) = WorkoutDuration(WorkoutDurationType.HR_ABOVE, bpm)
    private fun strokes(n: Long) = WorkoutDuration(WorkoutDurationType.STROKES, n)
    private fun reps(n: Long) = WorkoutDuration(WorkoutDurationType.REPS, n)
    private val skipButton = WorkoutDuration(WorkoutDurationType.BUTTON_PRESS)

    private val noAlerts = WorkoutTarget.NONE
    private fun pace(lowSeconds: Long, highSeconds: Long) =
        WorkoutTarget(WorkoutTargetType.PACE, low = lowSeconds * 1000, high = highSeconds * 1000)

    private fun cadence(low: Long, high: Long) = WorkoutTarget(WorkoutTargetType.CADENCE, low = low, high = high)
    private fun speedKmh(lowKmh: Long, highKmh: Long) =
        WorkoutTarget(WorkoutTargetType.SPEED, low = kmhToMmPerS(lowKmh), high = kmhToMmPerS(highKmh))

    private fun strokeRate(low: Long, high: Long) =
        WorkoutTarget(WorkoutTargetType.STROKE_RATE, low = low, high = high)

    private fun hrRange(low: Long, high: Long) = WorkoutTarget(WorkoutTargetType.HR_RANGE, low = low, high = high)

    private fun kmhToMmPerS(kmh: Long): Long = (kmh * 1000 / 3.6).roundToLong()
}
