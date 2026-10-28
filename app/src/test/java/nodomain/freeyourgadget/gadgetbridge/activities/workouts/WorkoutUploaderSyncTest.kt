package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.WorkoutUpload
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.test.TestBase
import nodomain.freeyourgadget.gadgetbridge.util.GBPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Date

/**
 * Covers how [WorkoutUploader.syncWorkout] treats a workout the service is recorded as holding,
 * depending on whether the service still has it.
 */
class WorkoutUploaderSyncTest : TestBase() {
    private class FakeTarget(private val lookup: RemoteLookup?) : WorkoutUploadTarget {
        val lookedUp = mutableListOf<String>()
        var uploads = 0

        override val service = 99
        override val nameRes = R.string.pref_category_endurain
        override val uploadMenuItemRes = R.id.activity_action_upload_to_endurain
        override val format = WorkoutPayloadFormat.FIT
        override val requiresTrack = false
        override val failureNotificationId = 0
        override val uploadedRes = R.string.endurain_successfully_uploaded_toast

        override fun isLoggedIn(context: Context) = true
        override fun isAutoUploadEnabled(prefs: GBPrefs) = false
        override fun failureMessage(context: Context, reason: String) = reason
        override fun uploadFailedMessage(context: Context, reason: String?) = reason ?: ""
        override fun activityUrl(context: Context, remoteActivityId: String): String? = null

        override fun upload(context: Context, summary: BaseActivitySummary, payload: File): WorkoutUploader.UploadResult {
            uploads++
            return WorkoutUploader.UploadResult(true, "new-$uploads", null)
        }

        override fun updateMetadata(context: Context, remoteActivityId: String, summary: BaseActivitySummary) = true

        override fun updateTrack(
            context: Context,
            remoteActivityId: String,
            payload: File,
            summary: BaseActivitySummary,
            row: WorkoutUpload
        ) = TrackUpdateResult(success = true)

        override fun syncPhoto(
            context: Context,
            remoteActivityId: String,
            photoFile: File?,
            previousMediaId: Int?
        ): WorkoutUploader.PhotoSyncResult? = null

        override fun lookupRemote(context: Context, remoteActivityId: String): RemoteLookup? {
            lookedUp.add(remoteActivityId)
            return lookup
        }
    }

    private val summary = BaseActivitySummary().apply {
        id = 1L
        startTime = Date(1_790_000_000_000L)
        endTime = Date(1_790_001_800_000L)
        activityKind = ActivityKind.RUNNING.code
    }

    private val device by lazy { createDummyGDevice("00:00:00:00:00:01") }

    /** Records [summary] as uploaded to [target] under `old`, in step with the workout as it is. */
    private fun recordInStep(target: WorkoutUploadTarget) {
        WorkoutUploadStore.recordSuccess(
            1L, target.service, "old", null,
            sourceHash = WorkoutUploadStore.sourceHashOf(summary)
        )
    }

    private fun sync(target: WorkoutUploadTarget, verifyRemote: Boolean) =
        WorkoutUploader.syncWorkout(getContext(), device, null, target, summary, verifyRemote = verifyRemote)

    private fun row(target: WorkoutUploadTarget) =
        WorkoutUploadStore.rowsForSummaries(listOf(1L))[1L]?.get(target.service)

    @Test
    fun withoutVerifyingAnInStepWorkoutIsLeftAlone() {
        val target = FakeTarget(RemoteLookup.GONE)
        recordInStep(target)

        assertEquals(WorkoutUploader.SyncOutcome.AlreadyInStep, sync(target, verifyRemote = false))
        assertTrue(target.lookedUp.isEmpty())
        assertEquals(0, target.uploads)
    }

    @Test
    fun anActivityTheServiceStillHoldsIsNotSentAgain() {
        val target = FakeTarget(RemoteLookup.PRESENT)
        recordInStep(target)

        assertEquals(WorkoutUploader.SyncOutcome.AlreadyInStep, sync(target, verifyRemote = true))
        assertEquals(listOf("old"), target.lookedUp)
        assertEquals(0, target.uploads)
        assertEquals("old", row(target)?.remoteActivityId)
    }

    @Test
    fun anActivityDeletedOnTheServiceIsUploadedAgain() {
        val target = FakeTarget(RemoteLookup.GONE)
        recordInStep(target)

        assertEquals(WorkoutUploader.SyncOutcome.Uploaded, sync(target, verifyRemote = true))
        assertEquals(1, target.uploads)
        assertEquals("new-1", row(target)?.remoteActivityId)
        assertEquals(WorkoutUploadStore.STATUS_SUCCESS, row(target)?.status)
    }

    @Test
    fun aFailedLookupSendsNothingAndKeepsTheRow() {
        val target = FakeTarget(RemoteLookup.FAILED)
        recordInStep(target)

        assertEquals(WorkoutUploader.SyncOutcome.Failed(null), sync(target, verifyRemote = true))
        assertEquals(0, target.uploads)
        assertEquals("old", row(target)?.remoteActivityId)
        assertNull(row(target)?.lastError)
    }

    @Test
    fun aServiceThatCannotTellIsTreatedAsHoldingIt() {
        val target = FakeTarget(null)
        recordInStep(target)

        assertEquals(WorkoutUploader.SyncOutcome.AlreadyInStep, sync(target, verifyRemote = true))
        assertEquals(0, target.uploads)
    }
}
