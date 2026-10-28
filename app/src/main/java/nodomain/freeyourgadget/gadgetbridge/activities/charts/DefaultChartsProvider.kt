package nodomain.freeyourgadget.gadgetbridge.activities.charts

import android.content.Context
import android.text.format.DateUtils
import androidx.fragment.app.Fragment
import lineageos.weather.util.TemperatureUtils
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.HeartRateUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ActivityChartsActivity.UnknownFragment
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.DeviceSettingsPreferenceConst
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.devices.GenericMetricSampleProvider
import nodomain.freeyourgadget.gadgetbridge.devices.HydrationSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.DailyTotals
import nodomain.freeyourgadget.gadgetbridge.model.HydrationUnit
import nodomain.freeyourgadget.gadgetbridge.model.MetricSample
import nodomain.freeyourgadget.gadgetbridge.model.TemperatureUnit
import nodomain.freeyourgadget.gadgetbridge.model.WeightUnit
import nodomain.freeyourgadget.gadgetbridge.util.DateTimeUtils
import nodomain.freeyourgadget.gadgetbridge.util.FormatUtils
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

open class DefaultChartsProvider : DeviceChartsProvider {
    companion object {
        @JvmField
        val INSTANCE = DefaultChartsProvider()

        const val OVERVIEW = "overview"
    }

    override fun getSupportedCharts(device: GBDevice): List<String> {
        val coordinator = device.deviceCoordinator
        val supportedCharts = mutableListOf<String>()

        if (coordinator.supportsCyclingData(device)) {
            supportedCharts.add("cycling")
        }
        if (coordinator.supportsActivityTracking(device)) {
            supportedCharts.add("activity")
            supportedCharts.add("activitylist")
        }
        if (coordinator.supportsSleepMeasurement(device)) {
            supportedCharts.add("sleep")
        }
        if (coordinator.supportsHrvMeasurement(device)) {
            supportedCharts.add("hrvstatus")
        }
        if (coordinator.supportsBodyEnergy(device)) {
            supportedCharts.add("bodyenergy")
        }
        if (coordinator.supportsVO2Max(device)) {
            supportedCharts.add("vo2max")
        }
        if (coordinator.supportsTrainingLoad(device)) {
            supportedCharts.add("load")
        }
        if (coordinator.supportsRacePrediction(device)) {
            supportedCharts.add("raceprediction")
        }
        if (coordinator.supportsTrainingReadiness(device)) {
            supportedCharts.add("trainingreadiness")
        }
        if (coordinator.supportsSolarCharging(device)) {
            supportedCharts.add("solarcharging")
        }
        if (GenericMetricSampleProvider.supportsMetrics(device).isNotEmpty()) {
            supportedCharts.add("genericmetrics")
        }
        if (coordinator.supportsHeartRateMeasurement(device)) {
            supportedCharts.add("heartrate")
        }
        if (coordinator.supportsStepCounter(device)) {
            supportedCharts.add("stepsweek")
        }
        if (coordinator.supportsStressMeasurement(device)) {
            supportedCharts.add("stress")
        }
        if (coordinator.supportsPai(device)) {
            supportedCharts.add("pai")
        }
        if (coordinator.supportsSpeedzones(device)) {
            supportedCharts.add("speedzones")
        }
        if (coordinator.supportsRealtimeData(device)) {
            supportedCharts.add("livestats")
        }
        if (coordinator.supportsSpo2(device)) {
            supportedCharts.add("spo2")
        }
        if (coordinator.supportsTemperatureMeasurement(device)) {
            supportedCharts.add("temperature")
        }
        if (coordinator.supportsBloodPressureMeasurement(device)) {
            supportedCharts.add("bloodpressure")
        }
        if (coordinator.supportsWeightMeasurement(device)) {
            supportedCharts.add("weight")
        }
        if (coordinator.supportsActiveCalories(device)) {
            supportedCharts.add("calories")
        }
        if (coordinator.supportsHydration(device)) {
            supportedCharts.add("hydration")
        }
        if (coordinator.supportsRespiratoryRate(device)) {
            supportedCharts.add("respiratoryrate")
        }

        if (supportedCharts.size > 3) {
            supportedCharts.add(0, OVERVIEW)
        }

        return supportedCharts
    }

