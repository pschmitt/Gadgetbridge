package nodomain.freeyourgadget.gadgetbridge.widgets.impl

import android.content.Context
import android.graphics.PointF
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.activities.charts.VO2MaxRanges
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.DeviceSettingsSpec
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.ListEntry
import nodomain.freeyourgadget.gadgetbridge.activities.devicesettings.dsl.deviceSettings
import nodomain.freeyourgadget.gadgetbridge.devices.Vo2MaxSampleProvider
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice
import nodomain.freeyourgadget.gadgetbridge.model.ActivityUser
import nodomain.freeyourgadget.gadgetbridge.model.Vo2MaxSample
import nodomain.freeyourgadget.gadgetbridge.widgets.Band
import nodomain.freeyourgadget.gadgetbridge.widgets.GaugeSpec
import nodomain.freeyourgadget.gadgetbridge.widgets.MiniChart
import nodomain.freeyourgadget.gadgetbridge.widgets.Stat
import nodomain.freeyourgadget.gadgetbridge.widgets.StatusLine
import nodomain.freeyourgadget.gadgetbridge.widgets.TileAxis
import nodomain.freeyourgadget.gadgetbridge.widgets.TileModel
import nodomain.freeyourgadget.gadgetbridge.widgets.TimedValue
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetDataScope
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetConfig
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetHistory
import nodomain.freeyourgadget.gadgetbridge.widgets.WidgetVariant
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

/**
 * VO2 Max of the sport in the widget settings.
 */
object Vo2MaxWidget : MetricWidget<Vo2MaxWidget.Data>() {
    const val KEY_SPORT = "sport"
    const val SPORT_ANY = "any"
    const val SPORT_RUNNING = "running"
    const val SPORT_CYCLING = "cycling"

    private val LOG = LoggerFactory.getLogger(Vo2MaxWidget::class.java)
    private const val WEEKS = 12
    private const val BAND_ALPHA = 46

    private val ZONE_LABELS = intArrayOf(
        R.string.sleep_score_poor,
        R.string.sleep_score_fair,
        R.string.sleep_score_good,
        R.string.sleep_score_excellent,
        R.string.widget_status_vo2_max_superior,
    )

    // Should match the percentiles in VO2MaxRanges
    @JvmField
    val SEGMENTS = floatArrayOf(0.40f, 0.20f, 0.20f, 0.15f, 0.05f)

    override val id = "vo2max"
    override val label = R.string.menuitem_vo2_max
    override val icon = R.drawable.ic_activity_running
    override val chartTab = "vo2max"

    override fun isSupportedBy(device: GBDevice): Boolean =
        device.deviceCoordinator.supportsVO2Max(device) || device.deviceCoordinator.supportsVO2MultiSport(device)

    override fun settings(context: Context, variant: WidgetVariant): DeviceSettingsSpec = deviceSettings {
        list(
            key = KEY_SPORT,
            title = R.string.xiaomi_scooter_ride_mode_sport,
            icon = R.drawable.ic_activity_unknown_small,
            entries = listOf(
                ListEntry.Res(SPORT_ANY, R.string.vo2max_any_sport),
                ListEntry.Res(SPORT_RUNNING, R.string.vo2max_running),
                ListEntry.Res(SPORT_CYCLING, R.string.vo2max_cycling),
            ),
            defaultValue = SPORT_ANY,
        )
    }

    override fun labelFor(variant: WidgetVariant, config: WidgetConfig): Int = when (sport(config)) {
        SPORT_RUNNING -> R.string.vo2max_running
        SPORT_CYCLING -> R.string.vo2max_cycling
        else -> label
    }

    private fun sport(config: WidgetConfig): String = config.prefs.getString(KEY_SPORT, SPORT_ANY)

    private fun typeOf(config: WidgetConfig): Vo2MaxSample.Type = when (sport(config)) {
        SPORT_RUNNING -> Vo2MaxSample.Type.RUNNING
        SPORT_CYCLING -> Vo2MaxSample.Type.CYCLING
        else -> Vo2MaxSample.Type.ANY
    }

