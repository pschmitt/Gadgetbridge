package nodomain.freeyourgadget.gadgetbridge.activities.workouts.charts

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.core.view.MenuProvider
import androidx.core.view.children
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.AbstractGBActivity
import nodomain.freeyourgadget.gadgetbridge.activities.charts.IntervalChartUtils
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartLegendView
import nodomain.freeyourgadget.gadgetbridge.databinding.WorkoutChartsBinding
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutChart

class WorkoutChartsActivity : AbstractGBActivity(), MenuProvider {

    private var context: Context = GBApplication.getContext()
    private lateinit var binding: WorkoutChartsBinding
    private var chartData: List<WorkoutChart>? = null
    val selectedCharts = mutableListOf<Any>()

    private var showHrZones = false
    private var showIntervals = false

    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = WorkoutChartsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        addMenuProvider(this)
        intent.getStringExtra(EXTRA_TITLE)?.let { supportActionBar?.title = it }
        chartData = ChartDataRepository.chartData

        if (chartData == null) {
            Toast.makeText(this, "No charts data found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding.workoutDataChart.zoomable = true
        binding.workoutDataChart.dismissSelectionOnTapOutside(binding.root)

        val initChartId = intent.getStringExtra(INIT_CHART_ID) ?: "none"
        selectedCharts.add(0, initChartId)
        setupChipGroup(binding.workoutDataChartChipGroup, initChartId)
        setupOverlayChips(binding.workoutDataChartOverlayChipGroup)
        refreshChart()
    }

    override fun onDestroy() {
        ChartDataRepository.clear()
        super.onDestroy()
    }

    fun setupChipGroup(chipGroup: ChipGroup, initChartId: String) {
        for (chart in chartData!!) {
            val chip = (layoutInflater.inflate(R.layout.layout_chart_chip, chipGroup, false) as Chip).apply {
                text = chart.title
                isCheckable = true
                isClickable = true
                tag = chart.id
                isChecked = chart.id == initChartId
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                val tag = chip.tag
                val checkedCount = binding.workoutDataChartChipGroup.children
                    .filterIsInstance<Chip>()
                    .count { it.isChecked }
                if (isChecked) {
                    selectedCharts.add(tag)
                    refreshChart()
                } else {
                    if (checkedCount == 0) {
                        chip.isChecked = true
                        Toast.makeText(this, context.getString(R.string.charts_at_least_one_item), Toast.LENGTH_SHORT).show()
                    } else {
                        selectedCharts.remove(tag)
                        refreshChart()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    fun chipUpdate(tag: String, checked: Boolean) {
        val chip = binding.workoutDataChartChipGroup.children
            .filterIsInstance<Chip>()
            .firstOrNull { it.tag == tag }
        chip?.isChecked = checked
    }

    private fun setupOverlayChips(group: ChipGroup) {
        // HR-zone bands: only offered when an HR chart with resolved thresholds is present.
        val hrChart = chartData?.find { it.id == HR_CHART_ID }
        if (hrChart?.zoneThresholds != null) {
            val chip = Chip(this).apply {
                text = context.getString(R.string.HeartRateZones)
                isCheckable = true
                isClickable = true
                isChecked = false
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                showHrZones = isChecked
                refreshChart()
            }
            group.addView(chip)
        }
        // Interval splits: only offered when the workout actually has distinct interval phases.
        if (IntervalChartUtils.hasIntervalPhases(ChartDataRepository.intervals)) {
            val chip = Chip(this).apply {
                text = context.getString(R.string.workout_intervals)
                isCheckable = true
                isClickable = true
                isChecked = false
            }
            chip.setOnCheckedChangeListener { _, isChecked ->
                showIntervals = isChecked
                refreshChart()
            }
            group.addView(chip)
        }
    }

    fun refreshChart() {
        val charts = selectedCharts.mapNotNull { id -> chartData?.find { it.id == id } }
        if (charts.isEmpty()) return
        val chart = binding.workoutDataChart
        val intervals = ChartDataRepository.intervals.takeIf { showIntervals }
        chart.selectionContent = WorkoutChartSpecs.selection(this, charts, intervals)
        chart.setSpec(WorkoutChartSpecs.spec(this, charts, showHrZones, intervals))
        binding.workoutDataChartLegend.setSeries(
            if (charts.size > 1) charts.map { ChartLegendView.lineItem(it.title, WorkoutChartSpecs.colorOf(it)) } else emptyList()
        )
    }

    override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
        this.menu = menu
        getMenuInflater().inflate(R.menu.workout_charts_menu, menu)
        setMenuFilterItemVisibility(getResources().configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
    }

    fun setMenuFilterItemVisibility(visibility: Boolean) {
        menu?.findItem(R.id.action_filter_charts)?.isVisible = visibility
    }

    override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        return when (menuItem.itemId) {
            R.id.action_rotate_screen -> {
                val currentOrientation = getResources().configuration.orientation
                if (currentOrientation == Configuration.ORIENTATION_PORTRAIT) {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                    setMenuFilterItemVisibility(true)
                } else {
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)
                    setMenuFilterItemVisibility(false)
                }
                true
            }
            R.id.action_filter_charts -> {
                showFiltersDialog()
                true
            }

            android.R.id.home -> {
                // back button
                finish()
                true
            }

            else -> false
        }
    }

    private fun showFiltersDialog() {
        val items = chartData!!.map { it.title }.toTypedArray()
        val ids = chartData!!.map { it.id }
        val checkedItems = BooleanArray(items.size) { index ->
            selectedCharts.contains(ids[index])
        }
        val selectedIndices = mutableSetOf<Int>()
        checkedItems.forEachIndexed { index, isChecked ->
            if (isChecked) selectedIndices.add(index)
        }
        val builder = MaterialAlertDialogBuilder(this)
            .setCancelable(true)
            .setMultiChoiceItems(items, checkedItems, null)
        val dialog = builder.create()
        dialog.setOnShowListener {
            val listView = dialog.listView
            listView.setOnItemClickListener { _, _, which, _ ->
                val isChecked = listView.isItemChecked(which)
                if (isChecked) {
                    selectedIndices.add(which)
                    chipUpdate(ids[which], true)
                } else {
                    if (selectedIndices.size == 1) {
                        listView.setItemChecked(which, true)
                        Toast.makeText(this, context.getString(R.string.charts_at_least_one_item), Toast.LENGTH_SHORT).show()
                    } else {
                        selectedIndices.remove(which)
                        chipUpdate(ids[which], false)
                    }
                }
            }
        }
        dialog.show()
    }

    companion object {
        const val INIT_CHART_ID = "INIT_CHART_ID"
        const val EXTRA_TITLE = "EXTRA_TITLE"
        private const val HR_CHART_ID = "heart_rate"
    }
}
