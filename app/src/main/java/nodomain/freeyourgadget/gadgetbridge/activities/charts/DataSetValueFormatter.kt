package nodomain.freeyourgadget.gadgetbridge.activities.charts

import com.github.mikephil.charting.formatter.IValueFormatter

/**
 * An [IValueFormatter] that Java code can extend without implementing
 * [IValueFormatter.getStackedFormattedValue].
 */
abstract class DataSetValueFormatter : IValueFormatter
