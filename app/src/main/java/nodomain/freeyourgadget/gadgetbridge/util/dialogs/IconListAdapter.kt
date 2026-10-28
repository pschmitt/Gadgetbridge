package nodomain.freeyourgadget.gadgetbridge.util.dialogs

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import com.google.android.material.color.MaterialColors
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * A list adapter for an alert dialog, where each row has an icon before the text.
 */
class IconListAdapter(
    context: Context,
    private val entries: List<Entry>,
) : ArrayAdapter<IconListAdapter.Entry>(context, R.layout.item_icon_list_dialog, entries) {
    data class Entry(@param:DrawableRes val icon: Int, val title: String)

    private val iconTint = ColorStateList.valueOf(MaterialColors.getColor(context, R.attr.textColorSecondary, 0))
    private val iconSize = context.resources.getDimensionPixelSize(R.dimen.dialog_list_icon_size)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent) as TextView
        val entry = entries[position]
        view.text = entry.title
        // Some icons have a large intrinsic size, which makes the row taller
        val icon = AppCompatResources.getDrawable(context, entry.icon)?.mutate()
        icon?.setTintList(iconTint)
        icon?.setBounds(0, 0, iconSize, iconSize)
        view.setCompoundDrawablesRelative(icon, null, null, null)
        return view
    }
}
