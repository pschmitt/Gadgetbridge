package nodomain.freeyourgadget.gadgetbridge.activities.workouts

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.distinctUntilChanged
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryGroup
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummarySimpleEntry
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.entries.ActivitySummaryTableRowEntry
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentWorkoutTabDetailsBinding
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import nodomain.freeyourgadget.gadgetbridge.model.workout.Workout
import nodomain.freeyourgadget.gadgetbridge.model.workout.WorkoutViewModel
import org.apache.commons.lang3.tuple.Pair

/**
 * Everything not shown on Overview (summary + GPS map + sets), Charts (per-metric charts + HR
 * zones) or Laps (laps/intervals): the remaining per-metric groups (pace, speed, cadence,
 * elevation, power, heart rate, etc.).
 */
class WorkoutTabDetailsFragment : Fragment(), WorkoutTabScreenshotProvider {
    private lateinit var viewModel: WorkoutViewModel

    private val workoutValueFormatter = WorkoutValueFormatter()

    private lateinit var binding: FragmentWorkoutTabDetailsBinding

    override val screenshotView: View get() = binding.root

    private var latestWorkout: Workout? = null

    private val workoutId: Long by lazy {
        requireArguments().getLong("workoutId")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())
            .get(WorkoutViewModel::class.java)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentWorkoutTabDetailsBinding.inflate(inflater, container, false)

        viewModel.getWorkout(workoutId).distinctUntilChanged().observe(viewLifecycleOwner) { workout ->
            workout ?: return@observe
            latestWorkout = workout
            workoutValueFormatter.setActivityKind(ActivityKind.fromCode(workout.summary.activityKind))
            updateDetails(workout)
        }
        viewModel.showRawData.observe(viewLifecycleOwner) { showRawData ->
            workoutValueFormatter.setRawData(showRawData)
            latestWorkout?.let { updateDetails(it) }
        }

        return binding.root
    }

    private fun updateDetails(workout: Workout) {
        binding.summaryDetails.removeAllViews()

        val activityKind = ActivityKind.fromCode(workout.summary.activityKind)
        val groups = ActivitySummaryGroup.buildGroupedList(workout.data)
        var isFirstHeader = true
        for ((groupKey, entries) in groups) {
            if (groupKey == ActivitySummaryEntries.GROUP_ACTIVITY ||
                groupKey == ActivitySummaryEntries.GROUP_LAPS ||
                groupKey == ActivitySummaryEntries.GROUP_INTERVALS ||
                groupKey == ActivitySummaryEntries.SETS ||
                groupKey == ActivitySummaryEntries.GROUP_HEART_RATE_ZONES
            ) {
                // Shown on the Overview, Laps or Charts tabs instead.
                continue
            }
            val rows = groupRows(entries)
            if (rows.isEmpty()) {
                continue
            }

            addGroupHeader(groupKey, activityKind, showDivider = !isFirstHeader)
            isFirstHeader = false
            addGroupContent(rows)
        }
    }

    /**
     * Flattens a group's entries into label/formatted-value rows. Most groups hold only plain
     * [ActivitySummarySimpleEntry] values (entries with no recorded value are skipped rather
     * than showing a blank "-"). Table-shaped entries (like the Garmin ANT+ gear info
     * header/rows, one row per device) don't map to a single label/value pair, so each data row
     * is flattened into one label/value row per column instead — both kinds can appear in the
     * same group (e.g. "Gear" also carries plain battery-gain fields).
     */
    private fun groupRows(entries: List<Pair<String, ActivitySummaryEntry>>): List<kotlin.Pair<String, String>> {
        val rows = mutableListOf<kotlin.Pair<String, String>>()

        for ((key, entry) in entries) {
            if (entry is ActivitySummarySimpleEntry && entry.value != null) {
                rows.add(workoutValueFormatter.getStringResourceByName(key) to workoutValueFormatter.formatValue(entry.value, entry.unit))
            }
        }

        val tableRows = entries.mapNotNull { (_, entry) -> entry as? ActivitySummaryTableRowEntry }
        val header = tableRows.firstOrNull { it.isHeader }
        if (header != null) {
            for (dataRow in tableRows.filter { !it.isHeader }) {
                for ((columnLabel, cell) in header.columns.zip(dataRow.columns)) {
                    rows.add(columnLabel.format(workoutValueFormatter) to cell.format(workoutValueFormatter))
                }
            }
        }

        return rows
    }

    private fun addGroupHeader(groupKey: String, activityKind: ActivityKind, showDivider: Boolean) {
        addSectionHeader(binding.summaryDetails, requireContext(), groupLabel(groupKey, activityKind), showDivider)
    }

    /**
     * The [ActivitySummaryEntries.GROUP_SPEED] group holds both speed- and pace-based workouts
     * (its own entries mix `SPEED_*`/`PACE_*` keys); the Charts tab already picks "Pace" vs
     * "Speed" per activity kind for its chart title (see `DefaultWorkoutCharts.createSpeedChart`)
     * — mirror that exact decision here so the two tabs stay consistent.
     */
    private fun groupLabel(groupKey: String, activityKind: ActivityKind): String {
        if (groupKey == ActivitySummaryEntries.GROUP_SPEED &&
            (ActivityKind.isRowingActivity(activityKind) ||
                ActivityKind.isSwimActivity(activityKind) ||
                ActivityKind.isPaceActivity(activityKind))
        ) {
            return getString(R.string.Pace)
        }
        return workoutValueFormatter.getStringResourceByName(groupKey)
    }

    private fun addGroupContent(rows: List<kotlin.Pair<String, String>>) {
        addDetailRows(binding.summaryDetails, requireContext(), rows)
    }
}
