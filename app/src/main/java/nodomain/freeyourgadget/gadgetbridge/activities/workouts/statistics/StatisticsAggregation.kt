package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * A sport and its workout count.
 */
data class SportCount(val kindCode: Int, val name: String, val count: Int)

/**
 * Values of one workout used for aggregation. Missing values are 0.
 */
data class WorkoutInput(
    val startMillis: Long,
    val endMillis: Long,
    val distanceMeters: Double = 0.0,
    val calories: Double = 0.0,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val steps: Double = 0.0,
    val avgSpeedMetersPerSecond: Double = 0.0,
    val maxSpeedMetersPerSecond: Double = 0.0,
    val avgHeartRate: Double = 0.0,
    val maxHeartRate: Double = 0.0,
    val avgPowerWatts: Double = 0.0,
    val maxPowerWatts: Double = 0.0,
    val avgCadence: Double = 0.0,
    val maxCadence: Double = 0.0,
    val cadenceUnit: String? = null,
    val avgSwolf: Double = 0.0,
    val aerobicEffect: Double = 0.0,
    val anaerobicEffect: Double = 0.0,
    val trainingLoad: Double = 0.0,
    val sets: Double = 0.0,
) {
    val totals: MetricTotals
        get() {
            val durationSeconds = (endMillis - startMillis).coerceAtLeast(0L) / MILLIS_PER_SECOND
            val movingSeconds = if (distanceMeters > 0.0 && avgSpeedMetersPerSecond > 0.0) {
                distanceMeters / avgSpeedMetersPerSecond
            } else {
                durationSeconds.toDouble()
            }
            return MetricTotals(
                durationSeconds = durationSeconds,
                distanceMeters = distanceMeters,
                calories = calories,
                ascentMeters = ascentMeters,
                descentMeters = descentMeters,
                steps = steps,
                speed = WeightedSum.of(avgSpeedMetersPerSecond, movingSeconds),
                maxSpeed = maxSpeedMetersPerSecond,
                heartRate = WeightedSum.of(avgHeartRate, durationSeconds.toDouble()),
                maxHeartRate = maxHeartRate,
                power = WeightedSum.of(avgPowerWatts, durationSeconds.toDouble()),
                maxPower = maxPowerWatts,
                cadence = WeightedSum.of(avgCadence, durationSeconds.toDouble()),
                maxCadence = maxCadence,
                swolf = WeightedSum.of(avgSwolf, durationSeconds.toDouble()),
                aerobicEffect = WeightedSum.of(aerobicEffect, durationSeconds.toDouble()),
                anaerobicEffect = WeightedSum.of(anaerobicEffect, durationSeconds.toDouble()),
                trainingLoad = trainingLoad,
                sets = sets,
            )
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}

/**
 * Sum and weight of a weighted average, so partial sums can be added.
 */
data class WeightedSum(val sum: Double = 0.0, val weight: Double = 0.0) {
    val average: Double get() = if (weight > 0.0) sum / weight else 0.0

    operator fun plus(other: WeightedSum) = WeightedSum(sum + other.sum, weight + other.weight)

    companion object {
        fun of(value: Double, weight: Double) = if (value > 0.0) WeightedSum(value * weight, weight) else WeightedSum()
    }
}

/**
 * Metric sums over workouts, in raw units.
 */
data class MetricTotals(
    val durationSeconds: Long = 0L,
    val distanceMeters: Double = 0.0,
    val calories: Double = 0.0,
    val ascentMeters: Double = 0.0,
    val descentMeters: Double = 0.0,
    val steps: Double = 0.0,
    val speed: WeightedSum = WeightedSum(),
    val maxSpeed: Double = 0.0,
    val heartRate: WeightedSum = WeightedSum(),
    val maxHeartRate: Double = 0.0,
    val power: WeightedSum = WeightedSum(),
    val maxPower: Double = 0.0,
    val cadence: WeightedSum = WeightedSum(),
    val maxCadence: Double = 0.0,
    val swolf: WeightedSum = WeightedSum(),
    val aerobicEffect: WeightedSum = WeightedSum(),
    val anaerobicEffect: WeightedSum = WeightedSum(),
    val trainingLoad: Double = 0.0,
    val sets: Double = 0.0,
) {
    operator fun plus(other: MetricTotals) = MetricTotals(
        durationSeconds = durationSeconds + other.durationSeconds,
        distanceMeters = distanceMeters + other.distanceMeters,
        calories = calories + other.calories,
        ascentMeters = ascentMeters + other.ascentMeters,
        descentMeters = descentMeters + other.descentMeters,
        steps = steps + other.steps,
        speed = speed + other.speed,
        maxSpeed = maxOf(maxSpeed, other.maxSpeed),
        heartRate = heartRate + other.heartRate,
        maxHeartRate = maxOf(maxHeartRate, other.maxHeartRate),
        power = power + other.power,
        maxPower = maxOf(maxPower, other.maxPower),
        cadence = cadence + other.cadence,
        maxCadence = maxOf(maxCadence, other.maxCadence),
        swolf = swolf + other.swolf,
        aerobicEffect = aerobicEffect + other.aerobicEffect,
        anaerobicEffect = anaerobicEffect + other.anaerobicEffect,
        trainingLoad = trainingLoad + other.trainingLoad,
        sets = sets + other.sets,
    )
}

/**
 * How a metric combines over several workouts.
 */
enum class MetricAggregation { TOTAL, AVERAGE, MAXIMUM }

/**
 * Metrics a sport's chart can plot. Speed is in m/s, pace in s/m.
 */
enum class SportMetric(
    val aggregation: MetricAggregation = MetricAggregation.TOTAL,
    val valueOf: (MetricTotals) -> Double,
) {
    DISTANCE(valueOf = { it.distanceMeters }),
    DURATION(valueOf = { it.durationSeconds.toDouble() }),
    AVG_PACE(
        aggregation = MetricAggregation.AVERAGE,
        valueOf = { if (it.speed.average > 0.0) 1.0 / it.speed.average else 0.0 },
    ),
    AVG_SPEED(aggregation = MetricAggregation.AVERAGE, valueOf = { it.speed.average }),
    MAX_PACE(
        aggregation = MetricAggregation.MAXIMUM,
        valueOf = { if (it.maxSpeed > 0.0) 1.0 / it.maxSpeed else 0.0 },
    ),
    MAX_SPEED(aggregation = MetricAggregation.MAXIMUM, valueOf = { it.maxSpeed }),
    AVG_HEART_RATE(aggregation = MetricAggregation.AVERAGE, valueOf = { it.heartRate.average }),
    MAX_HEART_RATE(aggregation = MetricAggregation.MAXIMUM, valueOf = { it.maxHeartRate }),
    AVG_POWER(aggregation = MetricAggregation.AVERAGE, valueOf = { it.power.average }),
    MAX_POWER(aggregation = MetricAggregation.MAXIMUM, valueOf = { it.maxPower }),
    AVG_CADENCE(aggregation = MetricAggregation.AVERAGE, valueOf = { it.cadence.average }),
    MAX_CADENCE(aggregation = MetricAggregation.MAXIMUM, valueOf = { it.maxCadence }),
    AVG_SWOLF(aggregation = MetricAggregation.AVERAGE, valueOf = { it.swolf.average }),
    CALORIES(valueOf = { it.calories }),
    ASCENT(valueOf = { it.ascentMeters }),
    DESCENT(valueOf = { it.descentMeters }),
    STEPS(valueOf = { it.steps }),
    TRAINING_LOAD(valueOf = { it.trainingLoad }),
    SETS(valueOf = { it.sets }),
    AVG_AEROBIC_EFFECT(aggregation = MetricAggregation.AVERAGE, valueOf = { it.aerobicEffect.average }),
    AVG_ANAEROBIC_EFFECT(aggregation = MetricAggregation.AVERAGE, valueOf = { it.anaerobicEffect.average }),
    ;

    /**
     * Averages and maximums aren't totals, so they are only drawn as lines.
     */
    val lineOnly: Boolean get() = aggregation != MetricAggregation.TOTAL
}

enum class PeriodKind { WEEK, MONTH, YEAR, CUSTOM }

/**
 * A calendar week (Mon-Sun), month or year, created with [containing], or a range of days, created with [custom].
 * Years have a bar per month, the others a bar per day.
 */
data class StatisticsPeriod(val kind: PeriodKind, val start: LocalDate, private val customEnd: LocalDate? = null) {
    val endInclusive: LocalDate
        get() = if (kind == PeriodKind.CUSTOM) checkNotNull(customEnd) else next().start.minusDays(1)

    val dayCount: Long get() = ChronoUnit.DAYS.between(start, endInclusive) + 1

    val barCount: Int get() = barOf(endInclusive)

    /**
     * Custom ranges move by their length.
     */
    fun previous() = shifted(-1)

    fun next() = shifted(1)

    private fun shifted(amount: Long): StatisticsPeriod = when (kind) {
        PeriodKind.WEEK -> StatisticsPeriod(kind, start.plusWeeks(amount))
        PeriodKind.MONTH -> StatisticsPeriod(kind, start.plusMonths(amount))
        PeriodKind.YEAR -> StatisticsPeriod(kind, start.plusYears(amount))
        PeriodKind.CUSTOM -> custom(start.plusDays(amount * dayCount), endInclusive.plusDays(amount * dayCount))
    }

    /**
     * 1-based bar for [date].
     */
    internal fun barOf(date: LocalDate): Int = if (kind == PeriodKind.YEAR) {
        ChronoUnit.MONTHS.between(YearMonth.from(start), YearMonth.from(date)).toInt() + 1
    } else {
        ChronoUnit.DAYS.between(start, date).toInt() + 1
    }

    /**
     * First day of [bar].
     */
    fun barStart(bar: Int): LocalDate =
        if (kind == PeriodKind.YEAR) start.plusMonths(bar - 1L) else start.plusDays(bar - 1L)

    companion object {
        fun containing(kind: PeriodKind, date: LocalDate) = StatisticsPeriod(
            kind,
            when (kind) {
                PeriodKind.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                PeriodKind.MONTH -> date.withDayOfMonth(1)
                PeriodKind.YEAR -> date.withDayOfYear(1)
                PeriodKind.CUSTOM -> throw IllegalArgumentException("Custom periods have no calendar unit")
            },
        )

        fun custom(start: LocalDate, endInclusive: LocalDate): StatisticsPeriod {
            require(!endInclusive.isBefore(start)) { "Range ends before it starts" }
            return StatisticsPeriod(PeriodKind.CUSTOM, start, endInclusive)
        }
    }
}

/**
 * One bar of a period; [x] is 1-based.
 */
data class PeriodBar(val x: Double, val totals: MetricTotals)

/**
 * Totals and bars of a sport over one period.
 */
data class PeriodStats(
    val period: StatisticsPeriod,
    val bars: List<PeriodBar>,
    val count: Int,
    val totals: MetricTotals,
)

/**
 * Date range and metrics with data for a sport. [cadenceUnit] is the unit most of its workouts record cadence in.
 */
data class SportOverview(
    val firstDate: LocalDate?,
    val lastDate: LocalDate?,
    val availableMetrics: List<SportMetric>,
    val cadenceUnit: String? = null,
)

object StatisticsAggregation {
    /**
     * Sorted by workout count, then name.
     */
    fun sportCounts(
        kindCodes: List<Int>,
        nameOf: (Int) -> String,
        collator: Comparator<in String>,
    ): List<SportCount> =
        kindCodes.groupingBy { it }.eachCount()
            .map { (code, count) -> SportCount(code, nameOf(code), count) }
            .sortedWith(compareByDescending<SportCount> { it.count }.thenBy(collator) { it.name })

    /**
     * Workouts count in the period they start in.
     */
    fun aggregate(workouts: List<WorkoutInput>, period: StatisticsPeriod, zone: ZoneId): PeriodStats {
        val bars = Array(period.barCount) { MetricTotals() }
        var count = 0
        var totals = MetricTotals()

        for (workout in workouts) {
            val date = Instant.ofEpochMilli(workout.startMillis).atZone(zone).toLocalDate()
            if (date.isBefore(period.start) || date.isAfter(period.endInclusive)) {
                continue
            }
            val workoutTotals = workout.totals
            val index = period.barOf(date) - 1
            bars[index] = bars[index] + workoutTotals
            count++
            totals += workoutTotals
        }

        return PeriodStats(
            period = period,
            bars = bars.mapIndexed { index, bar -> PeriodBar((index + 1).toDouble(), bar) },
            count = count,
            totals = totals,
        )
    }

    fun overview(workouts: List<WorkoutInput>, zone: ZoneId, paceSport: Boolean): SportOverview {
        if (workouts.isEmpty()) {
            return SportOverview(firstDate = null, lastDate = null, availableMetrics = listOf(SportMetric.DURATION))
        }
        val totals = workouts.map { it.totals }
        fun dateOf(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return SportOverview(
            firstDate = dateOf(workouts.minOf { it.startMillis }),
            lastDate = dateOf(workouts.maxOf { it.startMillis }),
            availableMetrics = SportMetric.entries
                .filter { it !in if (paceSport) SPEED_METRICS else PACE_METRICS }
                .filter { metric -> metric == SportMetric.DURATION || totals.any { metric.valueOf(it) > 0.0 } },
            cadenceUnit = workouts.filter { it.avgCadence > 0.0 || it.maxCadence > 0.0 }
                .mapNotNull { it.cadenceUnit }
                .groupingBy { it }.eachCount()
                .maxByOrNull { it.value }?.key,
        )
    }

    /**
     * Average speed or pace in m/s. 0 for other units.
     */
    fun speedMetersPerSecond(value: Double, unit: String): Double {
        if (value <= 0.0) {
            return 0.0
        }
        return when (unit) {
            ActivitySummaryEntries.UNIT_METERS_PER_SECOND -> value
            ActivitySummaryEntries.UNIT_KMPH -> value / KMPH_PER_METER_PER_SECOND
            ActivitySummaryEntries.UNIT_SECONDS_PER_KM -> METERS_PER_KM / value
            ActivitySummaryEntries.UNIT_SECONDS_PER_100_METERS -> METERS_PER_100_M / value
            ActivitySummaryEntries.UNIT_SECONDS_PER_M -> 1.0 / value
            else -> 0.0
        }
    }

    private val PACE_METRICS = setOf(SportMetric.AVG_PACE, SportMetric.MAX_PACE)
    private val SPEED_METRICS = setOf(SportMetric.AVG_SPEED, SportMetric.MAX_SPEED)
    private const val KMPH_PER_METER_PER_SECOND = 3.6
    private const val METERS_PER_KM = 1000.0
    private const val METERS_PER_100_M = 100.0
}