    override fun getEnabledCharts(device: GBDevice): List<String> {
        val prefs = GBApplication.getDevicePrefs(device)
        val enabledCharts = prefs.getString(DeviceSettingsPreferenceConst.PREFS_DEVICE_CHARTS_TABS, null)
        val supportedCharts = getSupportedCharts(device)
        return if (!enabledCharts.isNullOrBlank()) {
            val enabledSupportedCharts = enabledCharts.split(",").intersect(supportedCharts.toSet()).toList()
            if (enabledSupportedCharts.any { it != OVERVIEW }) {
                enabledSupportedCharts
            } else {
                // In case all charts are disabled, to prevent a fully empty charts view
                supportedCharts
            }
        } else {
            supportedCharts
        }
    }

    override fun getChartLabel(
        context: Context,
        device: GBDevice,
        chartName: String
    ): String {
        return when (chartName) {
            OVERVIEW -> context.getString(R.string.workout_tab_overview)
            "activity" -> context.getString(R.string.activity_sleepchart_activity_and_sleep)
            "activitylist" -> context.getString(R.string.charts_activity_list)
            "sleep" -> context.getString(R.string.sleepchart_your_sleep)
            "heartrate" -> context.getString(R.string.menuitem_hr)
            "hrvstatus" -> context.getString(R.string.pref_header_hrv_status)
            "bodyenergy" -> context.getString(R.string.body_energy)
            "vo2max" -> context.getString(R.string.menuitem_vo2_max)
            "stress" -> context.getString(R.string.menuitem_stress)
            "pai" -> context.getString(device.deviceCoordinator.getPaiName())
            "stepsweek" -> context.getString(R.string.steps)
            "speedzones" -> context.getString(R.string.stats_title)
            "livestats" -> context.getString(R.string.liveactivity_live_activity)
            "spo2" -> context.getString(R.string.pref_header_spo2)
            "temperature" -> context.getString(R.string.menuitem_temperature)
            "bloodpressure" -> context.getString(R.string.blood_pressure)
            "cycling" -> context.getString(R.string.title_cycling)
            "weight" -> context.getString(R.string.menuitem_weight)
            "calories" -> context.getString(R.string.calories)
            "hydration" -> context.getString(R.string.pref_header_hydration)
            "respiratoryrate" -> context.getString(R.string.respiratoryrate)
            "load" -> context.getString(R.string.pref_header_training_load)
            "raceprediction" -> context.getString(R.string.menuitem_race_predictor)
            "trainingreadiness" -> context.getString(R.string.metric_garmin_training_readiness)
            "solarcharging" -> context.getString(R.string.menuitem_solar_charging)
            "genericmetrics" -> context.getString(R.string.generic_metrics)
            else -> String.format(Locale.getDefault(), "Unknown %s", chartName)
        }
    }

