package nodomain.freeyourgadget.gadgetbridge.util.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.aggregate.AggregationResultGroupedByDuration
import androidx.health.connect.client.aggregate.AggregationResultGroupedByPeriod
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.response.ChangesResponse
import androidx.health.connect.client.response.InsertRecordsResponse
import androidx.health.connect.client.response.ReadRecordResponse
import androidx.health.connect.client.response.ReadRecordsResponse
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.runBlocking
import nodomain.freeyourgadget.gadgetbridge.util.healthconnect.syncers.WorkoutSyncerUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class HealthConnectWorkoutDeletionTest {
    @Test
    fun deleteWorkoutFromHealthConnect_deletesExactIdsAndNeverRangeDeletes() = runBlocking {
        val client = CapturingClient()
        val snapshot = testSnapshot()

        HealthConnectWorkoutDeletion.deleteWorkoutFromHealthConnect(client, allWorkoutPermissions(), snapshot)

        assertEquals(WorkoutSyncerUtils.WORKOUT_RECORD_TYPES.size, client.idDeletes.size)
        assertEquals(expectedDeleteOrder(), client.idDeletes.map { it.recordType })
        for (recordType in WorkoutSyncerUtils.WORKOUT_RECORD_TYPES) {
            val delete = client.idDeletes.single { it.recordType == recordType.recordClass }
            assertTrue(delete.recordIds.isEmpty())
            assertEquals(
                listOf(WorkoutSyncerUtils.workoutClientRecordId(recordType.key, snapshot.summaryId)),
                delete.clientRecordIds
            )
        }

        assertTrue(client.rangeDeletes.isEmpty())
    }

    @Test
    fun deleteWorkoutFromHealthConnect_missingSessionIdDoesNotFallBackToRangeDelete() = runBlocking {
        val client = CapturingClient(
            idDeleteFailures = mapOf(
                ExerciseSessionRecord::class to IllegalArgumentException("No records found for clientRecordId")
            )
        )

        HealthConnectWorkoutDeletion.deleteWorkoutFromHealthConnect(
            client,
            allWorkoutPermissions(),
            testSnapshot()
        )

        assertEquals(expectedDeleteOrder(), client.idDeletes.map { it.recordType })
        assertTrue(client.rangeDeletes.isEmpty())
    }

    @Test
    fun deleteWorkoutFromHealthConnect_deletesSessionWhenChildPermissionMissing() = runBlocking {
        val client = CapturingClient()
        val grantedPermissions = setOf(HealthPermission.getWritePermission(ExerciseSessionRecord::class))

        HealthConnectWorkoutDeletion.deleteWorkoutFromHealthConnect(client, grantedPermissions, testSnapshot())

        // Some child types (e.g. cadence) share the exercise write permission with the session.
        val expected = expectedDeleteOrder()
            .filter { HealthPermission.getWritePermission(it) in grantedPermissions }
        assertTrue(expected.size < WorkoutSyncerUtils.WORKOUT_RECORD_TYPES.size)
        assertEquals(expected, client.idDeletes.map { it.recordType })
        assertEquals(ExerciseSessionRecord::class, client.idDeletes.last().recordType)
        assertTrue(client.rangeDeletes.isEmpty())
    }

    @Test
    fun deleteWorkoutFromHealthConnect_missingChildRecordDoesNotKeepSession() = runBlocking {
        val client = CapturingClient(
            idDeleteFailures = mapOf(
                PowerRecord::class to IllegalArgumentException("No records found for clientRecordId")
            )
        )

        HealthConnectWorkoutDeletion.deleteWorkoutFromHealthConnect(
            client,
            allWorkoutPermissions(),
            testSnapshot()
        )

        assertEquals(expectedDeleteOrder(), client.idDeletes.map { it.recordType })
        assertTrue(client.rangeDeletes.isEmpty())
    }

    @Test
    fun deleteWorkoutFromHealthConnect_childFailureKeepsSessionAndAttemptsChildrenFirst() = runBlocking {
        val client = CapturingClient(
            idDeleteFailures = mapOf(
                HeartRateRecord::class to IllegalStateException("Health Connect delete failed")
            )
        )

        HealthConnectWorkoutDeletion.deleteWorkoutFromHealthConnect(
            client,
            allWorkoutPermissions(),
            testSnapshot()
        )

        assertEquals(expectedChildDeleteOrder(), client.idDeletes.map { it.recordType })
        assertTrue(client.idDeletes.none { it.recordType == ExerciseSessionRecord::class })
        assertTrue(client.rangeDeletes.isEmpty())
    }

    private fun testSnapshot() = HealthConnectWorkoutDeletion.WorkoutSummarySnapshot(summaryId = 42L)

    private fun allWorkoutPermissions(): Set<String> {
        return WorkoutSyncerUtils.WORKOUT_RECORD_TYPES
            .map { HealthPermission.getWritePermission(it.recordClass) }
            .toSet()
    }

    private fun expectedChildDeleteOrder(): List<KClass<out Record>> {
        return WorkoutSyncerUtils.WORKOUT_RECORD_TYPES
            .filter { it.key != WorkoutSyncerUtils.RECORD_TYPE_SESSION }
            .map { it.recordClass }
    }

    private fun expectedDeleteOrder(): List<KClass<out Record>> {
        return expectedChildDeleteOrder() + ExerciseSessionRecord::class
    }

    private data class IdDelete(
        val recordType: KClass<out Record>,
        val recordIds: List<String>,
        val clientRecordIds: List<String>
    )

    private data class RangeDelete(
        val recordType: KClass<out Record>,
        val timeRangeFilter: TimeRangeFilter
    )

    private class CapturingClient(
        private val idDeleteFailures: Map<KClass<out Record>, Exception> = emptyMap()
    ) : HealthConnectClient {
        val idDeletes = mutableListOf<IdDelete>()
        val rangeDeletes = mutableListOf<RangeDelete>()

        override suspend fun insertRecords(records: List<Record>): InsertRecordsResponse = throw NotImplementedError()
        override val permissionController: PermissionController get() = throw NotImplementedError()
        override suspend fun updateRecords(records: List<Record>) = throw NotImplementedError()
        override suspend fun deleteRecords(
            recordType: KClass<out Record>,
            recordIdsList: List<String>,
            clientRecordIdsList: List<String>
        ) {
            idDeletes.add(IdDelete(recordType, recordIdsList, clientRecordIdsList))
            idDeleteFailures[recordType]?.let { throw it }
        }

        override suspend fun deleteRecords(recordType: KClass<out Record>, timeRangeFilter: TimeRangeFilter) {
            rangeDeletes.add(RangeDelete(recordType, timeRangeFilter))
        }

        override suspend fun <T : Record> readRecord(recordType: KClass<T>, recordId: String): ReadRecordResponse<T> = throw NotImplementedError()
        override suspend fun <T : Record> readRecords(request: ReadRecordsRequest<T>): ReadRecordsResponse<T> = throw NotImplementedError()
        override suspend fun aggregate(request: AggregateRequest): AggregationResult = throw NotImplementedError()
        override suspend fun aggregateGroupByDuration(request: AggregateGroupByDurationRequest): List<AggregationResultGroupedByDuration> = throw NotImplementedError()
        override suspend fun aggregateGroupByPeriod(request: AggregateGroupByPeriodRequest): List<AggregationResultGroupedByPeriod> = throw NotImplementedError()
        override suspend fun getChangesToken(request: ChangesTokenRequest): String = throw NotImplementedError()
        override suspend fun getChanges(changesToken: String): ChangesResponse = throw NotImplementedError()
    }
}
