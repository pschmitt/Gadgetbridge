package nodomain.freeyourgadget.gadgetbridge.service.devices.huami.zeppos.workouts

import nodomain.freeyourgadget.gadgetbridge.devices.huami.zeppos.ZeppOsWorkoutTemplateSpec
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDuration
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutDurationType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutNodeType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutStepNode
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTarget
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTargetType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutTemplate
import nodomain.freeyourgadget.gadgetbridge.model.workouts.WorkoutWeightType
import nodomain.freeyourgadget.gadgetbridge.model.workouts.spec.WorkoutTemplateImporter
import org.json.JSONArray
import org.json.JSONObject
import org.slf4j.LoggerFactory
import kotlin.math.roundToLong

object ZeppOsWorkoutTemplateDecoder : WorkoutTemplateImporter {
    private val LOG = LoggerFactory.getLogger(ZeppOsWorkoutTemplateDecoder::class.java)

    override fun import(bytes: ByteArray): WorkoutTemplate? = try {
        decode(JSONObject(bytes.toString(Charsets.UTF_8)))
    } catch (e: Exception) {
        LOG.warn("Failed to parse workout template", e)
        null
    }

    private fun decode(json: JSONObject): WorkoutTemplate {
        val activityKind = ZeppOsWorkoutCodes.activityKind(json.getInt("sportType"))
            ?: throw IllegalArgumentException("sportType")
        val name = json.getString("templateName")
        if (name.isBlank()) throw IllegalArgumentException("templateName")
        json.getLong("templateId")
        val steps = groupSteps(json.getJSONArray("intervals"))
        return WorkoutTemplate(
            vendorId = ZeppOsWorkoutTemplateSpec.VENDOR_ID,
            name = name,
            activityKind = activityKind,
            steps = steps,
        )
    }

    private fun groupSteps(intervals: JSONArray): MutableList<WorkoutStepNode> {
        val ordered = (0 until intervals.length())
            .map { intervals.getJSONObject(it) }
            .sortedBy { it.getInt("index") }
        val steps = mutableListOf<WorkoutStepNode>()
        var i = 0
        while (i < ordered.size) {
            val loopTimes = ordered[i].getInt("loopTimes")
            if (loopTimes < 1) throw IllegalArgumentException("loopTimes")
            if (loopTimes == 1) {
                steps += decodeStep(ordered[i])
                i++
                continue
            }
            val groupId = ordered[i].getInt("groupId")
            var end = i + 1
            while (end < ordered.size &&
                ordered[end].getInt("groupId") == groupId &&
                ordered[end].getInt("loopTimes") == loopTimes
            ) {
                end++
            }
            val children = mutableListOf<WorkoutStepNode>()
            for (j in i until end) {
                children += decodeStep(ordered[j])
            }
            steps += WorkoutStepNode(
                type = WorkoutNodeType.REPEAT,
                repeatCount = loopTimes,
                children = children,
            )
            i = end
        }
        return steps
    }

    private fun decodeStep(obj: JSONObject): WorkoutStepNode {
        val stepType = ZeppOsWorkoutCodes.stepType(obj.getInt("trainingType"))
            ?: throw IllegalArgumentException("trainingType")
        return WorkoutStepNode.newStep(stepType).apply {
            duration = decodeDuration(obj)
            target = decodeTarget(obj)
            note = obj.getString("intervalDesc").ifEmpty { null }
            measurementSystem = ZeppOsWorkoutCodes.measurementSystem(obj.getInt("lengthUnit"))
                ?: throw IllegalArgumentException("lengthUnit")
            val weight = decodeWeight(obj.getString("strengthWeight"))
            weightType = weight.first
            weightValue = weight.second
            exerciseId = decodeExerciseId(obj.getInt("actionType"))
        }
    }

