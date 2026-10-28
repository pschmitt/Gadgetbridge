package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.database.DBHelper
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummary
import nodomain.freeyourgadget.gadgetbridge.entities.BaseActivitySummaryDao
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryJsonSummary
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryParser
import nodomain.freeyourgadget.gadgetbridge.util.DeviceHelper
import org.slf4j.LoggerFactory
import java.text.Collator
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

sealed interface StatisticsState {
    data object Loading : StatisticsState
    data class Ready(val sports: List<SportCount>) : StatisticsState
    data class Error(val cause: Throwable) : StatisticsState
}

/**
 * Loads a device's workouts once and caches parsed workouts per sport.
 */
class WorkoutStatisticsViewModel : ViewModel() {
    private val _state = MutableStateFlow<StatisticsState>(StatisticsState.Loading)
    val state: StateFlow<StatisticsState> = _state.asStateFlow()

    private var device: GBDevice? = null

    @Volatile
    private var summaries: List<BaseActivitySummary> = emptyList()
    private val workoutInputCache = ConcurrentHashMap<Int, List<WorkoutInput>>()
    private var loadJob: Job? = null
    private var loading = false

    /**
     * Does nothing if loading already started.
     */
    fun load(device: GBDevice?) {
        if (loading) {
            return
        }
        this.device = device
        startLoad(device)
    }

    fun reload() {
        device?.let { startLoad(it) }
    }

    private fun startLoad(device: GBDevice?) {
        loadJob?.cancel()
        loading = true
        // Clear summaries before the cache, so that a running workoutInputs() does not cache old results
        summaries = emptyList()
        workoutInputCache.clear()
        _state.value = StatisticsState.Loading
        loadJob = viewModelScope.launch {
            try {
                val loaded = withContext(Dispatchers.IO) { loadSummaries(device) }
                summaries = loaded
                val context = GBApplication.getContext()
                val sports = withContext(Dispatchers.Default) {
                    StatisticsAggregation.sportCounts(
                        kindCodes = loaded.map { it.activityKind },
                        nameOf = { code -> ActivityKind.fromCode(code).getLabel(context) },
                        collator = Collator.getInstance(),
                    )
                }
                _state.value = StatisticsState.Ready(sports)
                loading = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load workout statistics", e)
                _state.value = StatisticsState.Error(e)
            }
        }
    }

    suspend fun overview(kindCode: Int): SportOverview = withContext(Dispatchers.Default) {
        StatisticsAggregation.overview(
            workoutInputs(kindCode),
            ZoneId.systemDefault(),
            ActivityKind.isPaceActivity(ActivityKind.fromCode(kindCode)),
        )
    }

    suspend fun period(kindCode: Int, period: StatisticsPeriod): PeriodStats = withContext(Dispatchers.Default) {
        StatisticsAggregation.aggregate(workoutInputs(kindCode), period, ZoneId.systemDefault())
    }

    /**
     * Parsed workouts of a sport, cached. Waits for the initial load first.
     */
    private suspend fun workoutInputs(kindCode: Int): List<WorkoutInput> {
        workoutInputCache[kindCode]?.let { return it }

        loadJob?.join()

        val source = summaries
        val inputs = withContext(Dispatchers.IO) {
            val parsers = mutableMapOf<Long, ActivitySummaryParser>()
            source
                .filter { it.activityKind == kindCode }
                .mapNotNull { summary ->
                    val gbDevice = DeviceHelper.getInstance().toGBDevice(summary.device)
                        ?: return@mapNotNull null
                    val parser = parsers.getOrPut(summary.deviceId) {
                        gbDevice.deviceCoordinator.getActivitySummaryParser(gbDevice, GBApplication.app())
                    }
                    workoutInput(parser, summary)
                }
        }
        if (summaries === source) {
            workoutInputCache[kindCode] = inputs
        }
        return inputs
    }

