package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.GoalRingsView
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.renderFlowLegend
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.renderHeader
import nodomain.freeyourgadget.gadgetbridge.activities.dashboard.renderLegendButton
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.DistanceUnit
import nodomain.freeyourgadget.gadgetbridge.widgets.GBWidget
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetActions
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetColors
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Concentric goal rings, outside in: steps, distance, active time and sleep. The small size lists each goal's
 * value next to the rings; the large one only shows the rings.
 */
object GoalsWidget : GBWidget<GoalsWidget.Data> {
    private const val LEGEND_ROW_GAP_DP = 4f
    private const val TALL_LEGEND_ROW_GAP_DP = 6f
    private const val TALL_RINGS_DP = 128f
    private const val VALUE_ROW_GAP_DP = 7f
    private const val METERS_PER_KM = 1000.0
    private const val MILES_PER_METER = 0.000621371

    enum class Goal(val label: Int, val color: Int) {
        STEPS(R.string.steps, WidgetColors.activity),
        DISTANCE(R.string.distance, WidgetColors.distance),
        ACTIVE_TIME(R.string.activity_list_summary_active_time, WidgetColors.activeTime),
        SLEEP(R.string.menuitem_sleep, WidgetColors.lightSleep),
    }

    /**
     * How far [goal] got: [current] against [target], in steps, meters or minutes.
     */
    class Progress(val goal: Goal, val current: Double, val target: Double) {
        val fraction: Float get() = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
    }

    override val id = "goals"
    override val label = R.string.widget_goals_title
    override val icon = R.drawable.ic_done_all
    override val variants = listOf(WidgetVariant.LARGE, WidgetVariant.SMALL, WidgetVariant.OVERVIEW_TALL)

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsActivityTracking(device)