    override suspend fun loadData(scope: WidgetDataScope, config: WidgetConfig): Data {
        val vo2MaxType = typeOf(config)
        var value = -1f
        try {
            scope.db { db ->
                var latest: Vo2MaxSample? = null
                for (dev in scope.devices) {
                    @Suppress("UNCHECKED_CAST")
                    val provider = dev.deviceCoordinator.getVo2MaxSampleProvider(dev, db.daoSession)
                            as Vo2MaxSampleProvider<Vo2MaxSample>
                    val sample = provider.getLatestSample(vo2MaxType, scope.query.timeTo * 1000L)
                    if (sample != null && (latest == null || sample.timestamp > latest.timestamp)) {
                        latest = sample
                    }
                }
                if (latest != null) {
                    value = latest.value
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get vo2max for today", e)
        }

        val activityUser = ActivityUser()
        return Data(
            value,
            age = activityUser.getAgeAt(scope.day),
            gender = activityUser.gender,
            weeks = if (config.needsHistory) weeks(scope, vo2MaxType) else null,
            day = scope.day,
        )
    }

    override fun previewData() = Data(
        value = 46.2f,
        age = 35,
        gender = ActivityUser().gender,
        weeks = floatArrayOf(44.8f, 44.8f, 45.1f, 45.0f, 45.3f, 45.3f, 45.6f, 45.5f, 45.8f, 46.0f, 46.0f, 46.2f),
    )

    override fun model(context: Context, config: WidgetConfig, data: Data): TileModel {
        val colors = colors(context)
        if (data.value == -1f) {
            return emptyModel(context, GaugeSpec.Zones(colors, SEGMENTS))
        }
        val percentile = VO2MaxRanges.calculateVO2MaxPercentile(data.value, data.age, data.gender)
        val ranges = VO2MaxRanges.getVO2MaxRanges(data.age, data.gender)
        val zone = zoneOf(data.value, ranges)
        val line = data.weeks?.let { weekLine(context, it, ranges, colors) }
        val known = data.weeks?.filter { !it.isNaN() }.orEmpty()
        val change = if (known.size > 1) String.format(Locale.getDefault(), "%+.1f", known.last() - known.first()) else null
        return TileModel(
            value = String.format(Locale.getDefault(), "%.1f", data.value),
            gauge = GaugeSpec.Zones(colors, SEGMENTS, percentile),
            status = StatusLine.Chip(colors[zone], context.getString(ZONE_LABELS[zone])),
            chart = line,
            largeChart = if (config.isLarge) line else null,
            axis = if (config.isLarge && line != null) TileAxis.months(data.day, WEEKS) else emptyList(),
            trailing = if (config.isTall && change != null) change else null,
            miniStats = if (config.isLargeStyle) {
                listOfNotNull(
                    change?.let { Stat(it, context.getString(R.string.widget_stat_twelve_weeks)) },
                    nextZone(context, zone, ranges),
                )
            } else {
                emptyList()
            },
        )
    }

    /**
     * The value where the zone above [zone] starts, or null in the top zone.
     */
    private fun nextZone(context: Context, zone: Int, ranges: FloatArray): Stat? {
        if (zone >= ZONE_LABELS.size - 1) return null
        // ranges go from superior down to poor
        val threshold = ranges[ZONE_LABELS.size - 2 - zone]
        return Stat(
            String.format(Locale.getDefault(), "%.1f", threshold),
            context.getString(R.string.widget_stat_next_vo2max_zone, context.getString(ZONE_LABELS[zone + 1])),
        )
    }

    private suspend fun weeks(scope: WidgetDataScope, vo2MaxType: Vo2MaxSample.Type): FloatArray {
        val from = scope.dayStartMinusDays(WEEKS * 7 - 1) * 1000L
        val samples = try {
            scope.db { db ->
                scope.devices.flatMap { dev ->
                    @Suppress("UNCHECKED_CAST")
                    val provider = dev.deviceCoordinator.getVo2MaxSampleProvider(dev, db.daoSession)
                            as Vo2MaxSampleProvider<Vo2MaxSample>
                    provider.getAllSamples(from, scope.query.timeTo * 1000L)
                        .filter { (vo2MaxType == Vo2MaxSample.Type.ANY || it.type == vo2MaxType) && it.value > 0 }
                        .map { TimedValue(it.timestamp / 1000L, it.value) }
                }
            }
        } catch (e: Exception) {
            LOG.error("Could not get vo2max history", e)
            emptyList()
        }
        return WidgetHistory.weeklyLatest(samples, scope.day, WEEKS, scope.zone)
    }

    /**
     * The weekly values over the zone bands, with the y-range at the data's min and max, one either side.
     */
    private fun weekLine(context: Context, weeks: FloatArray, ranges: FloatArray, colors: IntArray): MiniChart.Line {
        val known = weeks.filter { !it.isNaN() }
        val yMin = floor((known.minOrNull() ?: 0f) - 1)
        val yMax = ceil((known.maxOrNull() ?: 0f) + 1)
        // ranges go from superior down to poor, colors from poor up to superior
        val lowerBounds = floatArrayOf(0f, ranges[3], ranges[2], ranges[1], ranges[0])
        val bands = colors.indices.map { zone ->
            val upper = if (zone + 1 < lowerBounds.size) lowerBounds[zone + 1] else Float.MAX_VALUE
            Band(lowerBounds[zone], upper, ColorUtils.setAlphaComponent(colors[zone], BAND_ALPHA))
        }
        return MiniChart.Line(
            // skip weeks without a value, so sparse measurements still form a line
            points = weeks.withIndex().filter { !it.value.isNaN() }.map { PointF(it.index.toFloat(), it.value) },
            xMin = 0f,
            xMax = (weeks.size - 1).toFloat(),
            yMin = yMin,
            yMax = yMax,
            color = GBApplication.getTextColor(context),
            bands = bands,
        )
    }

    /**
     * [weeks] holds the latest values of the twelve weeks ending on [day], oldest first, for every style but the
     * gauge.
     */
    class Data(
        val value: Float,
        val age: Int,
        val gender: Int,
        val weeks: FloatArray? = null,
        val day: LocalDate = LocalDate.now(),
    )

    /**
     * Index into [colors] of the zone [value] is in: 0 poor up to 4 superior.
     */
    fun zoneOf(value: Float, ranges: FloatArray): Int = when {
        value >= ranges[0] -> 4
        value >= ranges[1] -> 3
        value >= ranges[2] -> 2
        value >= ranges[3] -> 1
        else -> 0
    }

    @JvmStatic
    fun colors(context: Context) = intArrayOf(
        ContextCompat.getColor(context, R.color.vo2max_value_poor_color),
        ContextCompat.getColor(context, R.color.vo2max_value_fair_color),
        ContextCompat.getColor(context, R.color.vo2max_value_good_color),
        ContextCompat.getColor(context, R.color.vo2max_value_excellent_color),
        ContextCompat.getColor(context, R.color.vo2max_value_superior_color),
    )
}
