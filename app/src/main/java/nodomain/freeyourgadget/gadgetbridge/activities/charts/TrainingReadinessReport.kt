package nodomain.freeyourgadget.gadgetbridge.activities.charts

import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import kotlin.math.roundToInt

/**
 * A training readiness score and the factors behind it, as of a point in time.
 */
class TrainingReadinessReport(
    val score: Int?,
    val timestamp: Long?,
    val hrvStatus: HrvSummarySample.Status,
    val hrvWeeklyAverage: Int?,
    val acuteLoad: Int?,
    val chronicLoad: Int?,
    val sleepScore: Int?,
    val recoveryMinutes: Int?,
    val sleepHistoryAvg: Int?,
    val stressHistoryAvg: Int?,
    val stressRanges: IntArray,
) {
    companion object {
        // Factors flushed in the same sync can be timestamped a few seconds after the readiness record.
        private const val SAME_CYCLE_BUFFER_MILLIS = 5 * 60 * 1000L
        private const val THREE_DAYS_MILLIS = 3 * 24 * 60 * 60 * 1000L

        @JvmField
        val ZONE_COLOR_RES = intArrayOf(
            R.color.training_readiness_poor,
            R.color.training_readiness_low,
            R.color.training_readiness_moderate,
            R.color.training_readiness_high,
            R.color.training_readiness_prime,
        )

        @JvmField
        val ZONE_NAME_RES = intArrayOf(
            R.string.training_readiness_zone_poor,
            R.string.training_readiness_zone_low,
            R.string.training_readiness_zone_moderate,
            R.string.training_readiness_zone_high,
            R.string.training_readiness_zone_prime,
        )

        @JvmField
        val ZONE_DESC_RES = intArrayOf(
            R.string.training_readiness_zone_poor_desc,
            R.string.training_readiness_zone_low_desc,
            R.string.training_readiness_zone_moderate_desc,
            R.string.training_readiness_zone_high_desc,
            R.string.training_readiness_zone_prime_desc,
        )

        // Score bands: 1-24, 25-49, 50-74, 75-94, 95-100.
        @JvmField
        val ZONE_SEGMENTS = floatArrayOf(0.24f, 0.25f, 0.25f, 0.20f, 0.06f)

        @JvmStatic
        fun zoneIndexForScore(score: Int): Int = when {
            score >= 95 -> 4
            score >= 75 -> 3
            score >= 50 -> 2
            score >= 25 -> 1
            else -> 0
        }

        /**
         * The latest score at or before [untilMillis] and the factors as of that score.
         */
        @JvmStatic
        fun load(db: DBHandler, device: GBDevice, untilMillis: Long): TrainingReadinessReport {
            val latest = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, MetricSample.Metric.GARMIN_TRAINING_READINESS, untilMillis)
            val score = latest?.metricScore?.roundToInt()
            val timestamp = latest?.timestamp
            val asOf = if (timestamp != null) timestamp + SAME_CYCLE_BUFFER_MILLIS else untilMillis
            val threeDaysAgo = asOf - THREE_DAYS_MILLIS
            val coordinator = device.deviceCoordinator
            val session = db.daoSession

            val hrv = coordinator.getHrvSummarySampleProvider(device, session)?.getLatestSample(asOf)
            val acute = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE, asOf)
            val chronic = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, MetricSample.Metric.GENERIC_TRAINING_LOAD_CHRONIC, asOf)

            val sleepProvider = coordinator.getSleepScoreProvider(device, session)
            val sleepScore = sleepProvider?.getLatestSample(asOf)?.sleepScore
            val sleepHistoryAvg = sleepProvider?.getAllSamples(threeDaysAgo, asOf)
                ?.takeIf { it.isNotEmpty() }
                ?.let { samples -> (samples.sumOf { it.sleepScore }.toFloat() / samples.size).roundToInt() }

            val recovery = GenericMetricSampleProvider.getLatestMetricSampleBefore(db, device, MetricSample.Metric.GARMIN_RECOVERY_TIME, asOf)

            val stressHistoryAvg = coordinator.getStressSampleProvider(device, session)?.getAllSamples(threeDaysAgo, asOf)
                ?.filter { it.stress > 0 }
                ?.takeIf { it.isNotEmpty() }
                ?.let { samples -> (samples.sumOf { it.stress }.toFloat() / samples.size).roundToInt() }

            return TrainingReadinessReport(
                score = score,
                timestamp = timestamp,
                hrvStatus = hrv?.status ?: HrvSummarySample.Status.NONE,
                hrvWeeklyAverage = hrv?.weeklyAverage,
                acuteLoad = acute?.metricScore?.roundToInt(),
                chronicLoad = chronic?.metricScore?.roundToInt(),
                sleepScore = sleepScore,
                recoveryMinutes = recovery?.metricScore?.roundToInt(),
                sleepHistoryAvg = sleepHistoryAvg,
                stressHistoryAvg = stressHistoryAvg,
                stressRanges = coordinator.stressRanges,
            )
        }
    }
}
