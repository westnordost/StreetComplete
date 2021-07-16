package de.westnordost.streetcomplete.settings.questselection

import android.content.SharedPreferences
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.ItemTouchHelper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isInvisible

import java.util.Collections
import java.util.Locale
import java.util.concurrent.FutureTask

import javax.inject.Inject

import de.westnordost.countryboundaries.CountryBoundaries
import de.westnordost.streetcomplete.Prefs
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.view.ListAdapter


import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_DRAG
import androidx.recyclerview.widget.ItemTouchHelper.ACTION_STATE_IDLE
import androidx.recyclerview.widget.ItemTouchHelper.DOWN
import androidx.recyclerview.widget.ItemTouchHelper.UP
import de.westnordost.streetcomplete.data.quest.QuestType
import de.westnordost.streetcomplete.data.osm.osmquests.OsmElementQuestType
import de.westnordost.streetcomplete.data.quest.AllCountries
import de.westnordost.streetcomplete.data.quest.AllCountriesExcept
import de.westnordost.streetcomplete.data.quest.NoCountriesExcept
import de.westnordost.streetcomplete.databinding.RowQuestSelectionBinding
import de.westnordost.streetcomplete.ktx.containsAny
import de.westnordost.streetcomplete.settings.genericQuestTitle

/** Adapter for the list that in which the user can enable and disable quests as well as re-order
 *  them */
class QuestSelectionAdapter @Inject constructor(
    countryBoundaries: FutureTask<CountryBoundaries>,
    prefs: SharedPreferences
) : ListAdapter<QuestVisibility>() {
    private val currentCountryCodes: List<String>

    private val itemTouchHelper by lazy { ItemTouchHelper(TouchHelperCallback()) }

    interface Listener {
        fun onReorderedQuests(before: QuestType<*>, after: QuestType<*>)
        fun onChangedQuestVisibility(questType: QuestType<*>, visible: Boolean)
    }
    var listener: Listener? = null

    init {
        val lat = Double.fromBits(prefs.getLong(Prefs.MAP_LATITUDE, 0.0.toBits()))
        val lng = Double.fromBits(prefs.getLong(Prefs.MAP_LONGITUDE, 0.0.toBits()))
        currentCountryCodes = countryBoundaries.get().getIds(lng, lat)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        itemTouchHelper.attachToRecyclerView(recyclerView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder<QuestVisibility> {
        return QuestVisibilityViewHolder(RowQuestSelectionBinding
            .inflate(LayoutInflater.from(parent.context), parent, false))
    }

    private inner class TouchHelperCallback : ItemTouchHelper.Callback() {
        private var draggedFrom = -1
        private var draggedTo = -1

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val qv = (viewHolder as QuestVisibilityViewHolder).item
            return if (!qv.isInteractionEnabled) 0
            else makeFlag(ACTION_STATE_IDLE, UP or DOWN) or makeFlag(ACTION_STATE_DRAG, UP or DOWN)
        }

        override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val from = viewHolder.adapterPosition
            val to = target.adapterPosition
            Collections.swap(list, from, to)
            notifyItemMoved(from, to)
            return true
        }

        override fun canDropOver(recyclerView: RecyclerView, current: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean {
            val qv = (target as QuestVisibilityViewHolder).item
            return qv.isInteractionEnabled
        }

        override fun onMoved(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, fromPos: Int, target: RecyclerView.ViewHolder, toPos: Int, x: Int, y: Int) {
            super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y)
            if (draggedFrom == -1) draggedFrom = fromPos
            draggedTo = toPos
        }

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ACTION_STATE_IDLE && draggedTo != draggedFrom) {
                var pos = draggedTo
                if (draggedTo == 0) pos++

                val before = list[pos - 1].questType
                val after = list[pos].questType

                listener?.onReorderedQuests(before, after)

                draggedFrom = -1
                draggedTo = draggedFrom
            }
        }

        override fun isItemViewSwipeEnabled() = false

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
    }

    private inner class QuestVisibilityViewHolder(binding : RowQuestSelectionBinding) :
        ListAdapter.ViewHolder<QuestVisibility>(binding), CompoundButton.OnCheckedChangeListener {

        private val dragHandle: ImageView = binding.dragHandle
        private val questIcon: ImageView = binding.questIcon
        private val questTitle: TextView = binding.questTitle
        private val visibilityCheckBox: CheckBox = binding.visibilityCheckBox
        private val countryDisabledText: TextView = binding.countryDisabledText
        lateinit var item: QuestVisibility

        private val isEnabledInCurrentCountry: Boolean
            get() {
                (item.questType as? OsmElementQuestType<*>)?.let { questType ->
                    return when(val countries = questType.enabledInCountries) {
                        is AllCountries -> true
                        is AllCountriesExcept -> !countries.exceptions.containsAny(currentCountryCodes)
                        is NoCountriesExcept -> countries.exceptions.containsAny(currentCountryCodes)
                    }
                }
                return true
            }

        override fun onBind(with: QuestVisibility) {
            this.item = with
            val colorResId = if (item.isInteractionEnabled) R.color.background else R.color.greyed_out
            itemView.setBackgroundResource(colorResId)
            questIcon.setImageResource(item.questType.icon)
            questTitle.text = genericQuestTitle(questTitle, item.questType)
            visibilityCheckBox.setOnCheckedChangeListener(null)
            visibilityCheckBox.isChecked = item.visible
            visibilityCheckBox.isEnabled = item.isInteractionEnabled
            visibilityCheckBox.setOnCheckedChangeListener(this)

            dragHandle.isInvisible = !item.isInteractionEnabled
            dragHandle.setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> itemTouchHelper.startDrag(this)
                    MotionEvent.ACTION_UP -> v.performClick()
                }
                true
            }

            countryDisabledText.isGone = isEnabledInCurrentCountry
            if (!isEnabledInCurrentCountry) {
                val cc = if (currentCountryCodes.isEmpty()) "Atlantis" else currentCountryCodes[0]
                countryDisabledText.text =  countryDisabledText.resources.getString(
                    R.string.questList_disabled_in_country, Locale("", cc).displayCountry
                )
            }

            updateSelectionStatus()
        }

        private fun updateSelectionStatus() {
            if (!item.visible) {
                questIcon.setColorFilter(ContextCompat.getColor(itemView.context, R.color.greyed_out))
            } else {
                questIcon.clearColorFilter()
            }
            questTitle.isEnabled = item.visible
        }

        override fun onCheckedChanged(compoundButton: CompoundButton, b: Boolean) {
            item.visible = b
            updateSelectionStatus()
            listener?.onChangedQuestVisibility(item.questType, item.visible)
            if (b && item.questType.defaultDisabledMessage > 0) {
                AlertDialog.Builder(compoundButton.context)
                    .setTitle(R.string.enable_quest_confirmation_title)
                    .setMessage(item.questType.defaultDisabledMessage)
                    .setPositiveButton(android.R.string.yes, null)
                    .setNegativeButton(android.R.string.no) { _, _ ->
                        compoundButton.isChecked = false
                    }
                    .show()
            }
        }
    }
}
