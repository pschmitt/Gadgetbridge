package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

enum class SeriesType { BAR, LINE }

/**
 * A metric and how it is drawn. See [SportMetric.lineOnly].
 */
data class ChartSet(val metric: SportMetric, val type: SeriesType) {
    fun withMetric(newMetric: SportMetric) = ChartSet(newMetric, if (newMetric.lineOnly) SeriesType.LINE else type)

    companion object {
        /**
         * Lines for averages and maximums, bars for totals.
         */
        fun of(metric: SportMetric) = ChartSet(metric, if (metric.lineOnly) SeriesType.LINE else SeriesType.BAR)
    }
}

/**
 * Up to two data sets plotted on a sport's chart.
 */
data class ChartConfig(val first: ChartSet, val second: ChartSet? = null) {
    fun serialize(): String = listOfNotNull(first, second).joinToString(SET_SEPARATOR) {
        it.metric.name + TYPE_SEPARATOR + it.type.name
    }

    /**
     * Drops sets whose metric isn't in [available].
     */
    fun restrictedTo(available: List<SportMetric>): ChartConfig {
        fun valid(set: ChartSet?) = set?.takeIf { it.metric in available }?.let { it.withMetric(it.metric) }
        val first = valid(first) ?: default(available).first
        return ChartConfig(first, valid(second)?.takeIf { it.metric != first.metric })
    }

    companion object {
        private const val SET_SEPARATOR = ";"
        private const val TYPE_SEPARATOR = ":"

        fun default(available: List<SportMetric>) = ChartConfig(ChartSet.of(available.first()))

        /**
         * Null if [value] can't be parsed.
         */
        fun parse(value: String?): ChartConfig? {
            val sets = value?.split(SET_SEPARATOR)?.map { part ->
                val (metric, type) = part.split(TYPE_SEPARATOR).takeIf { it.size == 2 } ?: return null
                ChartSet(
                    SportMetric.entries.firstOrNull { it.name == metric } ?: return null,
                    SeriesType.entries.firstOrNull { it.name == type } ?: return null,
                )
            } ?: return null
            return when (sets.size) {
                1 -> ChartConfig(sets[0])
                2 -> ChartConfig(sets[0], sets[1])
                else -> null
            }
        }
    }
}
