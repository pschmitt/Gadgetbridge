package nodomain.freeyourgadget.gadgetbridge.util.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.RadioButton
import android.widget.TextView
import androidx.core.view.isVisible
import nodomain.freeyourgadget.gadgetbridge.R

class SubtitleListAdapter(
    context: Context,
    private val rows: List<Pair<CharSequence, CharSequence?>>,
    checkedPosition: Int = -1
) : ArrayAdapter<Pair<CharSequence, CharSequence?>>(context, R.layout.list_item_subtitle_picker, rows) {
    var checkedPosition: Int = checkedPosition
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView
            ?: LayoutInflater.from(context).inflate(R.layout.list_item_subtitle_picker, parent, false)
        val (title, subtitle) = rows[position]
        view.findViewById<TextView>(R.id.subtitle_picker_item_title).text = title
        view.findViewById<TextView>(R.id.subtitle_picker_item_subtitle).apply {
            text = subtitle
            isVisible = !subtitle.isNullOrEmpty()
        }
        view.findViewById<RadioButton>(R.id.subtitle_picker_item_radio).isChecked = position == checkedPosition
        return view
    }
}
