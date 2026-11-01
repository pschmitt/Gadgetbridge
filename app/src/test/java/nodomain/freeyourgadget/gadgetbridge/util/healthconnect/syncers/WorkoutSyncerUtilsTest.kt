package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.ExerciseCategory
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class WorkoutSyncerUtilsTest {
    @Test
    fun workoutClientRecordIds_stableAndUniquePerRecordType() {
        val summaryId = 1234L

        assertEquals(
            "gb-workout-session-1234",
            WorkoutSyncerUtils.workoutClientRecordId(WorkoutSyncerUtils.RECORD_TYPE_SESSION, summaryId)
        )

        val ids = WorkoutSyncerUtils.WORKOUT_RECORD_TYPES
            .map { WorkoutSyncerUtils.workoutClientRecordId(it.key, summaryId) }

        assertEquals(WorkoutSyncerUtils.WORKOUT_RECORD_TYPES.size, ids.toSet().size)
    }

    @Test
    fun workoutRecordMetadata_carriesStableClientIdAndSuppliedVersion() {
        val base = Metadata.autoRecorded(Device(Device.TYPE_WATCH, "Acme", "Band"))
        val version = 1_700_000_500_000L

        val metadata = WorkoutSyncerUtils.workoutRecordMetadata(
            base,
            WorkoutSyncerUtils.RECORD_TYPE_DISTANCE,
            1234L,
            version
        )

        assertEquals("gb-workout-distance-1234", metadata.clientRecordId)
        assertEquals(version, metadata.clientRecordVersion)
    }

    @Test
    fun testMapActivityKind_running() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.RUNNING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.OUTDOOR_RUNNING)
        )
    }

    @Test
    fun testMapActivityKind_walking() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.WALKING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.OUTDOOR_WALKING)
        )
    }

    @Test
    fun testMapActivityKind_cycling() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.CYCLING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.OUTDOOR_CYCLING)
        )
    }

    @Test
    fun testMapActivityKind_indoorCycling() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.INDOOR_CYCLING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.SPINNING)
        )
    }

    @Test
    fun testMapActivityKind_swimming() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.SWIMMING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.POOL_SWIM)
        )
    }

    @Test
    fun testMapActivityKind_openWaterSwimming() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.SWIMMING_OPENWATER)
        )
    }

    @Test
    fun testMapActivityKind_treadmill() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.TREADMILL)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.INDOOR_RUNNING)
        )
    }

    @Test
    fun testMapActivityKind_strengthTraining() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.STRENGTH_TRAINING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.WEIGHTLIFTING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BOOT_CAMP,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.CROSSFIT)
        )
    }

    @Test
    fun testMapActivityKind_yoga() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.YOGA)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.TAI_CHI)
        )
    }

    @Test
    fun testMapActivityKind_sports() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_SOCCER,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.SOCCER)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.BASKETBALL)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_TENNIS,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.TENNIS)
        )
    }

    @Test
    fun testMapActivityKind_hiking() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.HIKING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.TREKKING)
        )
    }

    @Test
    fun testMapActivityKind_climbing() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.CLIMBING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_ROCK_CLIMBING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.ROCK_CLIMBING)
        )
    }

    @Test
    fun testMapActivityKind_skiing() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_SKIING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.SKIING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_SNOWBOARDING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.SNOWBOARDING)
        )
    }

    @Test
    fun testMapActivityKind_martialArts() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.MARTIAL_ARTS)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.KARATE)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.TAEKWONDO)
        )
    }

    @Test
    fun testMapActivityKind_dancing() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.DANCE)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_DANCING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.ZUMBA)
        )
    }

    @Test
    fun testMapActivityKind_hiit() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.HIIT)
        )
    }

    @Test
    fun testMapActivityKind_pilates() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.PILATES)
        )
    }

    @Test
    fun testMapActivityKind_rowing() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.ROWING)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.ROWING_MACHINE)
        )
    }

    @Test
    fun testMapActivityKind_golf() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_GOLF,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.GOLF)
        )
    }

    @Test
    fun testMapActivityKind_guidedBreathing() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.BREATHWORK)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_GUIDED_BREATHING,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.MEDITATION)
        )
    }

    @Test
    fun testMapActivityKind_unknownActivity() {
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.UNKNOWN)
        )
        assertEquals(
            ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT,
            WorkoutSyncerUtils.mapActivityKindToExerciseType(ActivityKind.ARCHERY)
        )
    }

    @Test
    fun testMapActivityKind_allTypesMapToValidExerciseType() {
        // Test that all activity kinds map to a valid exercise type (no exceptions thrown)
        for (activityKind in ActivityKind.entries) {
            val exerciseType = WorkoutSyncerUtils.mapActivityKindToExerciseType(activityKind)
            assertTrue("Exercise type should be a valid int", exerciseType >= 0)
        }
    }

    @Test
    fun buildExerciseSegments_skipsNonStrengthSessions() {
        val sets = listOf(set(1000L, 60.0, 10, ExerciseCategory.CATEGORY_SQUAT))

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(900),
            Instant.ofEpochSecond(3000),
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        )

        assertTrue(segments.isEmpty())
    }

    @Test
    fun buildExerciseSegments_mapsActiveSetsAndSkipsRest() {
        val sets = listOf(
            set(1000L, 45.0, 10, ExerciseCategory.CATEGORY_SQUAT),
            set(1075L, 60.0, null, null, setType = 0),
            set(1160L, 30.0, 8, ExerciseCategory.CATEGORY_BENCH_PRESS)
        )

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(900),
            Instant.ofEpochSecond(3000),
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertEquals(2, segments.size)
        assertEquals(Instant.ofEpochSecond(1000), segments[0].startTime)
        assertEquals(Instant.ofEpochSecond(1045), segments[0].endTime)
        assertEquals(10, segments[0].repetitions)
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, segments[0].segmentType)
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS, segments[1].segmentType)
    }

    @Test
    fun buildExerciseSegments_clipsToSessionBoundsAndProducesValidSession() {
        val sessionStart = Instant.ofEpochSecond(1000)
        val sessionEnd = Instant.ofEpochSecond(2000)
        val sets = listOf(
            set(900L, 200.0, 5, ExerciseCategory.CATEGORY_SQUAT),
            set(1100L, 300.0, 5, ExerciseCategory.CATEGORY_DEADLIFT),
            set(2500L, 30.0, 5, ExerciseCategory.CATEGORY_PLANK),
            set(1500L, null, 5, ExerciseCategory.CATEGORY_LUNGE)
        )

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            sessionStart,
            sessionEnd,
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertEquals(3, segments.size)
        assertEquals(sessionStart, segments.first().startTime)
        assertTrue(segments.all { !it.startTime.isBefore(sessionStart) && !it.endTime.isAfter(sessionEnd) })
        assertTrue(segments.zipWithNext().all { (a, b) -> !a.endTime.isAfter(b.startTime) })

        // Health Connect validates segment bounds, ordering, and session compatibility
        // in the record constructor, so a segment list it rejects never reaches the service.
        ExerciseSessionRecord(
            startTime = sessionStart,
            startZoneOffset = ZoneOffset.UTC,
            endTime = sessionEnd,
            endZoneOffset = ZoneOffset.UTC,
            exerciseType = ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            segments = segments,
            metadata = metadata()
        )
    }

    @Test
    fun buildExerciseSegments_clipsSetThatRunsIntoTheNextOne() {
        val sets = listOf(
            set(1000L, 600.0, 5, ExerciseCategory.CATEGORY_SQUAT),
            set(1200L, 60.0, 5, ExerciseCategory.CATEGORY_BENCH_PRESS)
        )

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(900),
            Instant.ofEpochSecond(3000),
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertEquals(2, segments.size)
        assertEquals(Instant.ofEpochSecond(1000), segments[0].startTime)
        assertEquals(Instant.ofEpochSecond(1200), segments[0].endTime)
        assertEquals(Instant.ofEpochSecond(1200), segments[1].startTime)
        assertEquals(Instant.ofEpochSecond(1260), segments[1].endTime)
    }

    @Test
    fun buildExerciseSegments_clipsSetThatOverrunsTheSessionEnd() {
        val sessionEnd = Instant.ofEpochSecond(2000)
        val sets = listOf(set(1500L, 5000.0, 5, ExerciseCategory.CATEGORY_SQUAT))

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(1000),
            sessionEnd,
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertEquals(1, segments.size)
        assertEquals(Instant.ofEpochSecond(1500), segments[0].startTime)
        assertEquals(sessionEnd, segments[0].endTime)
    }

    @Test
    fun mapExerciseCategoryToSegmentType_fallsBackForUnmappedCategories() {
        assertEquals(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
            WorkoutSyncerUtils.mapExerciseCategoryToSegmentType(null)
        )
        assertEquals(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
            WorkoutSyncerUtils.mapExerciseCategoryToSegmentType(ExerciseCategory.CATEGORY_UNKNOWN)
        )
        assertEquals(
            ExerciseSegment.EXERCISE_SEGMENT_TYPE_OTHER_WORKOUT,
            WorkoutSyncerUtils.mapExerciseCategoryToSegmentType(ExerciseCategory.CATEGORY_ROW)
        )
    }

    @Test
    fun mapExerciseCategoryToSegmentType_mapsEachKnownCategory() {
        val expected = mapOf(
            ExerciseCategory.CATEGORY_BENCH_PRESS to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BENCH_PRESS,
            ExerciseCategory.CATEGORY_CRUNCH to ExerciseSegment.EXERCISE_SEGMENT_TYPE_CRUNCH,
            ExerciseCategory.CATEGORY_CURL to ExerciseSegment.EXERCISE_SEGMENT_TYPE_ARM_CURL,
            ExerciseCategory.CATEGORY_DEADLIFT to ExerciseSegment.EXERCISE_SEGMENT_TYPE_DEADLIFT,
            ExerciseCategory.CATEGORY_HIP_RAISE to ExerciseSegment.EXERCISE_SEGMENT_TYPE_HIP_THRUST,
            ExerciseCategory.CATEGORY_HIP_SWING to ExerciseSegment.EXERCISE_SEGMENT_TYPE_KETTLEBELL_SWING,
            ExerciseCategory.CATEGORY_HYPEREXTENSION to ExerciseSegment.EXERCISE_SEGMENT_TYPE_BACK_EXTENSION,
            ExerciseCategory.CATEGORY_LATERAL_RAISE to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LATERAL_RAISE,
            ExerciseCategory.CATEGORY_LEG_CURL to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_CURL,
            ExerciseCategory.CATEGORY_LEG_RAISE to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LEG_RAISE,
            ExerciseCategory.CATEGORY_LUNGE to ExerciseSegment.EXERCISE_SEGMENT_TYPE_LUNGE,
            ExerciseCategory.CATEGORY_PLANK to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PLANK,
            ExerciseCategory.CATEGORY_PULL_UP to ExerciseSegment.EXERCISE_SEGMENT_TYPE_PULL_UP,
            ExerciseCategory.CATEGORY_SHOULDER_PRESS to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SHOULDER_PRESS,
            ExerciseCategory.CATEGORY_SIT_UP to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SIT_UP,
            ExerciseCategory.CATEGORY_SQUAT to ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT
        )

        for ((category, segmentType) in expected) {
            assertEquals(category.name, segmentType, WorkoutSyncerUtils.mapExerciseCategoryToSegmentType(category))
        }
    }

    @Test
    fun mapExerciseCategoryToSegmentType_allCategoriesAreValidInsideStrengthSessions() {
        val start = Instant.ofEpochSecond(1000)
        val end = Instant.ofEpochSecond(2000)
        val sessionTypes = listOf(
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING
        )

        for (category in ExerciseCategory.entries) {
            val segment = ExerciseSegment(
                start,
                end,
                WorkoutSyncerUtils.mapExerciseCategoryToSegmentType(category),
                0
            )
            for (sessionType in sessionTypes) {
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = ZoneOffset.UTC,
                    endTime = end,
                    endZoneOffset = ZoneOffset.UTC,
                    exerciseType = sessionType,
                    segments = listOf(segment),
                    metadata = metadata()
                )
            }
        }
    }

    @Test
    fun buildExerciseSegments_treatsMissingSetTypeAsActive() {
        val sets = listOf(set(1000L, 30.0, 10, ExerciseCategory.CATEGORY_SQUAT, setType = null))

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(900),
            Instant.ofEpochSecond(3000),
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertEquals(1, segments.size)
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, segments[0].segmentType)
    }

    @Test
    fun buildExerciseSegments_usesNextSetStartWhenDurationIsMissing() {
        val sets = listOf(
            set(1000L, null, 10, ExerciseCategory.CATEGORY_SQUAT),
            set(1300L, 30.0, 8, ExerciseCategory.CATEGORY_BENCH_PRESS)
        )

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(900),
            Instant.ofEpochSecond(3000),
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertEquals(2, segments.size)
        assertEquals(Instant.ofEpochSecond(1000), segments[0].startTime)
        assertEquals(Instant.ofEpochSecond(1300), segments[0].endTime)
        assertEquals(Instant.ofEpochSecond(1300), segments[1].startTime)
        assertEquals(Instant.ofEpochSecond(1330), segments[1].endTime)
    }

    @Test
    fun buildExerciseSegments_clipsDurationlessSetAtTheNextSetOfAnyType() {
        val sets = listOf(
            set(1000L, null, 10, ExerciseCategory.CATEGORY_SQUAT),
            set(1060L, 60.0, null, null, setType = 0),
            set(1120L, 30.0, 8, ExerciseCategory.CATEGORY_BENCH_PRESS)
        )

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(900),
            Instant.ofEpochSecond(3000),
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertEquals(2, segments.size)
        assertEquals(Instant.ofEpochSecond(1000), segments[0].startTime)
        assertEquals(Instant.ofEpochSecond(1060), segments[0].endTime)
        assertEquals(Instant.ofEpochSecond(1120), segments[1].startTime)
        assertEquals(Instant.ofEpochSecond(1150), segments[1].endTime)
    }

    @Test
    fun buildExerciseSegments_dropsTrailingSetWithoutDuration() {
        val sets = listOf(set(1000L, null, 10, ExerciseCategory.CATEGORY_SQUAT))

        val segments = WorkoutSyncerUtils.buildExerciseSegments(
            sets,
            Instant.ofEpochSecond(900),
            Instant.ofEpochSecond(3000),
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        )

        assertTrue(segments.isEmpty())
    }

    private fun set(
        startTimeSec: Long,
        durationSec: Double?,
        repetitions: Int?,
        category: ExerciseCategory?,
        setType: Int? = 1
    ) = ActivityTrack.SetInfo(startTimeSec, durationSec, repetitions, null, setType, null, null, category, null)

    private fun metadata() = Metadata.autoRecorded(Device(Device.TYPE_WATCH, "Acme", "Test"))
}
