package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.R
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class StackedGridLayoutManagerTest {
    @Test
    fun stacksItemsInTheirColumns() {
        val context = RuntimeEnvironment.getApplication()
        val spacing = context.resources.getDimensionPixelSize(R.dimen.dashboard_grid_spacing)
        val recyclerView = WidgetGridRecyclerView(context)
        val adapter = TestAdapter(
            listOf(
                intArrayOf(0, 1, 100),
                intArrayOf(1, 1, 50),
                intArrayOf(0, 2, 100),
            ),
        )
        val layoutManager = StackedGridLayoutManager(context).apply {
            columns = 2
            itemSpecs = adapter
        }
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        recyclerView.measure(
            View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        recyclerView.layout(0, 0, 600, recyclerView.measuredHeight)

        assertEquals(3, recyclerView.childCount)
        assertEquals(200 + spacing * 4, recyclerView.measuredHeight)
        assertEquals(0, recyclerView.getChildAdapterPosition(recyclerView.getChildAt(0)))
        assertEquals(spacing, recyclerView.getChildAt(0).top)
        assertEquals(100 + spacing * 3, recyclerView.getChildAt(2).top)
        assertEquals(600 - spacing * 2, recyclerView.getChildAt(2).width)
    }

    private class TestAdapter(
        private val specs: List<IntArray>,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>(), StackedGridLayoutManager.ItemSpecs {
        override fun getItemCount(): Int = specs.size

        override fun itemCount(): Int = specs.size

        override fun itemX(position: Int): Int = specs[position][0]

        override fun itemWidth(position: Int): Int = specs[position][1]

        override fun itemHeightPx(position: Int): Int = specs[position][2]

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(View(parent.context)) {}

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
    }
}
