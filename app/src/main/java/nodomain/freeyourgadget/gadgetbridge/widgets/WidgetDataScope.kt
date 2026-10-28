package nodomain.freeyourgadget.gadgetbridge.widgets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StepAnalysis
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.SleepRangeUtils
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.GregorianCalendar
import kotlin.math.roundToInt

/**
 * The selected-day query parameters shared by every widget instance in one refresh.
 */
data class DashboardQuery(
    val timeFrom: Int,
    val timeTo: Int,
) {
    companion object {
        /**
         * The query for the 24 hours that end at the end of [day].
         */
        fun forDay(day: LocalDate): DashboardQuery {
            val timeTo = day.atTime(LocalTime.MAX).atZone(ZoneId.systemDefault()).toEpochSecond().toInt()
            return DashboardQuery(DateTimeUtils.shiftDays(timeTo, -1), timeTo)
        }
    }
}

/**
 * Per-widget-instance view over one dashboard refresh: the selected day ([query]) and the devices
 * this instance resolved to.
 */
class WidgetDataScope(
    val query: DashboardQuery,
    val devices: List<GBDevice>,
) {
    val zone: ZoneId get() = ZoneId.systemDefault()

    /**
     * The selected day.
     */
    val day: LocalDate get() = Instant.ofEpochSecond(query.timeTo.toLong()).atZone(zone).toLocalDate()

    val isToday: Boolean get() = day == LocalDate.now(zone)

    /**
     * Epoch second at which the selected day starts.
     */
    val dayStart: Long get() = day.atStartOfDay(zone).toEpochSecond()

    /**
     * Epoch second at which the day [days] days before the selected day starts.
     */
    fun dayStartMinusDays(days: Int): Long = day.minusDays(days.toLong()).atStartOfDay(zone).toEpochSecond()

    /**
     * How many hours of the selected day have begun: all of them for a past day.
     */
    val hoursStarted: Int
        get() = if (isToday) {
            WidgetHistory.hoursStarted(dayStart, System.currentTimeMillis() / 1000L)
        } else {
            WidgetHistory.HOURS_PER_DAY
        }

    /**
     * Runs [block] against a freshly-acquired read handle, closed when [block] returns.
     */
    suspend fun <T> db(block: (DBHandler) -> T): T = withContext(Dispatchers.IO) {
        GBApplication.acquireDbReadOnly().use { block(it) }
    }

    private fun dailyTotals(db: DBHandler, device: GBDevice): DailyTotals {
        val day = GregorianCalendar.getInstance()
        day.timeInMillis = query.timeTo * 1000L
        return DailyTotals.getDailyTotalsForDevice(device, day, db)
    }

    suspend fun allSamples(device: GBDevice): List<ActivitySample> = db { db ->
        val provider = device.deviceCoordinator.getSampleProvider(device, db.daoSession)
        provider?.getAllActivitySamples(query.timeFrom, query.timeTo) ?: emptyList()
    }

    /**
     * The device's numeric DB id.
     */
    suspend fun dbDeviceId(device: GBDevice): Long = db { db ->
        DBHelper.getDevice(device, db.daoSession).id ?: 0L
    }

    suspend fun workoutSummaries(deviceIds: List<Long>): List<BaseActivitySummary> {
        if (deviceIds.isEmpty()) return emptyList()
        return db { db ->
            db.daoSession.baseActivitySummaryDao.queryBuilder().where(
                BaseActivitySummaryDao.Properties.StartTime.gt(Date(query.timeFrom * 1000L)),
                BaseActivitySummaryDao.Properties.EndTime.lt(Date(query.timeTo * 1000L)),
                BaseActivitySummaryDao.Properties.DeviceId.`in`(deviceIds),
            ).build().list()
        }
    }

    //
    // Aggregates shared by multiple widgets.
    //

    suspend fun stepsTotal(): Int = db { db ->
        devices.filter { it.deviceCoordinator.supportsStepCounter(it) }
            .sumOf { dailyTotals(db, it).steps.toInt() }
    }

    fun stepsGoalFactor(stepsTotal: Int): Float {
        val goal = ActivityUser().stepsGoal.toFloat()
        return (stepsTotal / goal).coerceAtMost(1f)
    }

    suspend fun sleepMinutesTotal(): Long = db { db ->
        val sleepValues = devices.filter { it.deviceCoordinator.supportsSleepMeasurement(it) }
            .map { dailyTotals(db, it).sleep }
            .filter { it > 0 }
        if (sleepValues.isEmpty()) 0L else sleepValues.average().roundToInt().toLong()
    }

    /**
     * Light, deep, REM and awake minutes, averaged over the devices that recorded sleep.
     */
    suspend fun sleepStageMinutes(): LongArray = db { db ->
        val stages = devices.filter { it.deviceCoordinator.supportsSleepMeasurement(it) }
            .map { dailyTotals(db, it) }
            .filter { it.sleep > 0 }
            .map { it.sleepStages }
        LongArray(4) { stage -> if (stages.isEmpty()) 0L else stages.map { it[stage] }.average().roundToInt().toLong() }
    }

    fun sleepGoalFactor(sleepMinutesTotal: Long): Float {
        val goal = ActivityUser().sleepDurationGoal
        return (sleepMinutesTotal.toFloat() / goal).coerceAtMost(1f)
    }

    suspend fun distanceTotal(): Float = db { db ->
        val stepLength = ActivityUser().stepLengthCm
        var totalDistanceCm = 0L
        for (dev in devices.filter { it.deviceCoordinator.supportsStepCounter(it) }) {
            val totals = dailyTotals(db, dev)
            totalDistanceCm += if (totals.steps > 0 && totals.distance > 0) {
                totals.distance
            } else {
                totals.steps * stepLength
            }
        }
        totalDistanceCm * 0.01f
    }

    fun distanceGoalFactor(distanceTotal: Float): Float {
        val goal = ActivityUser().distanceGoalMeters.toFloat()
        return (distanceTotal / goal).coerceAtMost(1f)
    }

    suspend fun activeCaloriesTotal(): Int = db { db ->
        val total = devices.filter { it.deviceCoordinator.supportsActiveCalories(it) }
            .sumOf { dailyTotals(db, it).activeCalories.toInt() }
        // Convert calories to kcal
        total / 1000
    }

    fun activeCaloriesGoalFactor(activeCaloriesTotal: Int): Float {
        val goal = ActivityUser().caloriesBurntGoal.toFloat()
        return (activeCaloriesTotal / goal).coerceAtMost(1f)
    }

    suspend fun restingCaloriesTotal(): Int = db { db ->
        var total = 0
        var count = 0
        for (dev in devices.filter { it.deviceCoordinator.supportsActiveCalories(it) }) {
            val resting = dailyTotals(db, dev).restingCalories.toInt()
            if (resting > 0) {
                total += resting
                count++
            }
        }
        if (count == 0) 0 else (total / count.toFloat()).roundToInt()
    }

    /**
     * Per-hour sums of [valueOf] over the selected day's activity samples of [devices].
     */
    suspend fun hourlySums(devices: List<GBDevice>, valueOf: (ActivitySample) -> Float): FloatArray {
        val selectedDay = day
        val sums = FloatArray(WidgetHistory.HOURS_PER_DAY)
        for (dev in devices) {
            for (sample in allSamples(dev)) {
                val second = WidgetHistory.secondOfDay(sample.timestamp.toLong(), selectedDay)
                if (second < 0) continue
                val hour = second / 3600
                if (hour in sums.indices) {
                    sums[hour] += valueOf(sample).coerceAtLeast(0f)
                }
            }
        }
        return sums
    }

    /**
     * Active minutes per hour of the selected day, from the step sessions of the step counting devices.
     */
    suspend fun hourlyActiveMinutes(): FloatArray {
        val intervals = devices.filter { it.deviceCoordinator.supportsStepCounter(it) }.flatMap { dev ->
            StepAnalysis().calculateStepSessions(allSamples(dev), emptyList())
                .map { it.startTime.time / 1000L to it.endTime.time / 1000L }
        }
        return WidgetHistory.minutesPerHour(intervals, dayStart)
    }

    /**
     * The night ending on the selected day as stretches on the lanes awake (0), REM (1), light (2) and deep (3),
     * from the sleep measuring device with the most sleep samples.
     */
    suspend fun sleepStretches(): List<HypnogramSegment> = db { db ->
        val (from, toExclusive) = SleepRangeUtils.getSleepRange(query.timeTo)
        val to = toExclusive - 1
        val nights = devices.filter { it.deviceCoordinator.supportsSleepMeasurement(it) }.map { dev ->
            val provider = dev.deviceCoordinator.getSampleProvider(dev, db.daoSession)
            provider?.getAllActivitySamples(from, to).orEmpty().mapNotNull { sample ->
                sleepLane(sample.kind)?.let { sample.timestamp.toLong() to it }
            }
        }
        val night = nights.maxByOrNull { it.size }.orEmpty()
        val asleep = night.filter { it.second != SLEEP_LANE_AWAKE }
        if (asleep.isEmpty()) {
            emptyList()
        } else {
            val first = asleep.minOf { it.first }
            val last = asleep.maxOf { it.first }
            WidgetHistory.laneStretches(night.filter { it.first in first..last }, SLEEP_SAMPLE_SECONDS, SLEEP_MAX_GAP_SECONDS)
        }
    }

    private fun sleepLane(kind: ActivityKind): Int? = when (kind) {
        ActivityKind.AWAKE_SLEEP -> SLEEP_LANE_AWAKE
        ActivityKind.REM_SLEEP -> 1
        ActivityKind.LIGHT_SLEEP, ActivityKind.SLEEP_ANY -> 2
        ActivityKind.DEEP_SLEEP -> 3
        else -> null
    }

    suspend fun activeMinutes(device: GBDevice): Long {
        val samples = allSamples(device)
        val stepAnalysis = StepAnalysis()
        val sessions = stepAnalysis.calculateStepSessions(samples, emptyList())
        val summary = stepAnalysis.calculateSummary(sessions, sessions.isEmpty())
        return (summary.endTime.time - summary.startTime.time) / 1000 / 60
    }

    suspend fun activeMinutesTotal(): Long =
        devices.filter { it.deviceCoordinator.supportsStepCounter(it) }
            .sumOf { activeMinutes(it) }

    fun activeMinutesGoalFactor(activeMinutesTotal: Long): Float {
        val goal = ActivityUser().activeTimeGoalMinutes.toFloat()
        return (activeMinutesTotal / goal).coerceAtMost(1f)
    }

    private companion object {
        const val SLEEP_SAMPLE_SECONDS = 60L
        const val SLEEP_MAX_GAP_SECONDS = 120L
        const val SLEEP_LANE_AWAKE = 0
    }
}
