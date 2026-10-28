package nodomain.freeyourgadget.gadgetbridge.activities.charts

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.github.mikephil.charting.charts.Chart
import com.google.android.material.card.MaterialCardView
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.workouts.applyStatTileStyle
import nodomain.freeyourgadget.gadgetbridge.database.DBHandler
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import org.slf4j.LoggerFactory

/**
 * A grid of cards, one for each enabled chart, with the value of the chart for the selected time range.
 */
class ChartsOverviewFragment : AbstractChartFragment<ChartsOverviewFragment.OverviewData>() {
    private val adapter = OverviewAdapter { chartName ->
        chartsHost.showChart(chartName)
    }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var items: List<OverviewItem> = emptyList()

    /**
     * Incremented on each refresh, to drop the values of a previous refresh.
     */
    @Volatile
    private var generation = 0

    override fun getTitle(): String = getString(R.string.workout_tab_overview)

    override fun init() {
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val view = inflater.inflate(R.layout.fragment_charts_overview, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.charts_overview_list)
        val spanCount = (resources.configuration.screenWidthDp / CARD_MIN_WIDTH_DP).coerceAtLeast(2)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), spanCount)
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        recyclerView.adapter = adapter
        if (items.isEmpty()) {
            submitItems(createItems(requireContext(), chartsHost.device))
        }
        return view
    }

    override fun refresh() {
        generation++
        submitItems(items.map { it.copy(value = null, loading = true) })
        super.refresh()
    }

    override fun refreshInBackground(chartsHost: ChartsHost, db: DBHandler, device: GBDevice): OverviewData {
        val context = GBApplication.getContext()
        val provider = device.deviceCoordinator.chartsProvider
        val timestamp = toTimestamp(chartsHost.endDate)

        val refreshGeneration = generation

        val loadedItems = createItems(context, device).map { item ->
            val value = try {
                provider.getChartValue(context, db, device, item.chartName, timestamp)
            } catch (e: Exception) {
                LOG.error("Failed to get the value for {}", item.chartName, e)
                null
            }
            val loadedItem = item.copy(value = value ?: getString(R.string.stats_empty_value), loading = false)
            mainHandler.post {
                if (generation == refreshGeneration) {
                    submitItems(items.map { if (it.chartName == loadedItem.chartName) loadedItem else it })
                }
            }
            loadedItem
        }

        return OverviewData(loadedItems)
    }

    private fun createItems(context: Context, device: GBDevice): List<OverviewItem> {
        val provider = device.deviceCoordinator.chartsProvider
        return provider.getEnabledCharts(device)
            .filter { it != DefaultChartsProvider.OVERVIEW }
            .map { chartName ->
                OverviewItem(
                    chartName,
                    provider.getChartLabel(context, device, chartName),
                    provider.getChartIcon(chartName),
                    null,
                    true
                )
            }
    }

    private fun submitItems(newItems: List<OverviewItem>) {
        items = newItems
        adapter.submitList(newItems)
    }

    override fun updateChartsnUIThread(chartsData: OverviewData) {
        submitItems(chartsData.items)
    }

    override fun renderCharts() {
    }

    override fun setupLegend(chart: Chart<*>) {
    }

    class OverviewData(val items: List<OverviewItem>) : ChartsData()

    data class OverviewItem(
        val chartName: String,
        val label: String,
        @param:DrawableRes val icon: Int,
        val value: String?,
        val loading: Boolean,
    )

    private class OverviewAdapter(
        private val onClick: (String) -> Unit
    ) : ListAdapter<OverviewItem, OverviewAdapter.ViewHolder>(DIFF_CALLBACK) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_charts_overview, parent, false)
            applyStatTileStyle(view as MaterialCardView)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.icon.setImageResource(item.icon)
            holder.label.text = item.label
            holder.value.text = item.value
            holder.valueFrame.isGone = item.value?.isEmpty() == true
            holder.progress.isInvisible = !item.loading
            holder.itemView.setOnClickListener { onClick(item.chartName) }
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.charts_overview_icon)
            val label: TextView = view.findViewById(R.id.charts_overview_label)
            val value: TextView = view.findViewById(R.id.charts_overview_value)
            val valueFrame: View = view.findViewById(R.id.charts_overview_value_frame)
            val progress: View = view.findViewById(R.id.charts_overview_progress)
        }
    }

    companion object {
        private val LOG = LoggerFactory.getLogger(ChartsOverviewFragment::class.java)

        private const val CARD_MIN_WIDTH_DP = 160

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<OverviewItem>() {
            override fun areItemsTheSame(oldItem: OverviewItem, newItem: OverviewItem) =
                oldItem.chartName == newItem.chartName

            override fun areContentsTheSame(oldItem: OverviewItem, newItem: OverviewItem) =
                oldItem == newItem
        }
    }
}