    override fun getChartFragment(
        device: GBDevice,
        chartName: String,
        allowSwipe: Boolean
    ): Fragment {
        return when (chartName) {
            OVERVIEW -> ChartsOverviewFragment()
            "activity" -> ActivitySleepChartFragment()
            "activitylist" -> ActivityListingChartFragment()
            "sleep" -> SleepCollectionFragment.newInstance(allowSwipe)
            "heartrate" -> HeartRateCollectionFragment.newInstance(allowSwipe)
            "hrvstatus" -> HrvStatusCollectionFragment.newInstance(allowSwipe)
            "bodyenergy" -> BodyEnergyCollectionFragment.newInstance(allowSwipe)
            "vo2max" -> VO2MaxCollectionFragment.newInstance(allowSwipe)
            "load" -> LoadFragment()
            "raceprediction" -> RacePredictionCollectionFragment.newInstance(allowSwipe)
            "trainingreadiness" -> TrainingReadinessFragment()
            "solarcharging" -> SolarChargingCollectionFragment.newInstance(allowSwipe)
            "genericmetrics" -> GenericMetricCollectionFragment.newInstance(allowSwipe)
            "stress" -> StressCollectionFragment.newInstance(allowSwipe)
            "pai" -> PaiChartFragment()
            "stepsweek" -> StepsCollectionFragment.newInstance(allowSwipe)
            "speedzones" -> SpeedZonesFragment()
            "livestats" -> LiveActivityFragment()
            "spo2" -> Spo2CollectionFragment.newInstance(allowSwipe)
            "temperature" -> TemperatureCollectionFragment.newInstance(allowSwipe)
            "bloodpressure" -> BloodPressureCollectionFragment.newInstance(allowSwipe)
            "cycling" -> CyclingChartFragment()
            "weight" -> WeightChartFragment()
            "calories" -> CaloriesCollectionFragment.newInstance(allowSwipe)
            "hydration" -> HydrationCollectionFragment.newInstance(allowSwipe)
            "respiratoryrate" -> RespiratoryRateCollectionFragment.newInstance(allowSwipe)
            else -> UnknownFragment()
        }
    }

    override fun getChartIcon(chartName: String): Int {
        return when (chartName) {
            OVERVIEW -> R.drawable.ic_dashboard
            "activity" -> R.drawable.ic_activity_walking
            "activitylist" -> R.drawable.ic_list_numbered
            "sleep" -> R.drawable.ic_activity_sleep
            "heartrate" -> R.drawable.ic_heartrate
            "hrvstatus" -> R.drawable.ic_ecg
            "bodyenergy" -> R.drawable.ic_bolt
            "vo2max" -> R.drawable.ic_vo2_max
            "stress" -> R.drawable.ic_stress
            "pai" -> R.drawable.ic_run_circle
            "stepsweek" -> R.drawable.ic_steps
            "speedzones" -> R.drawable.ic_speed
            "livestats" -> R.drawable.ic_heart
            "spo2" -> R.drawable.ic_spo2
            "temperature" -> R.drawable.ic_temperature
            "bloodpressure" -> R.drawable.ic_pressure
            "cycling" -> R.drawable.ic_directions_bike
            "weight" -> R.drawable.ic_weight
            "calories" -> R.drawable.ic_calories
            "hydration" -> R.drawable.ic_drink
            "respiratoryrate" -> R.drawable.ic_activity_meditation
            "load" -> R.drawable.ic_show_chart
            "raceprediction" -> R.drawable.ic_timer
            "trainingreadiness" -> R.drawable.ic_insights
            "solarcharging" -> R.drawable.ic_wb_sunny
            "genericmetrics" -> R.drawable.ic_bar_chart
            else -> R.drawable.ic_help_outline
        }
    }

