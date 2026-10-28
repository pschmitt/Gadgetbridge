package nodomain.freeyourgadget.gadgetbridge.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemContactPickerBinding

class ContactPickerAdapter(
    private val selected: Set<Entry>,
    private val onToggle: (Entry) -> Unit
) : RecyclerView.Adapter<ContactPickerAdapter.ViewHolder>() {

    data class Entry(
        val id: Long,
        val name: String,
        val number: String,
        val typeLabel: String,
        val alreadyAdded: Boolean,
    )

    private var entries: List<Entry> = emptyList()

    @SuppressLint("NotifyDataSetChanged")
    fun setEntries(entries: List<Entry>) {
        this.entries = entries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemContactPickerBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = entries[position]
        with(holder.binding) {
            contactPickerItemName.text = entry.name
            contactPickerItemNumber.text = entry.number
            contactPickerItemType.text = entry.typeLabel
            contactPickerItemCheckbox.isChecked = entry.alreadyAdded || entry in selected
            contactPickerItemCheckbox.isEnabled = !entry.alreadyAdded
            contactPickerItem.isEnabled = !entry.alreadyAdded
            contactPickerItem.alpha = if (entry.alreadyAdded) 0.6f else 1f
            contactPickerItem.setOnClickListener {
                onToggle(entry)
                val adapterPosition = holder.bindingAdapterPosition
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    notifyItemChanged(adapterPosition)
                }
            }
        }
    }

    override fun getItemCount(): Int = entries.size

    class ViewHolder(val binding: ItemContactPickerBinding) : RecyclerView.ViewHolder(binding.root)
}
