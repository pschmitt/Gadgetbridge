package nodomain.freeyourgadget.gadgetbridge.activities.workouts.statistics

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import nodomain.freeyourgadget.gadgetbridge.R
import nodomain.freeyourgadget.gadgetbridge.databinding.ItemSportRowBinding
import nodomain.freeyourgadget.gadgetbridge.model.ActivityKind

class SportListAdapter(
    private val onClick: (SportCount) -> Unit,
) : ListAdapter<SportCount, SportListAdapter.ViewHolder>(DIFF) {

    class ViewHolder(val binding: ItemSportRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemSportRowBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val sport = getItem(position)
        holder.binding.sportRowIcon.setImageResource(ActivityKind.fromCode(sport.kindCode).icon)
        holder.binding.sportRowName.text = sport.name
        holder.binding.sportRowCount.text = holder.itemView.resources
            .getQuantityString(R.plurals.workout_list_activity_count, sport.count, sport.count)
        holder.binding.root.setOnClickListener { onClick(sport) }
    }

    private companion object {
        val DIFF = object : DiffUtil.ItemCallback<SportCount>() {
            override fun areItemsTheSame(old: SportCount, new: SportCount) = old.kindCode == new.kindCode
            override fun areContentsTheSame(old: SportCount, new: SportCount) = old == new
        }
    }
}