    private fun decodeDuration(obj: JSONObject): WorkoutDuration {
        val typeCode = obj.getInt("intervalType")
        val value = obj.getLong("intervalVal")
        return when (typeCode) {
            ZeppOsWorkoutCodes.DURATION_DISTANCE ->
                WorkoutDuration(WorkoutDurationType.DISTANCE, value * 100)

            ZeppOsWorkoutCodes.DURATION_TIME ->
                WorkoutDuration(WorkoutDurationType.TIME, value * 1000)

            ZeppOsWorkoutCodes.DURATION_CALORIES ->
                WorkoutDuration(WorkoutDurationType.CALORIES, value)

            ZeppOsWorkoutCodes.DURATION_HR_BELOW ->
                WorkoutDuration(WorkoutDurationType.HR_BELOW, value)

            ZeppOsWorkoutCodes.DURATION_HR_ABOVE ->
                WorkoutDuration(WorkoutDurationType.HR_ABOVE, value)

            ZeppOsWorkoutCodes.DURATION_STROKES ->
                WorkoutDuration(WorkoutDurationType.STROKES, value)

            ZeppOsWorkoutCodes.DURATION_LAPS ->
                WorkoutDuration(WorkoutDurationType.LAPS, value)

            ZeppOsWorkoutCodes.DURATION_COUNT ->
                WorkoutDuration(WorkoutDurationType.REPS, value)

            ZeppOsWorkoutCodes.DURATION_OPEN_OR_REPS ->
                if (value == ZeppOsWorkoutCodes.SKIP_BUTTON_VALUE) {
                    WorkoutDuration(WorkoutDurationType.BUTTON_PRESS)
                } else {
                    WorkoutDuration(WorkoutDurationType.REPS, value)
                }

            else -> throw IllegalArgumentException("intervalType")
        }
    }

    private fun decodeTarget(obj: JSONObject): WorkoutTarget {
        val up = obj.getLong("remindUpThreshold")
        val down = obj.getLong("remindDownThreshold")
        return when (obj.getInt("remindType")) {
            ZeppOsWorkoutCodes.REMIND_OFF -> WorkoutTarget.NONE
            ZeppOsWorkoutCodes.REMIND_PACE ->
                WorkoutTarget(WorkoutTargetType.PACE, low = down * 1000, high = up * 1000)

            ZeppOsWorkoutCodes.REMIND_CADENCE ->
                WorkoutTarget(WorkoutTargetType.CADENCE, low = down, high = up)

            ZeppOsWorkoutCodes.REMIND_HEART_RATE ->
                WorkoutTarget(WorkoutTargetType.HR_RANGE, low = down, high = up)

            ZeppOsWorkoutCodes.REMIND_SPEED ->
                WorkoutTarget(WorkoutTargetType.SPEED, low = kmhToMmPerS(down), high = kmhToMmPerS(up))

            ZeppOsWorkoutCodes.REMIND_STROKE_RATE ->
                WorkoutTarget(WorkoutTargetType.STROKE_RATE, low = down, high = up)

            else -> throw IllegalArgumentException("remindType")
        }
    }

    private fun kmhToMmPerS(kmh: Long): Long = (kmh * 1000 / 3.6).roundToLong()

    private fun decodeWeight(raw: String): Pair<WorkoutWeightType?, Int?> {
        val parts = raw.split('-')
        if (parts.size != 2) throw IllegalArgumentException("strengthWeight")
        val value = parts[0].trim().toLong()
        val mode = parts[1].trim().toInt()
        return when (mode) {
            0 -> {
                if (value != 0L) throw IllegalArgumentException("strengthWeight")
                null to null
            }

            ZeppOsWorkoutCodes.WEIGHT_MODE_MANUAL -> WorkoutWeightType.MANUAL to (value * 100).toInt()
            ZeppOsWorkoutCodes.WEIGHT_MODE_RM -> {
                if (value % 10L != 0L) throw IllegalArgumentException("strengthWeight")
                WorkoutWeightType.RM to (value / 10L).toInt()
            }

            ZeppOsWorkoutCodes.WEIGHT_MODE_BODY_WEIGHT -> {
                if (value != 0L) throw IllegalArgumentException("strengthWeight")
                WorkoutWeightType.BODY_WEIGHT to null
            }

            else -> throw IllegalArgumentException("strengthWeight")
        }
    }

    private fun decodeExerciseId(actionType: Int): String? {
        if (actionType == 0) return null
        return ZeppOsExerciseCatalog.INSTANCE.exerciseForActionType(actionType)?.id
            ?: throw IllegalArgumentException("actionType")
    }
}