    private fun loadSummaries(device: GBDevice?): List<BaseActivitySummary> {
        return GBApplication.acquireDbReadOnly().use { dbHandler ->
            val queryBuilder = dbHandler.daoSession.baseActivitySummaryDao.queryBuilder()

            if (device != null) {
                val dbDevice = DBHelper.findDevice(device, dbHandler.daoSession)
                    ?: return@use emptyList()
                queryBuilder.where(BaseActivitySummaryDao.Properties.DeviceId.eq(dbDevice.id))
            }

            queryBuilder
                .orderAsc(BaseActivitySummaryDao.Properties.StartTime)
                .list()
                .filter { isStatisticsSport(ActivityKind.fromCode(it.activityKind)) }
        }
    }

    /**
     * Metrics from the summary JSON. Missing values are 0.
     */
    private fun workoutInput(parser: ActivitySummaryParser, summary: BaseActivitySummary): WorkoutInput {
        val data = try {
            ActivitySummaryJsonSummary(parser, summary).getSummaryData(false)
        } catch (e: Exception) {
            LOG.warn("Could not read summary {}", summary.id, e)
            null
        }

        fun number(vararg keys: String): Double {
            val key = keys.firstOrNull { data?.has(it) == true } ?: return 0.0
            return data?.getNumber(key, 0)?.toDouble() ?: 0.0
        }

        fun speed(vararg keys: String): Double {
            val key = keys.firstOrNull { data?.has(it) == true } ?: return 0.0
            val entry = data?.get(key) as? ActivitySummarySimpleEntry ?: return 0.0
            val value = (entry.value as? Number)?.toDouble() ?: return 0.0
            return StatisticsAggregation.speedMetersPerSecond(value, entry.unit)
        }

        val cadenceKey = listOf(ActivitySummaryEntries.CADENCE_AVG, ActivitySummaryEntries.SWIM_AVG_CADENCE)
            .firstOrNull { data?.has(it) == true }
        val cadenceUnit = listOfNotNull(cadenceKey, ActivitySummaryEntries.CADENCE_MAX)
            .firstNotNullOfOrNull { (data?.get(it) as? ActivitySummarySimpleEntry)?.unit }

        return WorkoutInput(
            startMillis = summary.startTime.time,
            endMillis = summary.endTime.time,
            distanceMeters = number(ActivitySummaryEntries.DISTANCE_METERS),
            calories = number(ActivitySummaryEntries.CALORIES_BURNT, ActivitySummaryEntries.CALORIES_TOTAL),
            ascentMeters = number(ActivitySummaryEntries.TOTAL_ASCENT, ActivitySummaryEntries.ASCENT_METERS),
            descentMeters = number(ActivitySummaryEntries.TOTAL_DESCENT, ActivitySummaryEntries.DESCENT_METERS),
            steps = number(ActivitySummaryEntries.STEPS),
            avgSpeedMetersPerSecond = speed(ActivitySummaryEntries.PACE_AVG_SECONDS_KM, ActivitySummaryEntries.SPEED_AVG),
            maxSpeedMetersPerSecond = speed(ActivitySummaryEntries.PACE_MAX, ActivitySummaryEntries.SPEED_MAX),
            avgHeartRate = number(ActivitySummaryEntries.HR_AVG),
            maxHeartRate = number(ActivitySummaryEntries.HR_MAX),
            avgPowerWatts = number(ActivitySummaryEntries.AVG_POWER),
            maxPowerWatts = number(ActivitySummaryEntries.MAX_POWER),
            avgCadence = cadenceKey?.let { number(it) } ?: 0.0,
            maxCadence = number(ActivitySummaryEntries.CADENCE_MAX),
            cadenceUnit = cadenceUnit,
            avgSwolf = number(ActivitySummaryEntries.SWOLF_AVG, ActivitySummaryEntries.SWOLF_INDEX),
            aerobicEffect = number(ActivitySummaryEntries.TRAINING_EFFECT_AEROBIC),
            anaerobicEffect = number(ActivitySummaryEntries.TRAINING_EFFECT_ANAEROBIC),
            trainingLoad = number(ActivitySummaryEntries.TRAINING_LOAD),
            sets = number(ActivitySummaryEntries.SETS),
        )
    }

    override fun onCleared() {
        loadJob?.cancel()
        loading = false
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(WorkoutStatisticsViewModel::class.java)
    }
}
