package nodomain.freeyourgadget.gadgetbridge.activities.charts.steps

import android.os.Bundle
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.AbstractCoroutineChartFragment
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartDataRange
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartDataScope
import nodomain.freeyourgadget.gadgetbridge.activities.charts.ChartsHost
import nodomain.freeyourgadget.gadgetbridge.activities.charts.StepStreaksDashboard
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection
import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.ChartSpec
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GaugeDrawer
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.StatTileData
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addStatTileGrid
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentStepsBinding
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A day's cumulative steps / the daily goal.
 */
class StepsDailyFragment : AbstractCoroutineChartFragment<StepsDailyFragment.ScreenData>() {
    data class ScreenData(
        val totals: StepsDailyChartData.DailyTotals,
        val spec: ChartSpec,
    )

    private lateinit var binding: FragmentStepsBinding

    private var stepsGoal: Int = ActivityUser.defaultUserStepsGoal
    private var stepsColor: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        binding = FragmentStepsBinding.inflate(layoutInflater, container, false)

        binding.root.setOnScrollChangeListener { _, _, scrollY, _, _ ->
            chartsHost().enableSwipeRefresh(scrollY == 0)
        }

        binding.stepsDailyChart.zoomable = true
        binding.stepsDailyChart.dismissSelectionOnTapOutside(binding.root)

        stepsColor = ContextCompat.getColor(requireContext(), R.color.steps_color)
        stepsGoal = GBApplication.getPrefs()
            .getInt(ActivityUser.PREF_USER_STEPS_GOAL, ActivityUser.defaultUserStepsGoal)

        binding.stepsStreaksButton.setOnClickListener {
            StepStreaksDashboard.newInstance(stepsGoal, chartsHost().device)
                .show(requireActivity().supportFragmentManager, "steps_streaks_dashboard")
        }

        return binding.root
    }

    override fun getTitle(): CharSequence = getString(R.string.steps)

    override fun isSingleDay(): Boolean = true

    override suspend fun loadChart(scope: ChartDataScope, host: ChartsHost): ScreenData {
        val day = Calendar.getInstance()
        day.time = host.endDate
        day.set(Calendar.HOUR_OF_DAY, 0)
        day.set(Calendar.MINUTE, 0)
        day.set(Calendar.SECOND, 0)
        val tsStart = (day.timeInMillis / 1000).toInt()
        val tsEnd = tsStart + 24 * 60 * 60 - 1

        val samples = scope.activitySamples(host.device, tsStart, tsEnd)
        val totals = StepsDailyChartData.buildDailyTotals(samples)
        val spec = StepsDailyChartData.buildChartSpec(samples, tsStart, stepsColor, stepsGoal)
        return ScreenData(totals, spec)
    }

    override fun renderChart(data: ScreenData) {
        showTotals(data.totals)
        val chart = binding.stepsDailyChart
        if (data.spec.isEmpty) {
            chart.showMessage(getString(R.string.chart_no_data_synchronize))
            return
        }
        chart.selectionContent = { x -> selection(data.spec, x) }
        chart.setSpec(data.spec)
    }

    override fun renderError() {
        binding.stepsDailyChart.showMessage(getString(R.string.chart_no_data_synchronize))
    }

    private fun selection(spec: ChartSpec, x: Double): ChartSelection {
        val title = DateFormat.getTimeFormat(requireContext()).format(Date(x.toLong() * 1000L))
        val steps = spec.series.firstOrNull()?.points?.firstOrNull { it.x == x }?.y ?: 0.0
        val value = WorkoutValueFormatter().formatValue(steps.toLong(), ActivitySummaryEntries.UNIT_STEPS)
        return ChartSelection(
            title = title,
            rows = listOf(ChartSelection.Row(stepsColor, value)),
            description = "$title. ${getString(R.string.steps)} $value.",
        )
    }

    private fun showTotals(totals: StepsDailyChartData.DailyTotals) {
        binding.stepsDateView.text = SimpleDateFormat("E, MMM dd", Locale.getDefault()).format(chartsHost().endDate)

        val width = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            GAUGE_WIDTH_DP,
            resources.displayMetrics,
        ).toInt()
        binding.stepsGauge.setImageBitmap(
            GaugeDrawer.drawCircleGauge(
                width,
                width / GAUGE_BAR_WIDTH_DIVISOR,
                stepsColor,
                totals.steps.toInt(),
                stepsGoal,
                requireContext(),
            )
        )

        binding.stepsStatsContainer.removeAllViews()
        val stats = listOf(
            StatTileData(NumberFormat.getInstance().format(totals.steps), getString(R.string.steps)),
            StatTileData(WorkoutValueFormatter().formatValue(totals.distanceKm, "km"), getString(R.string.distance)),
        )
        addStatTileGrid(binding.stepsStatsContainer, requireContext(), stats, 0)
    }

    override fun getAvailableDataRange(device: GBDevice, db: DBHandler): ChartDataRange? =
        ChartDataRange.ofActivitySamples(device.deviceCoordinator.getSampleProvider(device, db.daoSession))

    companion object {
        private const val GAUGE_WIDTH_DP = 300f
        private const val GAUGE_BAR_WIDTH_DIVISOR = 15
    }
}
