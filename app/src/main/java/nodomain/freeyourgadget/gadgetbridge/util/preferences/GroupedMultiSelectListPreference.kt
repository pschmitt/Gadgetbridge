package nodomain.freeyourgadget.gadgetbridge.util.preferences

import android.content.Context
import androidx.preference.MultiSelectListPreference

/**
 * A [MultiSelectListPreference] that displays a header before each group of entries.
 */
class GroupedMultiSelectListPreference(context: Context) : MultiSelectListPreference(context) {
    /**
     * The group titles, by the index of the first entry of each group.
     */
    var groupTitles: Map<Int, CharSequence> = emptyMap()
}