    override fun getChartValue(
        context: Context,
        db: DBHandler,
        device: GBDevice,
        chartName: String,
        timestamp: Int,
    ): String? {
        val coordinator = device.deviceCoordinator
        val session = db.daoSession
        val date = Date(timestamp * 1000L)
        val tsStart = (DateTimeUtils.dayStart(date).time / 1000L).toInt()
        val tsEnd = (DateTimeUtils.dayEnd(date).time / 1000L).toInt()
        val start = tsStart * 1000L
        val end = tsEnd * 1000L

        return when (chartName) {
            // Activity / Steps - total number of steps for the day
            "activity", "stepsweek" -> {
                val steps = DailyTotals.getDailyStepsForDevice(device, calendarOf(tsEnd), db)
                NumberFormat.getInstance().format(steps)
            }

            // Sleep - score (if available), followed by sleep time
            "sleep" -> {
                val minutes = DailyTotals.getDailyTotalsForDevice(device, calendarOf(timestamp), db).sleep
                val score = if (coordinator.supportsSleepScore(device)) {
                    coordinator.getSleepScoreProvider(device, db.daoSession)
                        ?.getAllSamples(start, end)
                        ?.lastOrNull()
                        ?.sleepScore
                } else {
                    null
                }
                val sleepTime = DateTimeUtils.formatDurationHoursMinutes(minutes, TimeUnit.MINUTES)
                if (minutes > 0) {
                    if (score != null) {
                        "$score • $sleepTime"
                    } else {
                        sleepTime
                    }
                } else {
                    null
                }
            }

            // Calories - latest active calories value for the day
            "calories" -> {
                val kcal = DailyTotals.getDailyTotalsForDevice(device, calendarOf(tsEnd), db).activeCalories / 1000
                context.getString(R.string.body_composition_kcal, kcal.toInt())
            }

            // Heart rate - average for the time range
            "heartrate" -> {
                val heartRateUtils = HeartRateUtils.getInstance()
                coordinator.getSampleProvider(device, session)
                    ?.getAllActivitySamples(tsStart, tsEnd)
                    ?.map { it.heartRate }
                    ?.filter { heartRateUtils.isValidHeartRateValue(it) }
                    ?.takeIf { it.isNotEmpty() }
                    ?.let { context.getString(R.string.bpm_value_unit, it.average().roundToInt()) }
            }

            // HRV - latest weekly average within the time range
            "hrvstatus" -> coordinator.getHrvSummarySampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.lastOrNull()
                ?.weeklyAverage
                ?.takeIf { it > 0 }
                ?.let { context.getString(R.string.hrv_status_unit, it) }

            // Body energy - latest value if today, gain/loss in other days
            "bodyenergy" -> {
                val isToday = DateUtils.isToday(tsEnd * 1000L)
                if (isToday) {
                    coordinator.getBodyEnergySampleProvider(device, session)
                        ?.getAllSamples(start, end)
                        ?.lastOrNull()
                        ?.let { context.getString(R.string.battery_percentage_str, it.energy.toString()) }
                } else {
                    val samples = coordinator.getBodyEnergySampleProvider(device, db.daoSession)
                        ?.getAllSamples(start, end)

                    if (samples != null && samples.size > 1) {
                        var gained = 0
                        var lost = 0
                        samples.zipWithNext { a, b ->
                            val diff = b.energy - a.energy
                            if (diff > 0) {
                                gained += diff
                            } else {
                                lost -= diff
                            }
                        }
                        "↑${gained} ↓${lost}"
                    } else {
                        null
                    }
                }
            }

            // VO2 Max - latest until the end of the time range
            "vo2max" -> coordinator.getVo2MaxSampleProvider(device, session)
                ?.getLatestSample(end)
                ?.value
                ?.takeIf { it > 0 }
                ?.let { String.format(Locale.getDefault(), "%.1f", it) }

            // Stress - average for the time range
            "stress" -> coordinator.getStressSampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.map { it.stress }
                ?.filter { it > 0 }
                ?.takeIf { it.isNotEmpty() }
                ?.average()
                ?.roundToInt()
                ?.toString()

            // PAI - latest value within the time range
            "pai" -> coordinator.getPaiSampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.lastOrNull()
                ?.paiTotal
                ?.roundToInt()
                ?.toString()

            // SpO2 - average for the time range
            "spo2" -> coordinator.getSpo2SampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.map { it.spo2 }
                ?.filter { it > 0 }
                ?.takeIf { it.isNotEmpty() }
                ?.let { context.getString(R.string.battery_percentage_str, it.average().roundToInt().toString()) }

            // Temperature - average for the time range
            "temperature" -> coordinator.getTemperatureSampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.takeIf { it.isNotEmpty() }
                ?.let { samples ->
                    TemperatureUtils.formatAndConvert(
                        samples.map { it.temperature.toDouble() }.average(),
                        TemperatureUnit.CELSIUS,
                        GBApplication.getPrefs().temperatureUnit,
                        DecimalFormat("0.0")
                    )
                }

            // Blood Pressure - latest value within the time range
            "bloodpressure" -> coordinator.getBloodPressureSampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.lastOrNull()
                ?.let { context.getString(R.string.blood_pressure_avg_format, it.bpSystolic, it.bpDiastolic) }

            // Weight - latest value until the end of the time range
            "weight" -> coordinator.getWeightSampleProvider(device, session)
                ?.getLatestSample(end)
                ?.weightKg
                ?.takeIf { it > 0 }
                ?.let { WeightUnit.formatWeight(context, it.toDouble(), GBApplication.getPrefs().weightUnit) }

            // Hydration - daily total for the time range
            "hydration" -> {
                val date = LocalDate.ofInstant(Instant.ofEpochSecond(tsEnd.toLong()), ZoneId.systemDefault())
                coordinator.getHydrationSampleProvider(device, session)
                    ?.getDayTotal(HydrationSampleProvider.toDay(date))
                    ?.takeIf { it > 0 }
                    ?.let { HydrationUnit.forDevice(device).format(context, it) }
            }

            // Respiratory Rate - average for the time range
            "respiratoryrate" -> coordinator.getRespiratoryRateSampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.map { it.respiratoryRate }
                ?.filter { it > 0 }
                ?.takeIf { it.isNotEmpty() }
                ?.let { "${it.average().roundToInt()} ${context.getString(R.string.breaths_per_min)}" }

            // Other generic metrics - number of available metrics
            "genericmetrics" -> {
                context.getString(
                    R.string.number_of_generic_metrics,
                    GenericMetricSampleProvider.getSupportedMetrics(device).size
                )
            }

            // Cycling - distance for the time range
            "cycling" -> coordinator.getCyclingSampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.mapNotNull { it.distance }
                ?.takeIf { it.size > 1 }
                ?.let { FormatUtils.getFormattedDistanceLabel((it.last() - it.first()).toDouble()) }

            // Training load - latest acute load until the end of the time range
            "load" -> {
                val acuteLoad = if (GBApplication.getPrefs().experimentalMetrics() &&
                    GenericMetricSampleProvider.supportsMetrics(device, MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE)
                ) {
                    GenericMetricSampleProvider.getLatestMetricSampleBefore(
                        db, device, MetricSample.Metric.GENERIC_TRAINING_LOAD_ACUTE, end
                    )?.metricScore?.roundToInt()
                } else {
                    @Suppress("DEPRECATION")
                    coordinator.getTrainingAcuteLoadSampleProvider(device, session)
                        ?.getLatestSample(end)
                        ?.value
                }
                acuteLoad?.takeIf { it > 0 }?.toString()
            }

            // Race prediction - latest 5K prediction until the end of the time range
            "raceprediction" -> GenericMetricSampleProvider.getLatestMetricSampleBefore(
                db, device, MetricSample.Metric.GENERIC_RACE_PREDICTOR_5K, end
            )
                ?.metricScore
                ?.takeIf { it > 0 }
                ?.let { RacePredictionPeriodFragment.formatSeconds(it) }

            // Training readiness - latest score until the end of the time range
            "trainingreadiness" -> GenericMetricSampleProvider.getLatestMetricSampleBefore(
                db, device, MetricSample.Metric.GARMIN_TRAINING_READINESS, end
            )
                ?.metricScore
                ?.roundToInt()
                ?.toString()

            // Solar charging - battery gain for the time range
            "solarcharging" -> coordinator.getSolarChargeSampleProvider(device, session)
                ?.getAllSamples(start, end)
                ?.takeIf { it.isNotEmpty() }
                ?.let { SolarChargingStats.computeGainMillis(it) / 60000L }
                ?.takeIf { it > 0 }
                ?.let { "+" + context.getString(R.string.num_min, it.toInt()) }

            // Activity List, Speed Zones, Live Activity - no actual subtitle
            "activitylist", "speedzones", "livestats" -> ""

            else -> null
        }
    }

    private fun calendarOf(timestamp: Int): Calendar {
        val calendar = GregorianCalendar.getInstance()
        calendar.timeInMillis = timestamp * 1000L
        return calendar
    }

    override fun getDailySleepStats(
        context: Context,
        db: DBHandler,
        device: GBDevice,
        tsStart: Int,
        tsEnd: Int
    ): Map<String, ActivitySummarySimpleEntry> {
        return emptyMap()
    }
}
