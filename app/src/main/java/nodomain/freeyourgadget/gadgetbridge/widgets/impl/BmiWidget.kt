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
import nodomain.freeyourgadget.gadgetbridge.util.BodyCompositionEstimates
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import java.util.Locale

/**
 * Body mass index of the most recent weight measurement, from the profile height. Coloured by
 * WHO class like the weight widget's gauge, but showing the index itself.
 */
object BmiWidget : MetricWidget<WeightWidget.Data>() {
    override val id = "bmi"
    override val label = R.string.body_mass_index
    override val icon = R.drawable.ic_monitor_weight
    override val chartTab = "weight"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsWeightMeasurement(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): WeightWidget.Data {
        val latest = WeightWidget.latestWeightSample(scope)
        val history = if (config.isLarge) WeightWidget.history(scope) { session, s -> BodyCompositionEstimates.bmi(session, s) } else null
        return WeightWidget.Data(latest?.weightKg?.toDouble() ?: 0.0, ActivityUser().heightCm, history, scope.dayStart)
    }

    override fun previewData(): WeightWidget.Data {
        val weight = WeightWidget.previewData()
        val history = weight.history.orEmpty().mapNotNull { point ->
            Bmi.of(point.value.toDouble(), 178)?.let { TimedValue(point.seconds, it.toFloat()) }
        }
        return WeightWidget.Data(76.0, 178, history, weight.dayStart)
    }

    override fun model(context: Context, config: WidgetConfig, data: WeightWidget.Data): TileModel {
        val bmi = Bmi.of(data.weightKg, data.heightCm)
            ?: return emptyModel(context, GaugeSpec.Progress(Color.GRAY, -1f), MiniChart.ZoneBar(Bmi.COLORS, Bmi.SEGMENTS, -1f))
        val line = data.history?.takeIf { config.isLarge && it.isNotEmpty() }
            ?.let { WeightWidget.historyLine(it, data.dayStart, Bmi.colorFor(bmi), HISTORY_PADDING) }
        return TileModel(
            value = String.format(Locale.getDefault(), "%.1f", bmi),
            gauge = GaugeSpec.Progress(Bmi.colorFor(bmi), Bmi.gaugeFraction(bmi)),
            status = StatusLine.Chip(Bmi.colorFor(bmi), context.getString(LABELS[Bmi.categoryOf(bmi)])),
            chart = if (config.isTall && line != null) null else MiniChart.ZoneBar(Bmi.COLORS, Bmi.SEGMENTS, Bmi.scaleFraction(bmi)),
            largeChart = line,
            axis = if (line != null) WeightWidget.historyAxis(data.dayStart) else emptyList(),
        )
    }

    private const val HISTORY_PADDING = 0.2f

    private val LABELS = intArrayOf(
        R.string.widget_status_underweight,
        R.string.normal,
        R.string.widget_status_overweight,
        R.string.widget_status_obese,
    )
}
