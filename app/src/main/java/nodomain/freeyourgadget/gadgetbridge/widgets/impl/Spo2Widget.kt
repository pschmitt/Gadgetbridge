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
import nodomain.freeyourgadget.gadgetbridge.model.Spo2Sample
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import org.slf4j.LoggerFactory
import kotlin.math.roundToInt

/**
 * Latest blood oxygen saturation measurement for the day.
 */
object Spo2Widget : MetricWidget<Spo2Widget.Data>() {
    private val LOG = LoggerFactory.getLogger(Spo2Widget::class.java)

    /**
     * SpO2 only varies within a narrow band, so the gauge spans
     * [GAUGE_MIN]..100% rather than 0..100%.
     */
    private const val GAUGE_MIN = 80

    override val id = "spo2"
    override val label = R.string.menuitem_spo2
    override val icon = R.drawable.ic_spo2
    override val chartTab = "spo2"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsSpo2(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        var latestSpo2 = 0
        var latestTimestamp = 0L
        val day = mutableListOf<TimedValue>()

        try {
            scope.db { db ->
                for (dev in scope.devices) {
                    val provider = dev.deviceCoordinator.getSpo2SampleProvider(dev, db.daoSession) ?: continue
                    val samples: List<Spo2Sample> = provider.getAllSamples(
                        scope.query.timeFrom * 1000L,
                        scope.query.timeTo * 1000L
                    )
                    if (samples.isNotEmpty()) {
                        val latest = samples.last()
                        if (latest.spo2 > 0 && latest.timestamp > latestTimestamp) {
                            latestTimestamp = latest.timestamp
                            latestSpo2 = latest.spo2
                        }
                    }
                    if (config.needsHistory) {
                        samples.filter { it.spo2 > 0 }.mapTo(day) { TimedValue(it.timestamp / 1000L, it.spo2.toFloat()) }
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get spo2 samples", e)
        }

        if (!config.needsHistory) {
            return Data(latestSpo2)
        }
        return Data(
            latestSpo2,
            lowest = day.minOfOrNull { it.value }?.toInt() ?: 0,
            hourly = WidgetHistory.hourlyAverages(day, scope.dayStart),
            hoursStarted = scope.hoursStarted,
        )
    }

    override fun previewData() = Data(
        spo2 = 97,
        lowest = 92,
        hourly = floatArrayOf(
            95f, 94f, 95f, 94f, 95f, 96f, 96f, 97f, 97f, 98f, 97f, 97f,
            98f, 97f, 96f, 97f, Float.NaN, Float.NaN, Float.NaN, Float.NaN,
            Float.NaN, Float.NaN, Float.NaN, Float.NaN,
        ),
        hoursStarted = 16,
    )

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val bars = data.hourly?.let { hourly ->
            MiniChart.Bars(
                hourly,
                IntArray(hourly.size) { hour -> if (hourly[hour].isNaN()) 0 else colorFor(hourly[hour].roundToInt()) },
                GAUGE_MIN.toFloat(),
                100f,
                dataUntil = data.hoursStarted,
            )
        }
        if (data.spo2 <= 0) {
            return emptyModel(context, GaugeSpec.Progress(Color.GRAY, -1f), bars)
        }
        return TileModel(
            value = context.getString(R.string.battery_percentage_str, data.spo2.toString()),
            gauge = GaugeSpec.Progress(colorFor(data.spo2), (data.spo2 - GAUGE_MIN).coerceAtLeast(0) / (100f - GAUGE_MIN)),
            status = if (data.lowest > 0) {
                StatusLine.Text(
                    context.getString(R.string.widget_status_spo2_lowest_value, context.getString(R.string.battery_percentage_str, data.lowest.toString()))
                )
            } else {
                null
            },
            chart = bars,
            largeChart = if (config.isLarge) bars else null,
            axis = if (config.isLarge && bars != null) TileAxis.HOURS else emptyList(),
        )
    }

    /** Coloured by the usual clinical bands, as the blood pressure widget does. */
    private fun colorFor(spo2: Int): Int = when {
        spo2 >= 95 -> Color.rgb(76, 175, 80)   // green - normal
        spo2 >= 90 -> Color.rgb(255, 152, 0)   // orange - borderline
        else -> Color.rgb(244, 67, 54)         // red - low
    }

    /**
     * [lowest] and [hourly] are only loaded for the number style; [hoursStarted] is how many of the hours have begun.
     */
    class Data(
        val spo2: Int,
        val lowest: Int = 0,
        val hourly: FloatArray? = null,
        val hoursStarted: Int = WidgetHistory.HOURS_PER_DAY,
    )
}
