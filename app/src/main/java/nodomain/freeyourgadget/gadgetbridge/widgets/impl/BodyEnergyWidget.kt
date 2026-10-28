package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.PointF
import android.text.Spannable
import android.text.SpannableString
import android.text.TextUtils
import android.text.format.DateUtils
import android.text.style.RelativeSizeSpan
import androidx.core.content.ContextCompat
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.BodyEnergySample
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.LegendRow
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Body Energy: today's current level, or the gained/lost breakdown for a past day.
 */
object BodyEnergyWidget : MetricWidget<BodyEnergyWidget.Data>() {
    private val LOG = LoggerFactory.getLogger(BodyEnergyWidget::class.java)
    private const val SECONDS_PER_HOUR = 3600f

    override val id = "bodyenergy"
    override val label = R.string.body_energy
    override val icon = R.drawable.ic_bolt
    override val chartTab = "bodyenergy"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsBodyEnergy(device)

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val isToday = DateUtils.isToday(scope.query.timeTo * 1000L)
        val data = Data(isToday = isToday)

        if (isToday) {
            try {
                var latest: BodyEnergySample? = null
                for (dev in scope.devices) {
                    val sample = scope.db { db ->
                        dev.deviceCoordinator.getBodyEnergySampleProvider(dev, db.daoSession)?.latestSample
                    }
                    if (sample != null && (latest == null || sample.timestamp > latest.timestamp)) {
                        latest = sample
                    }
                }
                if (latest != null) {
                    data.value = latest.energy
                }
            } catch (e: Exception) {
                LOG.error("Could not get body energy for today", e)
            }
        }
        if (!isToday || config.needsHistory) {
            try {
                for (dev in scope.devices) {
                    val samples = scope.db { db ->
                        dev.deviceCoordinator.getBodyEnergySampleProvider(dev, db.daoSession)?.getAllSamples(
                            scope.query.timeFrom * 1000L,
                            scope.query.timeTo * 1000L
                        )
                    }
                    if (samples.isNullOrEmpty()) {
                        continue
                    }
                    data.lastOfDay = samples.last().energy
                    if (config.isLarge) {
                        data.samples = samples.map { TimedValue(it.timestamp / 1000L, it.energy.toFloat()) }
                        data.dayStart = scope.dayStart
                    }
                    if (samples.size > 1) {
                        var gained = 0
                        var lost = 0
                        for (i in 1 until samples.size) {
                            val s1 = samples[i - 1]
                            val s2 = samples[i]
                            if (s2.energy > s1.energy) {
                                gained += s2.energy - s1.energy
                            } else {
                                lost += s1.energy - s2.energy
                            }
                        }
                        data.gained = gained
                        data.lost = lost
                    }
                }
            } catch (e: Exception) {
                LOG.error("Could not calculate body energy change", e)
            }
        }

