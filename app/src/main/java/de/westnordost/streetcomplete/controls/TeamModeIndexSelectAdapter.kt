package de.westnordost.streetcomplete.controls

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.CellTeamModeColorCircleSelectBinding
import java.util.concurrent.CopyOnWriteArrayList

class TeamModeIndexSelectAdapter : RecyclerView.Adapter<TeamModeIndexSelectAdapter.ViewHolder>() {
    var count: Int = 0
        set(value) {
            deselect()
            field = value
            notifyDataSetChanged()
        }

    private var selectedIndex: Int? = null
        set(index) {
            val oldIndex = field
            field = index

            oldIndex?.let { notifyItemChanged(it) }
            index?.let { notifyItemChanged(it) }
            for (listener in listeners) {
                listener.onSelectedIndexChanged(index)
            }
        }

    val listeners: MutableList<OnSelectedIndexChangedListener> = CopyOnWriteArrayList()

    interface OnSelectedIndexChangedListener {
        fun onSelectedIndexChanged(index: Int?)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CellTeamModeColorCircleSelectBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        val holder = ViewHolder(binding)
        holder.onClickListener = ::toggle
        return holder
    }

    private fun toggle(index: Int) {
        if (index < 0 || index >= count)
            throw ArrayIndexOutOfBoundsException(index)

        selectedIndex = if (index == selectedIndex) null else index
    }

    private fun deselect() {
        selectedIndex = null
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(position)
        holder.itemView.isSelected = selectedIndex == position
    }

    override fun getItemCount() = count

    class ViewHolder(val binding: CellTeamModeColorCircleSelectBinding) : RecyclerView.ViewHolder(binding.root) {
        var onClickListener: ((index: Int) -> Unit)? = null
            set(value) {
                field = value
                if (value == null) itemView.setOnClickListener(null)
                else itemView.setOnClickListener {
                    val index = adapterPosition
                    if (index != RecyclerView.NO_POSITION) value.invoke(index)
                }
            }

        fun bind(index: Int) {
            binding.teamModeColorCircle.setIndexInTeam(index)
        }
    }
}
