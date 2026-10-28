package nodomain.freeyourgadget.gadgetbridge.activities.charts.weight

import nodomain.freeyourgadget.gadgetbridge.activities.charts.spec.AxisSide
import org.junit.Assert
import org.junit.Test

class WeightChartDataTest {
    // Synthetic readings; nothing here is a real person's measurement.
    private val seconds = longArrayOf(1_000_000L, 1_086_400L, 1_172_800L)
    private val weights = doubleArrayOf(80.0, 79.5, 79.0)

    private fun spec(vararg lines: WeightChartData.PercentLine) = WeightChartData.spec(
        seconds.first(), seconds.last(), seconds, weights, 75.0, "Weight", 1, 2, lines.toList(), "%"
    )

    @Test
    fun `without percent lines there is only the weight and no end axis`() {
        val spec = spec()

        Assert.assertEquals(listOf("weight"), spec.series.map { it.key })
        Assert.assertNull(spec.endYAxis)
    }

    @Test
    fun `percent lines go on the end axis and skip readings without an estimate`() {
        val spec = spec(
            WeightChartData.PercentLine("body_fat", "Body fat", 3, doubleArrayOf(25.0, Double.NaN, 24.0)),
            WeightChartData.PercentLine("body_water", "Body water", 4, doubleArrayOf(55.0, Double.NaN, 56.0)),
        )

        Assert.assertEquals(listOf("weight", "body_fat", "body_water"), spec.series.map { it.key })
        Assert.assertEquals(AxisSide.START, spec.series[0].axis)
        val fat = spec.series[1]
        Assert.assertEquals(AxisSide.END, fat.axis)
        Assert.assertEquals(listOf(seconds[0].toDouble(), seconds[2].toDouble()), fat.points.map { it.x })
        Assert.assertEquals(listOf(25.0, 24.0), fat.points.map { it.y })
        // the weight axis is not stretched to fit the percentages
        Assert.assertEquals(74.0, spec.yAxis.minimum!!, 0.001)
        Assert.assertEquals(81.0, spec.yAxis.maximum!!, 0.001)

        val end = spec.endYAxis
        Assert.assertNotNull(end)
        Assert.assertEquals(23.0, end!!.minimum!!, 0.001)
        Assert.assertEquals(57.0, end.maximum!!, 0.001)
        Assert.assertEquals("%", end.unit)
    }

    @Test
    fun `a line without any estimate is left out, and so is the end axis`() {
        val spec = spec(WeightChartData.PercentLine("body_fat", "Body fat", 3, DoubleArray(3) { Double.NaN }))

        Assert.assertEquals(listOf("weight"), spec.series.map { it.key })
        Assert.assertNull(spec.endYAxis)
    }

    @Test
    fun `the end axis stays within 0 and 100 percent`() {
        val spec = spec(WeightChartData.PercentLine("body_water", "Body water", 4, doubleArrayOf(0.5, 99.5, Double.NaN)))

        Assert.assertEquals(0.0, spec.endYAxis!!.minimum!!, 0.001)
        Assert.assertEquals(100.0, spec.endYAxis!!.maximum!!, 0.001)
    }

    @Test
    fun `no weight means no chart, whatever the percentages`() {
        val spec = WeightChartData.spec(
            seconds.first(), seconds.last(), seconds, DoubleArray(3), 75.0, "Weight", 1, 2,
            listOf(WeightChartData.PercentLine("body_fat", "Body fat", 3, doubleArrayOf(25.0, 25.0, 25.0))), "%"
        )

        Assert.assertTrue(spec.isEmpty)
    }
}