        return data
    }

    override fun previewData(): Data {
        val dayStart = LocalDate.of(2026, 1, 1).atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
        val levels = intArrayOf(32, 40, 49, 58, 66, 73, 79, 84, 81, 76, 72, 69, 65, 63, 58, 54)
        return Data(
            value = levels.last(),
            gained = 52,
            lost = 30,
            isToday = true,
            lastOfDay = levels.last(),
            samples = levels.mapIndexed { hour, level ->
                TimedValue(dayStart + hour * SECONDS_PER_HOUR.toLong(), level.toFloat())
            },
            dayStart = dayStart,
        )
    }

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val colorEnergy = ContextCompat.getColor(context, R.color.body_energy_level_color)

        if (config.isLarge) {
            return largeModel(context, config, data, colorEnergy)
        }

        if (config.needsHistory) {
            val level = if (data.isToday) data.value else data.lastOfDay
            if (level < 0) {
                return emptyModel(context, chart = MiniChart.Progress(colorEnergy, -1f))
            }
            return TileModel(
                value = context.getString(R.string.body_energy_percentage, level),
                gauge = GaugeSpec.Progress(colorEnergy, level / 100f),
                status = if (data.gained >= 0 && data.lost >= 0) {
                    StatusLine.Text(context.getString(R.string.widget_status_energy_charged_drained, data.gained, data.lost))
                } else {
                    null
                },
                chart = MiniChart.Progress(colorEnergy, level / 100f),
            )
        }

        if (data.isToday) {
            if (data.value < 0) {
                return emptyModel(context)
            }
            return TileModel(
                value = context.getString(R.string.body_energy_percentage, data.value),
                gauge = GaugeSpec.Progress(colorEnergy, data.value / 100f),
            )
        }

        if (data.gained < 0 || data.lost < 0) {
            return emptyModel(context)
        }

        val spanGain = SpannableString("↑${data.gained}")
        val spanLost = SpannableString("↓${data.lost}")
        spanGain.setSpan(RelativeSizeSpan(0.65f), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        spanLost.setSpan(RelativeSizeSpan(0.65f), 0, 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        val colors = intArrayOf(
            colorEnergy,
            ContextCompat.getColor(context, R.color.body_energy_lost_color),
        )
        val total = (data.gained + data.lost).toFloat()
        return TileModel(
            value = TextUtils.concat(spanGain, " ", spanLost),
            gauge = GaugeSpec.Zones(colors, if (total > 0) floatArrayOf(data.gained / total, data.lost / total) else floatArrayOf(0f, 0f)),
        )
    }

    private fun largeModel(context: Context, config: WidgetConfig, data: Data, colorEnergy: Int): TileModel {
        val level = if (data.isToday) data.value else data.lastOfDay
        if (level < 0) {
            return emptyModel(context, chart = MiniChart.Progress(colorEnergy, -1f))
        }
        val colorLost = ContextCompat.getColor(context, R.color.body_energy_lost_color)
        val samples = data.samples.orEmpty()
        val peak = samples.maxByOrNull { it.value }
        val value = context.getString(R.string.body_energy_percentage, level)
        val gauge = GaugeSpec.Progress(colorEnergy, level / 100f)

        if (config.isTall) {
            return TileModel(
                value = value,
                gauge = gauge,
                legend = listOfNotNull(
                    LegendRow(colorEnergy, context.getString(R.string.body_energy_gained), "+${data.gained.coerceAtLeast(0)}"),
                    LegendRow(colorLost, context.getString(R.string.body_energy_lost), "-${data.lost.coerceAtLeast(0)}"),
                    peak?.let {
                        LegendRow(colorEnergy, context.getString(R.string.widget_legend_energy_peak), context.getString(R.string.body_energy_percentage, it.value.toInt()))
                    },
                ),
            )
        }

        val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
        val peakTime = peak?.let { Instant.ofEpochSecond(it.seconds).atZone(ZoneId.systemDefault()).format(time) }
        val line = MiniChart.Line(
            points = samples.map { PointF((it.seconds - data.dayStart) / SECONDS_PER_HOUR, it.value) },
            xMin = 0f,
            xMax = WidgetHistory.HOURS_PER_DAY.toFloat(),
            yMin = 0f,
            yMax = 100f,
            color = colorEnergy,
            fill = true,
        )
        if (config.isLargeStyle) {
            return TileModel(
                value = value,
                gauge = gauge,
                miniStats = listOfNotNull(
                    Stat("+${data.gained.coerceAtLeast(0)}", context.getString(R.string.body_energy_gained)),
                    Stat("-${data.lost.coerceAtLeast(0)}", context.getString(R.string.body_energy_lost)),
                    peakTime?.let { Stat(it, context.getString(R.string.widget_legend_energy_peak)) },
                ),
                largeChart = line,
                axis = TileAxis.HOURS,
            )
        }
        return TileModel(
            value = value,
            gauge = gauge,
            status = peak?.takeIf { it.value > level }?.let {
                StatusLine.Chip(colorLost, context.getString(R.string.widget_status_decrease_since_peak, it.value.toInt() - level, peakTime))
            },
            chart = MiniChart.Progress(colorEnergy, level / 100f),
            largeChart = line,
            axis = TileAxis.HOURS,
        )
    }

    /**
     * [value] is today's latest level, [lastOfDay] the selected day's last level. [gained] and [lost] are loaded for
     * past days, and for today in every style but the gauge. [samples] are the day's levels, only for the tall and
     * wide and large styles.
     */
    data class Data(
        var value: Int = -1,
        var gained: Int = -1,
        var lost: Int = -1,
        val isToday: Boolean,
        var lastOfDay: Int = -1,
        var samples: List<TimedValue>? = null,
        var dayStart: Long = 0,
    )
}
