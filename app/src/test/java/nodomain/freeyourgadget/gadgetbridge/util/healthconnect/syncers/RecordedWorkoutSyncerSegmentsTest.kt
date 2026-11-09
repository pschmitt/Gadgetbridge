package nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers

import android.content.Context
import androidx.health.connect.client.records.ExerciseSegment
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Device
import androidx.health.connect.client.records.metadata.Metadata
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivityPoint
import nodomain.freeyourgadget.gadgetbridge.model.ActivityTrack
import nodomain.freeyourgadget.gadgetbridge.model.DeviceType
import nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.fit.enums.ExerciseCategory
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito
import java.time.Instant
import java.time.ZoneOffset

class RecordedWorkoutSyncerSegmentsTest {

    private val device = GBDevice("00:11:22:33:44:55", "Testie", "Testie Alias", "Test Folder", DeviceType.TEST)
    private val metadata = Metadata.unknownRecordingMethod(
        Device(type = Device.TYPE_WATCH, manufacturer = "test", model = "test")
    )
    private val context: Context = Mockito.mock(Context::class.java)

    @Test
    fun detailedWorkoutKeepsSetSegmentsOnTheSessionRecord() {
        val start = Instant.ofEpochSecond(1000)
        val end = Instant.ofEpochSecond(3000)
        val workout = BaseActivitySummary()
        workout.name = "Legs"
        val records = mutableListOf<Record>()

        RecordedWorkoutSyncer.processDetailedWorkout(
            workout,
            listOf(ActivityPoint()),
            listOf(
                ActivityTrack.SetInfo(1100L, 60.0, 10, null, 1, null, 0, ExerciseCategory.CATEGORY_SQUAT, null)
            ),
            start,
            end,
            ZoneOffset.UTC,
            ZoneOffset.UTC,
            metadata,
            1L,
            0L,
            emptySet(),
            records,
            device,
            ActivityKind.STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            context
        )

        val session = records.filterIsInstance<ExerciseSessionRecord>().single()
        assertEquals(1, session.segments.size)
        assertEquals(Instant.ofEpochSecond(1100), session.segments[0].startTime)
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, session.segments[0].segmentType)
        assertEquals(10, session.segments[0].repetitions)
    }

    @Test
    fun aggregateWorkoutKeepsSetSegmentsOnTheSessionRecord() {
        val start = Instant.ofEpochSecond(1000)
        val end = Instant.ofEpochSecond(3000)
        val workout = BaseActivitySummary()
        workout.name = "Legs"
        val records = mutableListOf<Record>()

        RecordedWorkoutSyncer.processAggregateWorkout(
            device,
            workout,
            listOf(
                ActivityTrack.SetInfo(1100L, 60.0, 10, null, 1, null, 0, ExerciseCategory.CATEGORY_SQUAT, null)
            ),
            start,
            end,
            ZoneOffset.UTC,
            ZoneOffset.UTC,
            metadata,
            1L,
            0L,
            emptySet(),
            records,
            "Testie",
            ActivityKind.STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            context
        )

        val session = records.filterIsInstance<ExerciseSessionRecord>().single()
        assertEquals(1, session.segments.size)
        assertEquals(Instant.ofEpochSecond(1100), session.segments[0].startTime)
        assertEquals(ExerciseSegment.EXERCISE_SEGMENT_TYPE_SQUAT, session.segments[0].segmentType)
    }
}