    override fun settings(context: Context, variant: WidgetVariant): DeviceSettingsSpec = deviceSettings {
        switchSetting(
            key = "legend",
            title = R.string.pref_dashboard_widget_show_legend_title,
            summary = R.string.pref_dashboard_widget_show_legend_summary,
            icon = R.drawable.ic_legend_toggle,
            defaultValue = true,
            connectedOnly = false,
        )
    }

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val user = ActivityUser()
        val steps = scope.devices.any { it.deviceCoordinator.supportsStepCounter(it) }
        val sleep = scope.devices.any { it.deviceCoordinator.supportsSleepMeasurement(it) }
        val goals = buildList {
            if (steps) {
                add(Progress(Goal.STEPS, scope.stepsTotal().toDouble(), user.stepsGoal.toDouble()))
                add(Progress(Goal.DISTANCE, scope.distanceTotal().toDouble(), user.distanceGoalMeters.toDouble()))
                add(Progress(Goal.ACTIVE_TIME, scope.activeMinutesTotal().toDouble(), user.activeTimeGoalMinutes.toDouble()))
            }
            if (sleep) {
                add(Progress(Goal.SLEEP, scope.sleepMinutesTotal().toDouble(), user.sleepDurationGoal.toDouble()))
            }
        }
        return Data(goals)
    }

    override fun previewData(): Data = Data(
        listOf(
            Progress(Goal.STEPS, 6400.0, ActivityUser.defaultUserStepsGoal.toDouble()),
            Progress(Goal.DISTANCE, 4800.0, ActivityUser.defaultUserDistanceGoalMeters.toDouble()),
            Progress(Goal.ACTIVE_TIME, 42.0, ActivityUser.defaultUserActiveTimeGoalMinutes.toDouble()),
            Progress(Goal.SLEEP, 452.0, ActivityUser.defaultUserSleepDurationGoal.toDouble()),
        )
    )

    override fun createView(inflater: LayoutInflater, parent: ViewGroup, variant: WidgetVariant): View = when (variant) {
        WidgetVariant.SMALL -> inflater.inflate(R.layout.dashboard_widget_goals_wide, parent, false)
        WidgetVariant.OVERVIEW_TALL -> inflater.inflate(R.layout.dashboard_widget_goal_rings_tall, parent, false).also {
            it.findViewById<GoalRingsView>(R.id.tile_goal_rings).maxSizeDp = TALL_RINGS_DP
        }
        else -> inflater.inflate(R.layout.dashboard_widget_goal_rings, parent, false)
    }

    override fun bind(view: View, variant: WidgetVariant, config: WidgetConfig, data: Data) {
        val context = view.context
        renderHeader(view, config.title(context, label), icon)
        view.findViewById<GoalRingsView>(R.id.tile_goal_rings).setRings(data.goals.map { GoalRingsView.Ring(it.goal.color, it.fraction) })
        if (isSmall(config)) {
            renderValues(view.findViewById(R.id.tile_goal_values), data.goals.map { it.goal to valueText(context, it) })
        } else {
            view.findViewById<View>(R.id.tile_empty).visibility = if (data.goals.isEmpty()) View.VISIBLE else View.GONE
            renderFlowLegend(
                view.findViewById(R.id.tile_flow_legend),
                if (showLegend(config)) data.goals.map { it.goal.color to context.getString(it.goal.label) } else emptyList(),
                legendGap(config),
            )
        }
        renderLegendButton(
            view,
            config.title(context, label),
            if (isSmall(config) || showLegend(config)) emptyList() else data.goals.map { it.goal.color to context.getString(it.goal.label) },
        )
        view.contentDescription = describe(context, config, data)
    }

    override fun bindLoading(view: View, variant: WidgetVariant, config: WidgetConfig) {
        val context = view.context
        renderHeader(view, config.title(context, label), icon)
        view.findViewById<GoalRingsView>(R.id.tile_goal_rings).setRings(Goal.entries.map { GoalRingsView.Ring(it.color, -1f) })
        if (isSmall(config)) {
            renderValues(view.findViewById(R.id.tile_goal_values), Goal.entries.map { it to context.getString(R.string.stats_empty_value) })
        } else {
            renderFlowLegend(
                view.findViewById(R.id.tile_flow_legend),
                if (showLegend(config)) Goal.entries.map { it.color to context.getString(it.label) } else emptyList(),
                legendGap(config),
            )
        }
        renderLegendButton(view, "", emptyList())
    }

    override fun onClick(view: View, variant: WidgetVariant, config: WidgetConfig, timestamp: Int) {
        WidgetActions.openChart(view.context, config, this, "activitylist", label, "", timestamp)
    }

    private fun isSmall(config: WidgetConfig) = config.variant == WidgetVariant.SMALL

    private fun showLegend(config: WidgetConfig) = config.prefs.getBoolean("legend", true)

    private fun legendGap(config: WidgetConfig) =
        if (config.variant == WidgetVariant.OVERVIEW_TALL) TALL_LEGEND_ROW_GAP_DP else LEGEND_ROW_GAP_DP

    /**
     * One row per goal: its swatch and name, then the value right-aligned.
     */
    private fun renderValues(container: LinearLayout, rows: List<Pair<Goal, CharSequence>>) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(container.context)
        val density = container.resources.displayMetrics.density
        for ((index, row) in rows.withIndex()) {
            val (goal, value) = row
            val item = inflater.inflate(R.layout.dashboard_widget_goal_value_row, container, false)
            val swatch = GradientDrawable()
            swatch.setColor(goal.color)
            swatch.cornerRadius = 2 * density
            item.findViewById<View>(R.id.swatch).background = swatch
            item.findViewById<TextView>(R.id.swatch_label).text = container.context.getString(goal.label)
            item.findViewById<TextView>(R.id.goal_value).text = value
            val params = item.layoutParams as LinearLayout.LayoutParams
            params.topMargin = if (index > 0) (VALUE_ROW_GAP_DP * density).roundToInt() else 0
            container.addView(item, params)
        }
    }

    /**
     * "7,592 / 10,000", the current value emphasised.
     */
    private fun valueText(context: Context, progress: Progress): CharSequence {
        val (current, target) = formatted(context, progress)
        val text = SpannableStringBuilder(current)
        text.setSpan(ForegroundColorSpan(MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurface, Color.WHITE)), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        text.setSpan(TypefaceSpan("sans-serif-medium"), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val start = text.length
        text.append(context.getString(R.string.widget_value_target, target))
        text.setSpan(ForegroundColorSpan(GBApplication.getSecondaryTextColor(context)), start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return text
    }

    /**
     * The current value and the target as text: steps grouped, distance in km or mi, active time in minutes and
     * sleep in hours and minutes.
     */
    private fun formatted(context: Context, progress: Progress): Pair<String, String> = when (progress.goal) {
        Goal.STEPS -> progress.current.roundToInt().toString() to progress.target.roundToInt().toString()
        Goal.DISTANCE -> {
            val imperial = GBApplication.getPrefs().distanceUnit == DistanceUnit.IMPERIAL
            val factor = if (imperial) MILES_PER_METER else 1 / METERS_PER_KM
            val unit = context.getString(if (imperial) R.string.mi else R.string.km)
            val target = progress.target * factor
            val targetText = if (target % 1.0 == 0.0) target.toInt().toString() else String.format(Locale.getDefault(), "%.1f", target)
            String.format(Locale.getDefault(), "%.1f", progress.current * factor) to "$targetText $unit"
        }
        Goal.ACTIVE_TIME -> progress.current.roundToInt().toString() to context.getString(R.string.widget_value_minutes, progress.target.roundToInt())
        Goal.SLEEP -> duration(context, progress.current.roundToInt()) to duration(context, progress.target.roundToInt())
    }

    private fun duration(context: Context, minutes: Int): String = if (minutes % 60 == 0) {
        context.getString(R.string.widget_duration_hours, minutes / 60)
    } else {
        context.getString(R.string.widget_duration_hours_minutes, minutes / 60, minutes % 60)
    }

    private fun describe(context: Context, config: WidgetConfig, data: Data): String {
        val parts = data.goals.joinToString(", ") { progress ->
            val (current, target) = formatted(context, progress)
            context.getString(R.string.widget_description_of_goal, context.getString(progress.goal.label), current, target)
        }
        return context.getString(R.string.widget_overview_description, config.title(context, label), parts)
    }

    /**
     * The goals of the supported metrics.
     */
    class Data(val goals: List<Progress>)
}
