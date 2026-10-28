package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.BottomsheetSportChartConfigBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemChartMetricOptionBinding
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemChartSetEditorBinding

/**
 * Picks the chart's data sets. Returns the result under [REQUEST_KEY].
 */
class ChartConfigBottomSheet : BottomSheetDialogFragment() {
    private var _binding: BottomsheetSportChartConfigBinding? = null
    private val binding get() = _binding!!

    private lateinit var available: List<SportMetric>
    private lateinit var draft: ChartConfig
    private var rendering = false

    override fun getTheme() = R.style.ThemeOverlay_App_ChartConfigSheet

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = BottomsheetSportChartConfigBinding.inflate(inflater, container, false)
        val args = requireArguments()
        available = args.getStringArrayList(ARG_AVAILABLE).orEmpty().map { SportMetric.valueOf(it) }
        draft = ChartConfig.parse(savedInstanceState?.getString(STATE_DRAFT) ?: args.getString(ARG_CONFIG))
            ?.restrictedTo(available)
            ?: ChartConfig.default(available)

        binding.firstSet.setLabel.setText(R.string.statistics_chart_data_set_1)
        binding.secondSet.setLabel.setText(R.string.statistics_chart_data_set_2)
        binding.firstSet.setField.setOnClickListener { field ->
            showOptions(field, withNone = false, draft.first.metric, draft.second?.metric, R.string.statistics_chart_set_2) {
                it?.let { metric -> draft = draft.copy(first = draft.first.withMetric(metric)) }
            }
        }
        binding.secondSet.setField.setOnClickListener { field ->
            showOptions(field, withNone = true, draft.second?.metric, draft.first.metric, R.string.statistics_chart_set_1) {
                draft = draft.copy(second = it?.let { metric -> draft.second?.withMetric(metric) ?: ChartSet.of(metric) })
            }
        }
        listenToType(binding.firstSet) { type -> draft = draft.copy(first = draft.first.copy(type = type)) }
        listenToType(binding.secondSet) { type -> draft = draft.copy(second = draft.second?.copy(type = type)) }

        binding.chartConfigCancel.setOnClickListener { dismiss() }
        binding.chartConfigApply.setOnClickListener {
            setFragmentResult(REQUEST_KEY, Bundle().apply { putString(RESULT_CONFIG, draft.serialize()) })
            dismiss()
        }

        render()
        return binding.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DRAFT, draft.serialize())
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun listenToType(set: ItemChartSetEditorBinding, onChange: (SeriesType) -> Unit) {
        set.setType.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (rendering || !isChecked) {
                return@addOnButtonCheckedListener
            }
            onChange(if (checkedId == R.id.set_type_bar) SeriesType.BAR else SeriesType.LINE)
            render()
        }
    }

    private fun render() {
        rendering = true
        renderSet(binding.firstSet, draft.first)
        renderSet(binding.secondSet, draft.second)
        binding.firstSet.setSwatch.backgroundTintList = ColorStateList.valueOf(
            MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorOnSurface)
        )
        binding.secondSet.setSwatch.backgroundTintList = ColorStateList.valueOf(
            if (draft.second != null) {
                ContextCompat.getColor(requireContext(), R.color.chart_sport_statistics_second)
            } else {
                MaterialColors.getColor(binding.root, com.google.android.material.R.attr.colorSurfaceContainerHighest)
            }
        )
        rendering = false
    }

    private fun renderSet(set: ItemChartSetEditorBinding, chartSet: ChartSet?) {
        set.setField.text = chartSet?.let { getString(it.metric.labelRes()) } ?: getString(R.string.none)
        set.setType.check(if (chartSet?.type == SeriesType.LINE) R.id.set_type_line else R.id.set_type_bar)
        set.setType.isEnabled = chartSet != null
        set.setTypeBar.isEnabled = chartSet != null && !chartSet.metric.lineOnly
        set.setTypeLine.isEnabled = chartSet != null
        set.setHint.visibility = if (chartSet?.metric?.lineOnly == true) View.VISIBLE else View.GONE
    }

    /**
     * Metric dropdown for one data set.
     */
    private fun showOptions(
        anchor: View,
        withNone: Boolean,
        current: SportMetric?,
        taken: SportMetric?,
        otherSetLabel: Int,
        onPick: (SportMetric?) -> Unit,
    ) {
        val options: List<SportMetric?> = (if (withNone) listOf(null) else emptyList()) + available
        val popup = ListPopupWindow(requireContext())
        popup.anchorView = anchor
        popup.width = anchor.width
        popup.isModal = true
        popup.setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.bg_chart_metric_dropdown))
        popup.setAdapter(OptionAdapter(options, current, taken, getString(otherSetLabel)))
        popup.setOnItemClickListener { _, _, position, _ ->
            onPick(options[position])
            render()
            popup.dismiss()
        }
        popup.show()
    }

    private inner class OptionAdapter(
        private val options: List<SportMetric?>,
        private val current: SportMetric?,
        private val taken: SportMetric?,
        private val takenTag: String,
    ) : BaseAdapter() {
        override fun getCount() = options.size

        override fun getItem(position: Int) = options[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun isEnabled(position: Int) = options[position].let { it == null || it != taken }

        override fun areAllItemsEnabled() = false

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView?.let { ItemChartMetricOptionBinding.bind(it) }
                ?: ItemChartMetricOptionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            val option = options[position]
            val selected = option == current
            val disabled = !isEnabled(position)
            row.optionLabel.text = option?.let { getString(it.labelRes()) } ?: getString(R.string.none)
            row.optionLabel.setTextColor(
                MaterialColors.getColor(
                    row.root,
                    if (disabled) com.google.android.material.R.attr.colorOutline
                    else com.google.android.material.R.attr.colorOnSurface,
                )
            )
            row.optionTag.text = takenTag
            row.optionTag.visibility = if (disabled) View.VISIBLE else View.GONE
            row.optionCheck.visibility = if (selected) View.VISIBLE else View.GONE
            row.root.setBackgroundColor(
                if (selected) {
                    MaterialColors.getColor(row.root, com.google.android.material.R.attr.colorSurfaceContainerHighest)
                } else {
                    0
                }
            )
            return row.root
        }
    }

    companion object {
        const val TAG = "ChartConfigBottomSheet"
        const val REQUEST_KEY = "sportChartConfig"
        const val RESULT_CONFIG = "config"
        private const val ARG_AVAILABLE = "available"
        private const val ARG_CONFIG = "config"
        private const val STATE_DRAFT = "draft"

        fun newInstance(available: List<SportMetric>, config: ChartConfig) = ChartConfigBottomSheet().apply {
            arguments = Bundle().apply {
                putStringArrayList(ARG_AVAILABLE, ArrayList(available.map { it.name }))
                putString(ARG_CONFIG, config.serialize())
            }
        }
    }
}
