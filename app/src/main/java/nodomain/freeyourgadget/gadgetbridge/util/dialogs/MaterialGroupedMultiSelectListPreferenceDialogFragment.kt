package nodomain.freeyourgadget.gadgetbridge.util.dialogs

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.util.preferences.GroupedMultiSelectListPreference

/**
 * A multi-select dialog for a [GroupedMultiSelectListPreference], with a header before each group.
 */
class MaterialGroupedMultiSelectListPreferenceDialogFragment : MaterialMultiSelectListPreferenceDialogFragment() {
    private sealed interface Row {
        data class Header(val title: CharSequence) : Row
        data class Item(val index: Int) : Row
    }

    override fun onPrepareDialogBuilder(builder: MaterialAlertDialogBuilder) {
        val groupTitles = (preference as GroupedMultiSelectListPreference).groupTitles
        val rows = buildList {
            for (index in mEntries.indices) {
                groupTitles[index]?.let { add(Row.Header(it)) }
                add(Row.Item(index))
            }
        }

        val context = builder.context
        var itemLayout = 0
        // Use the same item layout as the AlertDialog multi-choice list.
        context.withStyledAttributes(
            null,
            androidx.appcompat.R.styleable.AlertDialog,
            androidx.appcompat.R.attr.alertDialogStyle,
            0,
        ) {
            itemLayout = getResourceId(androidx.appcompat.R.styleable.AlertDialog_multiChoiceItemLayout, 0)
        }

        val listView = ListView(context).apply {
            choiceMode = ListView.CHOICE_MODE_MULTIPLE
            divider = null
            adapter = RowAdapter(LayoutInflater.from(context), rows, itemLayout)
        }
        rows.forEachIndexed { position, row ->
            if (row is Row.Item) {
                listView.setItemChecked(position, mEntryValues[row.index].toString() in mNewValues)
            }
        }
        listView.setOnItemClickListener { _, _, position, _ ->
            val row = rows[position] as Row.Item
            val value = mEntryValues[row.index].toString()
            val changed = if (listView.isItemChecked(position)) mNewValues.add(value) else mNewValues.remove(value)
            mPreferenceChanged = mPreferenceChanged || changed
        }

        builder.setView(listView)
    }

    private inner class RowAdapter(
        private val inflater: LayoutInflater,
        private val rows: List<Row>,
        private val itemLayout: Int,
    ) : BaseAdapter() {
        override fun getCount() = rows.size

        override fun getItem(position: Int) = rows[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getViewTypeCount() = 2

        override fun getItemViewType(position: Int) = if (rows[position] is Row.Header) 0 else 1

        override fun areAllItemsEnabled() = false

        override fun isEnabled(position: Int) = rows[position] is Row.Item

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            when (val row = rows[position]) {
                is Row.Header -> {
                    val view = convertView ?: inflater.inflate(R.layout.item_dialog_list_header, parent, false)
                    (view as TextView).text = row.title
                    view
                }

                is Row.Item -> {
                    val view = convertView ?: inflater.inflate(itemLayout, parent, false)
                    view.findViewById<TextView>(android.R.id.text1).text = mEntries[row.index]
                    view
                }
            }
    }
}
