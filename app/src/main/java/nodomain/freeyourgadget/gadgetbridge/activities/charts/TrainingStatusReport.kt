package nodomain.freeyourgadget.gadgetbridge.activities.charts

import android.content.Context
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus.StatusRun
import nodomain.freeyourgadget.gadgetbridge.activities.charts.trainingstatus.TrainingStatusChartData
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.HrvSummarySample
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The training status of a [day], the [days] up to it, oldest first, and the stats of that day.
 */
class TrainingStatusReport(
    val days: Array<Int?>,
    val day: Long,
    val today: Long,
    val run: StatusRun?,
    val acuteLoad: Int?,
    val chronicLoad: Int?,
    val vo2Max: Float?,
    val hrvStatus: HrvSummarySample.Status,
    val hrvWeeklyAverage: Int?,
) {
    val status: Int? get() = days.last()

    fun rangeText(context: Context): String? =
        run?.let { TrainingStatusPeriodFragment.rangeText(context, it, today) }

    companion object {
        @JvmStatic
        fun load(db: DBHandler, device: GBDevice, day: Long, count: Int): TrainingStatusReport {
            val today = LocalDate.now().toEpochDay()
            val firstDay = day - count + 1
            val lastLoaded = maxOf(day, today)
            val startMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = LocalDate.ofEpochDay(day + 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

            val samples = TrainingStatusPeriodFragment.loadSamples(db, device, firstDay, lastLoaded)
            val sampleDays = TrainingStatusPeriodFragment.sampleDays(samples)
            val sampleStatuses = TrainingStatusPeriodFragment.sampleStatuses(samples)
            val days = TrainingStatusChartData.dailyStatuses(sampleDays, sampleStatuses, day, count)
            val run = if (days.last() != null) {
                TrainingStatusPeriodFragment.loadRuns(db, device, sampleDays, sampleStatuses, firstDay, lastLoaded)
                    .lastOrNull { day in it.startDay..it.endDay }
            } else {
                null
            }

            val coordinator = device.deviceCoordinator
            val session = db.daoSession

            val acuteLoad: Int?
            val chronicLoad: Int?
            if (GBApplication.getPrefs().experimentalMetrics() && GenericMetricSampleProvider.supportsMetrics(device, MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE)) {
                acuteLoad = GenericMetricSampleProvider.getMetricSamples(db, device, MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE, startMillis, endMillis - 1)
                    .lastOrNull()?.metricScore?.roundToInt()
                chronicLoad = GenericMetricSampleProvider.getMetricSamples(db, device, MetricSample.Metric.GENERIC_TRAINING_LOAD_CHRONIC, startMillis, endMillis - 1)
                    .lastOrNull()?.metricScore?.roundToInt()
            } else {
                acuteLoad = coordinator.getTrainingAcuteLoadSampleProvider(device, session)
                    ?.getAllSamples(startMillis, endMillis - 1)?.lastOrNull()?.value
                chronicLoad = coordinator.getTrainingChronicLoadSampleProvider(device, session)
                    ?.getAllSamples(startMillis, endMillis - 1)?.lastOrNull()?.value
            }

            val vo2Max = if (coordinator.supportsVO2Max(device)) {
                coordinator.getVo2MaxSampleProvider(device, session)?.getLatestSample(endMillis)?.value?.takeIf { it > 0 }
            } else {
                null
            }

            val hrv = coordinator.getHrvSummarySampleProvider(device, session)
                ?.getAllSamples(startMillis, endMillis - 1)?.lastOrNull()

            return TrainingStatusReport(
                days = days,
                day = day,
                today = today,
                run = run,
                acuteLoad = acuteLoad,
                chronicLoad = chronicLoad,
                vo2Max = vo2Max,
                hrvStatus = hrv?.status ?: HrvSummarySample.Status.NONE,
                hrvWeeklyAverage = hrv?.weeklyAverage,
            )
        }
    }
}
