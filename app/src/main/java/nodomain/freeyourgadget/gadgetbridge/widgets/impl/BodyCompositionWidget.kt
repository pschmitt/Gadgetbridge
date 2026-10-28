/*  Copyright (C) 2026 oddballza

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.Color
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionCalculator
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionEstimates
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope

/**
 * Base for the widgets showing a body composition value of the most recent weight
 * measurement. Like the weight widget they show the last known measurement rather than one
 * for the selected day, since a scale is not stepped on every day. The values are estimated
 * from the measurement's impedance when shown, the same way the weight chart does it.
 */
abstract class BodyCompositionWidget : MetricWidget<BodyCompositionWidget.Data>() {
    override val chartTab = "weight"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsWeightMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val sample = WeightWidget.latestWeightSample(scope)
        val composition = sample?.let { s -> scope.db { db -> BodyCompositionEstimates.composition(db.daoSession, s) } }
        val history = if (config.isLarge) {
            WeightWidget.history(scope) { session, s -> BodyCompositionEstimates.composition(session, s)?.let { valueOf(it) } }
        } else {
            null
        }
        return Data(composition, ActivityUser().gender, history, scope.dayStart)
    }

    override fun previewData(): Data {
        val gender = ActivityUser().gender
        val weight = WeightWidget.previewData()
        val history = weight.history.orEmpty().mapNotNull { point ->
            BodyCompositionCalculator.compute(gender, 35, 178, point.value, 500f)?.let { TimedValue(point.seconds, valueOf(it)) }
        }
        return Data(BodyCompositionCalculator.compute(gender, 35, 178, 76f, 500f), gender, history, weight.dayStart)
    }

    final override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val value = data.composition?.let { valueOf(it) }
            ?: return emptyModel(context, GaugeSpec.Progress(Color.GRAY, -1f))
        val zones = zones(data)
        val chart = when {
            zones != null -> MiniChart.ZoneBar(zones.colors, zones.segments, scale(value))
            showsScale -> MiniChart.Progress(colorFor(value, data), scale(value))
            else -> null
        }
        val line = data.history?.takeIf { config.isLarge && it.isNotEmpty() }
            ?.let { WeightWidget.historyLine(it, data.dayStart, colorFor(value, data), historyPadding) }
        return TileModel(
            value = format(context, value),
            gauge = GaugeSpec.Progress(colorFor(value, data), scale(value)),
            status = zones?.let { StatusLine.Chip(colorFor(value, data), context.getString(zones.labelFor(value))) },
            chart = if (config.isTall && line != null) null else chart,
            largeChart = line,
            axis = if (line != null) WeightWidget.historyAxis(data.dayStart) else emptyList(),
        )
    }

    /**
     * Whether the number style shows where the value sits on the gauge's scale.
     */
    protected open val showsScale: Boolean = true

    protected open val historyPadding: Float = 0.5f

    /**
     * The categories the value is judged by, or null for values without a generally agreed range.
     */
    protected open fun zones(data: Data): Zones? = null

    /**
     * Categories on the gauge's 0..1 scale, and the label for a value.
     */
    class Zones(val colors: IntArray, val segments: FloatArray, val labelFor: (Float) -> Int)

    /** The value this widget shows. */
    protected abstract fun valueOf(composition: BodyCompositionCalculator.BodyComposition): Float

    protected abstract fun format(context: Context, value: Float): String

    /** Where the value sits on the gauge, as a 0..1 fraction. */
    protected abstract fun scale(value: Float): Float

    /**
     * Colour for the value. Neutral by default, for values without a generally agreed
     * healthy range; body fat overrides it with published categories.
     */
    protected open fun colorFor(value: Float, data: Data): Int = NEUTRAL

    /**
     * [history] holds the last 30 days' values, oldest first, only for the tall and full-width styles.
     */
    data class Data(
        val composition: BodyCompositionCalculator.BodyComposition?,
        val gender: Int,
        val history: List<TimedValue>? = null,
        val dayStart: Long = 0,
    )

    companion object {
        internal val GREEN = Color.rgb(76, 175, 80)
        internal val ORANGE = Color.rgb(255, 152, 0)
        internal val RED = Color.rgb(244, 67, 54)
        internal val NEUTRAL = Color.rgb(3, 169, 244)

        internal fun fraction(value: Float, min: Float, max: Float): Float =
            ((value - min) / (max - min)).coerceIn(0f, 1f)
    }
}
