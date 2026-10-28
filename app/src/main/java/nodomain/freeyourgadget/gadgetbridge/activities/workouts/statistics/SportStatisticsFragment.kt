package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.format.DateFormat
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.util.Pair as DateRange
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.CompositeDateValidator
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.mpchart.ChartSelection
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.WorkoutValueFormatter
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addDetailRows
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.addSectionHeader
import nodomain.freeyourgadget.gadgetbridge.databinding.FragmentSportStatisticsBinding
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySummaryEntries
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Formatter
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Statistics of one sport over a week, month, year or custom range.
 */
class SportStatisticsFragment : Fragment() {
    private val viewModel: WorkoutStatisticsViewModel by activityViewModels()

    private var _binding: FragmentSportStatisticsBinding? = null
    private val binding get() = _binding!!

    private val kindCode: Int get() = requireArguments().getInt(ARG_KIND_CODE)

    private var kind = PeriodKind.MONTH
    private var period: StatisticsPeriod? = null
    private var config: ChartConfig? = null
    private var overview: SportOverview? = null
    private var stats: PeriodStats? = null
    private var loadJob: Job? = null
    private var tabTextColors: ColorStateList? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            kind = PeriodKind.valueOf(savedInstanceState.getString(STATE_KIND, kind.name))
            if (savedInstanceState.containsKey(STATE_PERIOD_START)) {
                val start = LocalDate.ofEpochDay(savedInstanceState.getLong(STATE_PERIOD_START))
                period = if (kind == PeriodKind.CUSTOM) {
                    StatisticsPeriod.custom(start, LocalDate.ofEpochDay(savedInstanceState.getLong(STATE_PERIOD_END)))
                } else {
                    StatisticsPeriod(kind, start)
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentSportStatisticsBinding.inflate(inflater, container, false)
        binding.sportChart.dismissSelectionOnTapOutside(binding.sportContent)

        PRESET_KINDS.forEach { periodKind ->
            val tab = binding.sportPeriodTabs.newTab().setText(tabLabel(periodKind)).setTag(periodKind)
            binding.sportPeriodTabs.addTab(tab, periodKind == kind)
        }
        binding.sportPeriodTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                kind = tab.tag as PeriodKind
                if (overview != null) {
                    showPeriod(StatisticsPeriod.containing(kind, today()))
                }
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                if (kind == PeriodKind.CUSTOM) {
                    onTabSelected(tab)
                }
            }
        })
        tabTextColors = binding.sportPeriodTabs.tabTextColors

        binding.sportPeriodPrevious.setOnClickListener { period?.let { showPeriod(startingAfterFirstWorkout(it.previous())) } }
        binding.sportPeriodNext.setOnClickListener { period?.let { showPeriod(it.next()) } }
        binding.sportPeriodTitle.setOnClickListener { showRangePicker() }
        binding.sportChartEdit.setOnClickListener {
            val available = overview?.availableMetrics ?: return@setOnClickListener
            val current = config ?: return@setOnClickListener
            ChartConfigBottomSheet.newInstance(available, current).show(childFragmentManager, ChartConfigBottomSheet.TAG)
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.setFragmentResultListener(ChartConfigBottomSheet.REQUEST_KEY, viewLifecycleOwner) { _, result ->
            val available = overview?.availableMetrics ?: return@setFragmentResultListener
            val applied = ChartConfig.parse(result.getString(ChartConfigBottomSheet.RESULT_CONFIG))
                ?.restrictedTo(available)
                ?: return@setFragmentResultListener
            config = applied
            GBApplication.getPrefs().preferences.edit().putString(configKey(), applied.serialize()).apply()
            stats?.let { renderChart(it) }
        }
        (childFragmentManager.findFragmentByTag(TAG_RANGE_PICKER) as? MaterialDatePicker<*>)?.let {
            @Suppress("UNCHECKED_CAST")
            listenToRangePicker(it as MaterialDatePicker<DateRange<Long, Long>>)
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val loaded = viewModel.overview(kindCode)
                overview = loaded
                config = (ChartConfig.parse(GBApplication.getPrefs().getString(configKey(), null))
                    ?: ChartConfig.default(loaded.availableMetrics)).restrictedTo(loaded.availableMetrics)
                showPeriod(period?.takeIf { it.kind == kind } ?: StatisticsPeriod.containing(kind, today()))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to load statistics for sport {}", kindCode, e)
                renderError()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val sportName = ActivityKind.fromCode(kindCode).getLabel(requireContext())
        requireActivity().title = getString(R.string.statistics_sport_title, sportName)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_KIND, kind.name)
        period?.let {
            outState.putLong(STATE_PERIOD_START, it.start.toEpochDay())
            outState.putLong(STATE_PERIOD_END, it.endInclusive.toEpochDay())
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun tabLabel(periodKind: PeriodKind): String = getString(
        when (periodKind) {
            PeriodKind.WEEK -> R.string.calendar_week
            PeriodKind.MONTH -> R.string.calendar_month
            PeriodKind.YEAR -> R.string.calendar_year
            PeriodKind.CUSTOM -> R.string.statistics_custom_range
        }
    )

    private fun today(): LocalDate = LocalDate.now(ZoneId.systemDefault())

    private fun formatter() = WorkoutValueFormatter(ActivityKind.fromCode(kindCode))

    /**
     * Pace in s/m as a value and unit: per 100 m for swimming, per km/mi otherwise.
     */
    private fun pace(raw: Double): Pair<Double, String> =
        if (ActivityKind.isSwimActivity(ActivityKind.fromCode(kindCode))) {
            raw * METERS_PER_100 to ActivitySummaryEntries.UNIT_SECONDS_PER_100_METERS
        } else {
            raw to ActivitySummaryEntries.UNIT_SECONDS_PER_M
        }

    private fun configKey() = PREF_CHART_CONFIG_PREFIX + kindCode

    /**
     * Moves a custom range that starts before the first workout to start on it, keeping its length.
     */
    private fun startingAfterFirstWorkout(shown: StatisticsPeriod): StatisticsPeriod {
        val first = overview?.firstDate ?: return shown
        if (shown.kind != PeriodKind.CUSTOM || !shown.start.isBefore(first)) {
            return shown
        }
        return StatisticsPeriod.custom(first, first.plusDays(shown.dayCount - 1))
    }

    private fun showRangePicker() {
        val shown = period ?: return
        if (overview == null || childFragmentManager.findFragmentByTag(TAG_RANGE_PICKER) != null) {
            return
        }
        val today = today()
        val first = overview?.firstDate?.takeIf { !it.isAfter(today) }
        val validators = listOfNotNull(
            first?.let { DateValidatorPointForward.from(utcMillis(it)) },
            DateValidatorPointBackward.before(utcMillis(today)),
        )
        val selectedStart = first?.let { maxOf(shown.start, it) } ?: shown.start
        val selectedEnd = minOf(shown.endInclusive, today)
        val constraints = CalendarConstraints.Builder()
            .setEnd(utcMillis(today))
            .setOpenAt(utcMillis(first?.let { maxOf(selectedEnd, it) } ?: selectedEnd))
            .setValidator(CompositeDateValidator.allOf(validators))
        first?.let { constraints.setStart(utcMillis(it)) }

        val builder = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText(R.string.statistics_custom_range)
            .setCalendarConstraints(constraints.build())
        if (!selectedEnd.isBefore(selectedStart)) {
            builder.setSelection(DateRange(utcMillis(selectedStart), utcMillis(selectedEnd)))
        }
        val picker = builder.build()
        listenToRangePicker(picker)
        picker.show(childFragmentManager, TAG_RANGE_PICKER)
    }

    private fun listenToRangePicker(picker: MaterialDatePicker<DateRange<Long, Long>>) {
        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: return@addOnPositiveButtonClickListener
            val custom = StatisticsPeriod.custom(utcDate(start), utcDate(end))
            if (_binding != null && overview != null) {
                showPeriod(custom)
            } else {
                period = custom
                kind = PeriodKind.CUSTOM
            }
        }
    }

    private fun utcMillis(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun utcDate(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

    /**
     * Cancels any load still running for another period.
     */
    private fun showPeriod(newPeriod: StatisticsPeriod) {
        period = newPeriod
        kind = newPeriod.kind
        renderTabs(newPeriod)
        renderHeader(newPeriod)

        loadJob?.cancel()
        loadJob = viewLifecycleOwner.lifecycleScope.launch {
            try {
                render(viewModel.period(kindCode, newPeriod))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LOG.error("Failed to aggregate {} of sport {}", newPeriod, kindCode, e)
                renderError()
            }
        }
    }

    /**
     * No tab is highlighted while a custom range is shown.
     */
    private fun renderTabs(shown: StatisticsPeriod) {
        val tabs = binding.sportPeriodTabs
        val colors = tabTextColors ?: return
        if (shown.kind == PeriodKind.CUSTOM) {
            tabs.selectTab(null)
            tabs.tabTextColors = ColorStateList.valueOf(colors.defaultColor)
            tabs.setSelectedTabIndicatorColor(HIDDEN_INDICATOR_COLOR)
        } else {
            tabs.tabTextColors = colors
            tabs.setSelectedTabIndicatorColor(MaterialColors.getColor(tabs, R.attr.subtab_active))
        }
    }

    /**
     * Period title and arrows, limited to the range with workouts.
     */
    private fun renderHeader(shown: StatisticsPeriod) {
        binding.sportPeriodTitle.text = periodTitle(shown)

        val overview = overview ?: return
        if (shown.kind == PeriodKind.CUSTOM) {
            binding.sportPeriodPrevious.isEnabled = overview.firstDate?.isBefore(shown.start) == true
            binding.sportPeriodNext.isEnabled = shown.endInclusive.isBefore(today())
            return
        }
        val earliest = overview.firstDate?.let { StatisticsPeriod.containing(kind, it).start }
        val latestDate = overview.lastDate?.takeIf { it.isAfter(today()) } ?: today()
        val latest = StatisticsPeriod.containing(kind, latestDate).start
        binding.sportPeriodPrevious.isEnabled = earliest != null && shown.start.isAfter(earliest)
        binding.sportPeriodNext.isEnabled = shown.start.isBefore(latest)
    }

    /**
     * Locale-formatted, e.g. "Sep 22 - 28, 2026", "September 2026", "2026", "Sep 3, 2025 - Feb 1, 2026".
     */
    private fun periodTitle(shown: StatisticsPeriod): String {
        fun millis(date: LocalDate) = date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC).toEpochMilli()
        fun range(start: LocalDate, end: LocalDate, flags: Int): String = DateUtils.formatDateRange(
            requireContext(),
            Formatter(StringBuilder(), Locale.getDefault()),
            millis(start),
            millis(end),
            flags or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR,
            "UTC",
        ).toString()

        return when (shown.kind) {
            PeriodKind.WEEK, PeriodKind.CUSTOM -> range(shown.start, shown.endInclusive, DateUtils.FORMAT_ABBREV_MONTH)
            PeriodKind.MONTH -> range(shown.start, shown.start, DateUtils.FORMAT_NO_MONTH_DAY)
            PeriodKind.YEAR -> shown.start.year.toString()
        }
    }

    private fun render(newStats: PeriodStats) {
        stats = newStats
        renderChart(newStats)
        renderSummary(newStats)
        showContent()
    }

    private fun renderError() {
        stats = null
        binding.sportChart.showMessage(getString(R.string.statistics_load_failed))
        binding.sportChart.contentDescription = null
        binding.sportChartLegend.setSeries(emptyList())
        binding.sportTiles.removeAllViews()
        showContent()
    }

    private fun showContent() {
        binding.loadingSpinner.visibility = View.GONE
        binding.sportContent.visibility = View.VISIBLE
    }

    private fun renderChart(stats: PeriodStats) {
        val config = config ?: return
        val formatter = formatter()
        val colors = listOf(
            MaterialColors.getColor(binding.sportChart, com.google.android.material.R.attr.colorOnSurface),
            ContextCompat.getColor(requireContext(), R.color.chart_sport_statistics_second),
        )
        val charts = listOfNotNull(config.first, config.second).mapIndexed { index, set ->
            val metric = set.metric
            MetricChart(metric, getString(metric.labelRes()), colors[index], set.type, emptyAxisMaximum(metric)) { raw ->
                displayValue(metric, raw, formatter)
            }
        }

        val barLabel = if (stats.period.kind == PeriodKind.CUSTOM) customBarLabeler(stats.period) else null
        val spec = SportChartSpecBuilder.build(stats, charts.first(), charts.getOrNull(1), barLabel)
        binding.sportChart.selectionContent = { x -> selection(stats, x.toInt(), charts, formatter) }
        binding.sportChart.setSpec(spec)
        binding.sportChart.contentDescription =
            charts.joinToString("; ") { chartDescription(stats, it.metric, it.label, formatter) }
        binding.sportChartLegend.setSeries(spec.series.filter { it.points.isNotEmpty() })
    }

    private fun selection(
        stats: PeriodStats,
        slot: Int,
        charts: List<MetricChart>,
        formatter: WorkoutValueFormatter,
    ): ChartSelection {
        val title = slotTitle(stats.period, slot)
        val totals = stats.bars.firstOrNull { it.x == slot.toDouble() }?.totals
        val shown = charts.mapNotNull { chart ->
            val raw = totals?.let { chart.metric.valueOf(it) } ?: 0.0
            if (raw == 0.0) null else chart to formatMetric(chart.metric, raw, formatter)
        }
        return ChartSelection(
            title = title,
            rows = shown.map { (chart, value) -> ChartSelection.Row(chart.color, value) },
            description = (listOf(title) + shown.map { (chart, value) -> "${chart.label} $value" })
                .joinToString(". ", postfix = "."),
        )
    }

    /**
     * Locale-formatted, e.g. "Wed, Sep 24", "Sep 24, 2026", "September 2026".
     */
    private fun slotTitle(period: StatisticsPeriod, slot: Int): String {
        val date = period.barStart(slot)
        return when (period.kind) {
            PeriodKind.WEEK -> formatDate("EEEMMMd", date)
            PeriodKind.YEAR -> formatDate("yMMMM", date)
            PeriodKind.MONTH, PeriodKind.CUSTOM -> formatDate("yMMMd", date)
        }
    }

    /**
     * Locale-formatted, e.g. "Sep 1".
     */
    private fun customBarLabeler(period: StatisticsPeriod): (Int) -> String =
        { slot -> formatDate("MMMd", period.barStart(slot)) }

    private fun formatDate(skeleton: String, date: LocalDate): String {
        val locale = Locale.getDefault()
        return DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
    }

    private fun chartDescription(
        stats: PeriodStats,
        metric: SportMetric,
        label: String,
        formatter: WorkoutValueFormatter,
    ): String = getString(
        if (stats.period.kind == PeriodKind.YEAR) {
            when (metric.aggregation) {
                MetricAggregation.TOTAL -> R.string.statistics_chart_description_monthly
                MetricAggregation.AVERAGE -> R.string.statistics_chart_description_monthly_average
                MetricAggregation.MAXIMUM -> R.string.statistics_chart_description_monthly_maximum
            }
        } else {
            when (metric.aggregation) {
                MetricAggregation.TOTAL -> R.string.statistics_chart_description_daily
                MetricAggregation.AVERAGE -> R.string.statistics_chart_description_daily_average
                MetricAggregation.MAXIMUM -> R.string.statistics_chart_description_daily_maximum
            }
        },
        label,
        periodTitle(stats.period),
        formatTotal(metric, metric.valueOf(stats.totals), formatter),
    )

    private fun displayValue(metric: SportMetric, raw: Double, formatter: WorkoutValueFormatter): Double = when (metric) {
        // From km, as meters would convert to feet on imperial
        SportMetric.DISTANCE ->
            formatter.convert(raw / METERS_PER_KM, ActivitySummaryEntries.UNIT_KILOMETERS, true).value
        SportMetric.DURATION -> raw / SECONDS_PER_HOUR
        SportMetric.AVG_PACE, SportMetric.MAX_PACE -> pace(raw).let { (value, unit) ->
            formatter.convert(value, unit, true).value * SECONDS_PER_MINUTE
        }
        SportMetric.AVG_SPEED, SportMetric.MAX_SPEED -> formatter.convert(raw, ActivitySummaryEntries.UNIT_METERS_PER_SECOND, true).value
        SportMetric.AVG_HEART_RATE, SportMetric.MAX_HEART_RATE, SportMetric.AVG_POWER, SportMetric.MAX_POWER,
        SportMetric.AVG_CADENCE, SportMetric.MAX_CADENCE, SportMetric.AVG_SWOLF, SportMetric.AVG_AEROBIC_EFFECT,
        SportMetric.AVG_ANAEROBIC_EFFECT -> raw
        SportMetric.ASCENT, SportMetric.DESCENT ->
            formatter.convert(raw, ActivitySummaryEntries.UNIT_METERS, true).value
        SportMetric.CALORIES, SportMetric.STEPS, SportMetric.TRAINING_LOAD, SportMetric.SETS -> raw
    }

    private fun emptyAxisMaximum(metric: SportMetric): Double = when (metric) {
        SportMetric.DISTANCE -> 10.0
        SportMetric.DURATION -> 1.0
        SportMetric.AVG_PACE, SportMetric.MAX_PACE, SportMetric.AVG_SPEED, SportMetric.MAX_SPEED,
        SportMetric.AVG_HEART_RATE, SportMetric.MAX_HEART_RATE, SportMetric.AVG_POWER, SportMetric.MAX_POWER,
        SportMetric.AVG_CADENCE, SportMetric.MAX_CADENCE,
        SportMetric.AVG_SWOLF, SportMetric.AVG_AEROBIC_EFFECT, SportMetric.AVG_ANAEROBIC_EFFECT -> 0.0
        SportMetric.CALORIES -> 500.0
        SportMetric.ASCENT, SportMetric.DESCENT -> 100.0
        SportMetric.STEPS -> 1000.0
        SportMetric.TRAINING_LOAD -> 100.0
        SportMetric.SETS -> 10.0
    }

    private fun formatTotal(metric: SportMetric, raw: Double, formatter: WorkoutValueFormatter): String =
        if (metric.lineOnly && raw == 0.0) getString(R.string.stats_empty_value) else formatMetric(metric, raw, formatter)

    private fun formatMetric(metric: SportMetric, raw: Double, formatter: WorkoutValueFormatter): String = when (metric) {
        SportMetric.DISTANCE -> formatter.formatValue(raw / METERS_PER_KM, ActivitySummaryEntries.UNIT_KILOMETERS)
        SportMetric.DURATION -> formatter.formatValue(raw, ActivitySummaryEntries.UNIT_SECONDS)
        SportMetric.AVG_PACE, SportMetric.MAX_PACE -> pace(raw).let { (value, unit) -> formatter.formatValue(value, unit) }
        SportMetric.AVG_SPEED, SportMetric.MAX_SPEED ->
            formatter.formatValue(raw, ActivitySummaryEntries.UNIT_METERS_PER_SECOND)
        SportMetric.AVG_HEART_RATE, SportMetric.MAX_HEART_RATE ->
            formatter.formatValue(raw.roundToLong(), ActivitySummaryEntries.UNIT_BPM)
        SportMetric.AVG_POWER, SportMetric.MAX_POWER ->
            formatter.formatValue(raw.roundToLong(), ActivitySummaryEntries.UNIT_WATT)
        SportMetric.AVG_CADENCE, SportMetric.MAX_CADENCE -> overview?.cadenceUnit?.let { formatter.formatValue(raw.roundToLong(), it) }
            ?: raw.roundToLong().toString()
        SportMetric.AVG_AEROBIC_EFFECT, SportMetric.AVG_ANAEROBIC_EFFECT ->
            String.format(Locale.getDefault(), "%.1f", raw)
        SportMetric.CALORIES -> formatter.formatValue(raw.roundToLong(), ActivitySummaryEntries.UNIT_KCAL)
        SportMetric.ASCENT, SportMetric.DESCENT ->
            formatter.formatValue(raw.roundToLong(), ActivitySummaryEntries.UNIT_METERS)
        SportMetric.STEPS -> formatter.formatValue(raw.roundToLong(), ActivitySummaryEntries.UNIT_STEPS)
        SportMetric.AVG_SWOLF, SportMetric.TRAINING_LOAD, SportMetric.SETS -> raw.roundToLong().toString()
    }

    private fun renderSummary(stats: PeriodStats) {
        val formatter = formatter()
        val empty = getString(R.string.stats_empty_value)
        fun row(label: String, value: String) = label to if (stats.count == 0) empty else value

        val rows = listOf(row(getString(R.string.statistics_sessions), stats.count.toString())) +
            overview?.availableMetrics.orEmpty().map { metric ->
                row(getString(metric.labelRes()), formatTotal(metric, metric.valueOf(stats.totals), formatter))
            }

        binding.sportTiles.removeAllViews()
        addSectionHeader(binding.sportTiles, requireContext(), getString(R.string.statistics_summary), showDivider = true)
        addDetailRows(binding.sportTiles, requireContext(), rows)
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(SportStatisticsFragment::class.java)
        private const val ARG_KIND_CODE = "kindCode"
        private const val STATE_KIND = "kind"
        private const val STATE_PERIOD_START = "periodStart"
        private const val STATE_PERIOD_END = "periodEnd"
        private const val TAG_RANGE_PICKER = "rangePicker"

        // Color.TRANSPARENT would remove the tint instead of hiding the indicator
        private const val HIDDEN_INDICATOR_COLOR = 0x00000001
        private val PRESET_KINDS = listOf(PeriodKind.WEEK, PeriodKind.MONTH, PeriodKind.YEAR)
        private const val PREF_CHART_CONFIG_PREFIX = "sport_statistics_chart_"
        private const val METERS_PER_KM = 1000.0
        private const val METERS_PER_100 = 100.0
        private const val SECONDS_PER_HOUR = 3600.0
        private const val SECONDS_PER_MINUTE = 60.0

        fun newInstance(kindCode: Int) = SportStatisticsFragment().apply {
            arguments = Bundle().apply { putInt(ARG_KIND_CODE, kindCode) }
        }
    }
}
