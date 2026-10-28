package nodomain.freeyourgadget.gadgetbridge.activities.dashboard

import android.animation.Keyframe
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.core.animation.doOnEnd
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.Prefs
import nodomain.freeyourgadget.gadgetbridge.widgets.GridRect
import nodomain.freeyourgadget.gadgetbridge.widgets.PlacedWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetGrid
import nodomain.freeyourgadget.gadgetbridge.widgets.bindUnsafe
import kotlin.math.roundToInt

/**
 * Puts each [PlacedWidget] in a [MaterialCardView] in a [WidgetGridRecyclerView], and binds its loaded data.
 */
class DashboardGridBinder(
    private val recyclerView: WidgetGridRecyclerView,
) : RecyclerView.Adapter<DashboardGridBinder.CellHolder>(), StackedGridLayoutManager.ItemSpecs {
    interface Callbacks {
        fun onLongPress(item: PlacedWidget, card: View)

        /**
         * A tap on the edit button of [item].
         */
        fun onEditClick(item: PlacedWidget, button: View)

        /**
         * A request to move [item] by [offset] positions in the order.
         */
        fun onMove(item: PlacedWidget, offset: Int)
    }

    class CellHolder(val cell: Cell) : RecyclerView.ViewHolder(cell.card)

    class Cell(
        val card: MaterialCardView,
        val slot: FrameLayout,
        val error: View,
        val editButton: View,
        val foreground: Drawable?,
    ) {
        var instanceId: String? = null
        var placed: PlacedWidget? = null
        var content: View? = null
        var moveActions: List<Int> = emptyList()
    }

    private val layoutManager = StackedGridLayoutManager(recyclerView.context)
    private val spacing = recyclerView.resources.getDimensionPixelSize(R.dimen.dashboard_grid_spacing)
    private val density = recyclerView.resources.displayMetrics.density
    private val items = mutableListOf<PlacedWidget>()
    private var states: Map<String, WidgetState> = emptyMap()
    private val stableIds = mutableMapOf<String, Long>()

    /**
     * The dashboard-wide device filter, inherited by instances whose own filter mode is "inherit".
     */
    var dashboardShowAllDevices: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            rebindVisible()
        }
    var dashboardDeviceList: Set<String> = emptySet()
        set(value) {
            if (field == value) return
            field = value
            rebindVisible()
        }

    /**
     * The epoch second of the currently selected dashboard day.
     */
    var timestamp: Int = 0
        set(value) {
            if (field == value) return
            field = value
            rebindVisible()
        }

    var cardsEnabled: Boolean = true
        set(value) {
            if (field == value) return
            field = value
            forEachCell { applyStyle(it) }
        }

    var callbacks: Callbacks? = null

    var draggedId: String? = null

    /**
     * Whether the cards show their edit button instead of opening the chart on a tap.
     */
    var editing: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            forEachCell {
                applyStyle(it)
                bindListeners(it)
            }
        }

    var columns: Int
        get() = layoutManager.columns
        set(value) {
            layoutManager.columns = value
        }

    init {
        setHasStableIds(true)
        layoutManager.itemSpecs = this
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = this
        recyclerView.itemAnimator = object : DefaultItemAnimator() {
            override fun animateMove(
                holder: RecyclerView.ViewHolder,
                fromX: Int,
                fromY: Int,
                toX: Int,
                toY: Int,
            ): Boolean {
                if (draggedId != null && (holder as? CellHolder)?.cell?.instanceId == draggedId) {
                    dispatchMoveFinished(holder)
                    return false
                }
                val moving = super.animateMove(holder, fromX, fromY, toX, toY)
                // super cancels the animation that lowers a dropped card
                if (holder.itemView.translationZ != 0f) {
                    holder.itemView.animate().translationZ(0f)
                }
                return moving
            }
        }.apply { supportsChangeAnimations = false }
    }

    override fun getItemCount(): Int = items.size

    override fun itemCount(): Int = items.size

    override fun itemX(position: Int): Int = items.getOrNull(position)?.x ?: 0

    override fun itemWidth(position: Int): Int = items.getOrNull(position)?.variant?.width ?: 1

    override fun itemHeightPx(position: Int): Int =
        ((items.getOrNull(position)?.variant?.heightDp ?: 0) * density).roundToInt()

    override fun getItemId(position: Int): Long =
        stableIds.getOrPut(items[position].instance.instanceId) { stableIds.size.toLong() }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): CellHolder {
        val card =
            LayoutInflater.from(parent.context).inflate(R.layout.dashboard_widget_card, parent, false) as MaterialCardView
        return CellHolder(
            Cell(
                card,
                card.findViewById(R.id.dashboard_widget_content),
                card.findViewById(R.id.dashboard_widget_error),
                card.findViewById(R.id.dashboard_widget_edit),
                card.foreground,
            ),
        )
    }

    override fun onBindViewHolder(holder: CellHolder, position: Int) {
        val item = items[position]
        val cell = holder.cell
        val previous = cell.placed
        if (cell.instanceId != item.instance.instanceId ||
            previous == null ||
            previous.widget.id != item.widget.id ||
            previous.variant != item.variant
        ) {
            cell.slot.removeAllViews()
            val content = item.widget.createView(LayoutInflater.from(cell.slot.context), cell.slot, item.variant)
            cell.slot.addView(content)
            cell.content = content
            cell.instanceId = item.instance.instanceId
        }
        cell.placed = item

        applyStyle(cell)
        bind(cell)
    }

    override fun onBindViewHolder(holder: CellHolder, position: Int, payloads: List<Any>) {
        val cell = holder.cell
        if (payloads.isEmpty() ||
            payloads.any { it != PAYLOAD_PLACEMENT } ||
            cell.instanceId != items[position].instance.instanceId
        ) {
            onBindViewHolder(holder, position)
            return
        }
        cell.placed = items[position]
        bindListeners(cell)
    }

    fun cardOf(instanceId: String): View? {
        val position = positionOf(instanceId)
        if (position < 0) return null
        return recyclerView.findViewHolderForAdapterPosition(position)?.itemView
    }

    fun instanceIdOf(card: View): String? {
        val position = recyclerView.getChildAdapterPosition(card)
        return items.getOrNull(position)?.instance?.instanceId
    }

    /**
     * Draws a border in the primary color around the card of [instanceId]. The border fades in, stays for a few
     * seconds, and fades out.
     */
    fun highlight(instanceId: String) {
        val card = cardOf(instanceId) ?: return
        card.doOnLayout {
            val density = card.resources.displayMetrics.density
            val border = GradientDrawable()
            border.cornerRadius = TILE_RADIUS_DP * density
            border.setStroke(
                (HIGHLIGHT_STROKE_DP * density).roundToInt(),
                MaterialColors.getColor(card, androidx.appcompat.R.attr.colorPrimary),
            )
            border.alpha = 0
            // In the overlay of the grid, so that it draws above the dashed border, in the foreground of the card
            recyclerView.overlay.add(border)

            val alpha = PropertyValuesHolder.ofKeyframe(
                "alpha",
                Keyframe.ofInt(0f, 0),
                Keyframe.ofInt(0.1f, 255),
                Keyframe.ofInt(0.9f, 255),
                Keyframe.ofInt(1f, 0),
            )
            ValueAnimator.ofPropertyValuesHolder(alpha).apply {
                duration = HIGHLIGHT_DURATION_MS
                addUpdateListener {
                    // The card can move while the border shows
                    val left = card.left + card.translationX.roundToInt()
                    val top = card.top + card.translationY.roundToInt()
                    border.setBounds(left, top, left + card.width, top + card.height)
                    border.alpha = it.animatedValue as Int
                }
                doOnEnd { recyclerView.overlay.remove(border) }
                start()
            }
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun submitItems(newItems: List<PlacedWidget>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    /**
     * Updates the load states. Binds only the instances whose state changed.
     */
    fun submitStates(newStates: Map<String, WidgetState>) {
        val oldStates = states
        states = newStates
        for (position in items.indices) {
            val instanceId = items[position].instance.instanceId
            if (oldStates[instanceId] != newStates[instanceId]) {
                notifyItemChanged(position)
            }
        }
    }

    fun ids(): List<String> = items.map { it.instance.instanceId }

    fun columnOf(instanceId: String): Int = items.getOrNull(positionOf(instanceId))?.x ?: 0

    fun widthOf(instanceId: String): Int = items.getOrNull(positionOf(instanceId))?.variant?.width ?: 1

    fun heightPxOf(instanceId: String): Int = itemHeightPx(positionOf(instanceId))

    fun arrangement(): List<Pair<String, Int>> = items.map { it.instance.instanceId to it.x }

    fun moveBefore(instanceId: String, beforeId: String?) {
        val from = positionOf(instanceId)
        if (from < 0) return
        val to = if (beforeId == null) {
            items.size - 1
        } else {
            val before = positionOf(beforeId)
            if (before < 0) return
            if (before < from) before else before - 1
        }
        if (from == to) return
        val item = items.removeAt(from)
        items.add(to, item)
        notifyItemMoved(from, to)
    }

    fun setColumn(instanceId: String, column: Int) {
        val position = positionOf(instanceId)
        if (position < 0) return
        val item = items[position]
        if (item.x == column) return
        items[position] = item.copy(x = column)
        notifyItemChanged(position, PAYLOAD_PLACEMENT)
    }

    /**
     * Puts [instanceId] at the first free column under the items before it.
     */
    fun placeAtFirstFree(instanceId: String) {
        val position = positionOf(instanceId)
        if (position < 0) return
        val before = items.take(position).map(::rectOf)
        val width = items[position].variant.width
        val column = WidgetGrid.firstFreeColumn(columns, WidgetGrid.stack(columns, before), width)
        setColumn(instanceId, column)
    }

    private fun positionOf(instanceId: String): Int =
        items.indexOfFirst { it.instance.instanceId == instanceId }

    private fun rectOf(item: PlacedWidget): GridRect =
        GridRect(item.x, 0, item.variant.width, (item.variant.heightDp * density).roundToInt() + spacing * 2)

    private fun forEachCell(block: (Cell) -> Unit) {
        for (index in 0 until recyclerView.childCount) {
            val holder = recyclerView.getChildViewHolder(recyclerView.getChildAt(index)) as? CellHolder ?: continue
            block(holder.cell)
        }
    }

    private fun rebindVisible() = forEachCell(::bind)

    /**
     * Applies the card style. In the edit mode, the border of the card is replaced by a dashed border.
     */
    private fun applyStyle(cell: Cell) {
        val card = cell.card
        applyCardStyle(card, cardsEnabled)
        if (!editing) {
            card.foreground = cell.foreground
            return
        }

        val density = card.resources.displayMetrics.density
        val dashed = GradientDrawable()
        dashed.cornerRadius = TILE_RADIUS_DP * density
        dashed.setStroke(
            (TILE_STROKE_DP * density).roundToInt(),
            MaterialColors.getColor(card, R.attr.stat_tile_border),
            EDIT_DASH_DP * density,
            EDIT_DASH_DP * density,
        )
        card.strokeWidth = 0
        card.foreground = LayerDrawable(listOfNotNull(cell.foreground, dashed).toTypedArray())
    }

    private fun bind(cell: Cell) {
        val item = cell.placed ?: return
        val view = cell.content ?: return

        val config = configOf(item)
        val state = states[item.instance.instanceId]
        cell.slot.isInvisible = state is WidgetState.Error
        cell.error.isVisible = state is WidgetState.Error
        when (state) {
            is WidgetState.Ready -> {
                item.widget.bindUnsafe(view, item.variant, config, state.data)
                cell.card.contentDescription = view.contentDescription
            }

            is WidgetState.Error -> cell.card.contentDescription = cell.error.contentDescription

            else -> {
                item.widget.bindLoading(view, item.variant, config)
                cell.card.contentDescription = null
            }
        }
        bindListeners(cell)
    }

    private fun bindListeners(cell: Cell) {
        val item = cell.placed ?: return
        val view = cell.content ?: return
        val card = cell.card

        card.setOnLongClickListener {
            callbacks?.onLongPress(item, card)
            true
        }
        cell.editButton.isVisible = editing
        cell.editButton.setOnClickListener { callbacks?.onEditClick(item, it) }
        when {
            editing -> card.setOnClickListener(null)
            states[item.instance.instanceId] is WidgetState.Ready -> card.setOnClickListener {
                item.widget.onClick(view, item.variant, configOf(item), timestamp)
            }

            else -> card.setOnClickListener(null)
        }

        cell.moveActions.forEach { ViewCompat.removeAccessibilityAction(card, it) }
        cell.moveActions = if (editing) {
            listOf(R.string.widget_move_up to -1, R.string.widget_move_down to 1).map { (label, offset) ->
                ViewCompat.addAccessibilityAction(card, card.context.getString(label)) { _, _ ->
                    callbacks?.onMove(item, offset)
                    true
                }
            }
        } else {
            emptyList()
        }
    }

    private fun configOf(item: PlacedWidget): WidgetConfig {
        val prefs = Prefs(GBApplication.getWidgetSharedPrefs(item.instance.instanceId))
        return WidgetConfig(item.instance, prefs, dashboardShowAllDevices, dashboardDeviceList, item.variant)
    }

    private companion object {
        const val PAYLOAD_PLACEMENT = "placement"
    }
}

private const val TILE_STROKE_DP = 1.5f
private const val TILE_RADIUS_DP = 12f
private const val EDIT_DASH_DP = 6f
private const val HIGHLIGHT_STROKE_DP = 2f
private const val HIGHLIGHT_DURATION_MS = 2500L

/**
 * Draws [card] as a tile, or without a fill and a border if [cardsEnabled] is false.
 */
fun applyCardStyle(card: MaterialCardView, cardsEnabled: Boolean) {
    val density = card.resources.displayMetrics.density
    card.cardElevation = 0f
    if (cardsEnabled) {
        // Same fill as the stat tiles, so it follows the theme.
        card.setCardBackgroundColor(MaterialColors.getColor(card, R.attr.stat_tile_bg))
        card.strokeColor = MaterialColors.getColor(card, R.attr.stat_tile_border)
        card.strokeWidth = (TILE_STROKE_DP * density).roundToInt()
        card.radius = TILE_RADIUS_DP * density
    } else {
        card.setCardBackgroundColor(Color.TRANSPARENT)
        card.strokeWidth = 0
        card.radius = 0f
    }
}
