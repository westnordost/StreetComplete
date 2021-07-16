package de.westnordost.streetcomplete.quests.postbox_collection_times

import android.content.Context
import android.text.format.DateFormat
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isInvisible

import java.util.Locale

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.meta.CountryInfo
import de.westnordost.streetcomplete.quests.opening_hours.model.Weekdays
import de.westnordost.streetcomplete.quests.opening_hours.WeekdaysPickerDialog
import de.westnordost.streetcomplete.quests.opening_hours.adapter.OpeningWeekdaysRow
import de.westnordost.streetcomplete.quests.opening_hours.adapter.OpeningHoursRow
import de.westnordost.streetcomplete.quests.opening_hours.model.TimeRange
import de.westnordost.streetcomplete.quests.opening_hours.parser.toOpeningHoursRules
import de.westnordost.streetcomplete.view.dialogs.TimePickerDialog

class CollectionTimesAdapter(
    private val context: Context,
    private val countryInfo: CountryInfo
) : RecyclerView.Adapter<CollectionTimesAdapter.ViewHolder>() {

    var collectionTimesRows: MutableList<OpeningHoursRow> = mutableListOf()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var isEnabled = true
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun createCollectionTimes() = collectionTimesRows.toOpeningHoursRules()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return ViewHolder(inflater.inflate(R.layout.quest_times_weekday_row, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val times = collectionTimesRows[position]
        val previousTimes = if (position > 0) collectionTimesRows[position - 1] as? OpeningWeekdaysRow else null
        holder.update(times as OpeningWeekdaysRow, previousTimes, isEnabled)
    }

    override fun getItemCount() = collectionTimesRows.size

    /* ------------------------------------------------------------------------------------------ */

    private fun remove(position: Int) {
        if (!isEnabled) return

        collectionTimesRows.removeAt(position)
        notifyItemRemoved(position)
        // if not last weekday removed -> element after this one may need to be updated
        // because it may need to show the weekdays now
        if (position < collectionTimesRows.size) notifyItemChanged(position)
    }

    fun addNewWeekdays() {
        val isFirst = collectionTimesRows.isEmpty()
        openSetWeekdaysDialog(getWeekdaysSuggestion(isFirst)) { weekdays ->
            openSetTimeDialog(12 * 60) { minutes ->
                add(weekdays, minutes) }
        }
    }

    fun addNewHours() {
        val rowAbove = if (collectionTimesRows.size > 0) collectionTimesRows[collectionTimesRows.size - 1] else null
        if (rowAbove !is OpeningWeekdaysRow) return
        openSetTimeDialog(12 * 60) { minutes ->
            add(rowAbove.weekdays, minutes)
        }
    }

    private fun add(weekdays: Weekdays, minutes: Int) {
        val insertIndex = itemCount
        val timeRange = TimeRange(minutes)
        collectionTimesRows.add(OpeningWeekdaysRow(weekdays, timeRange))
        notifyItemInserted(insertIndex)
    }

    /* ------------------------------------ weekdays select --------------------------------------*/

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val weekdaysLabel: TextView = itemView.findViewById(R.id.weekdaysLabel)
        private val hoursLabel: TextView = itemView.findViewById(R.id.hoursLabel)
        private val deleteButton: View = itemView.findViewById(R.id.deleteButton)

        init {
            deleteButton.setOnClickListener {
                val index = adapterPosition
                if (index != RecyclerView.NO_POSITION) remove(adapterPosition)
            }
        }

        fun update(times: OpeningWeekdaysRow, previousTimes: OpeningWeekdaysRow?, isEnabled: Boolean) {
            if (previousTimes != null && times.weekdays == previousTimes.weekdays) {
                weekdaysLabel.text = ""
            } else {
                weekdaysLabel.text = times.weekdays.toLocalizedString(context.resources)
            }

            weekdaysLabel.setOnClickListener {
                openSetWeekdaysDialog(times.weekdays) { weekdays ->
                    times.weekdays = weekdays
                    notifyItemChanged(adapterPosition)
                }
            }
            hoursLabel.text = times.timeRange.toStringUsing(Locale.getDefault(), "–")
            hoursLabel.setOnClickListener {
                openSetTimeDialog(times.timeRange.start) { minutes ->
                    times.timeRange = TimeRange(minutes)
                    notifyItemChanged(adapterPosition)
                }
            }

            deleteButton.isInvisible = !isEnabled
            deleteButton.isClickable = isEnabled
            weekdaysLabel.isClickable = isEnabled
            hoursLabel.isClickable = isEnabled
        }
    }

    private fun getWeekdaysSuggestion(isFirst: Boolean): Weekdays {
        if (isFirst) {
            val firstWorkDayIdx = Weekdays.getWeekdayIndex(countryInfo.firstDayOfWorkweek)
            val result = BooleanArray(Weekdays.OSM_ABBR_WEEKDAYS.size)
            for (i in 0 until countryInfo.workweekDays) {
                result[(i + firstWorkDayIdx) % Weekdays.WEEKDAY_COUNT] = true
            }
            return Weekdays(result)
        }
        return Weekdays()
    }

    private fun openSetWeekdaysDialog(weekdays: Weekdays, callback: (weekdays: Weekdays) -> Unit) {
        WeekdaysPickerDialog.show(context, weekdays, callback)
    }

    private fun openSetTimeDialog(minutes: Int, callback: (minutes: Int) -> Unit) {
        TimePickerDialog(context, minutes / 60, minutes % 60, DateFormat.is24HourFormat(context)) { hourOfDay, minute ->
            callback(hourOfDay * 60 + minute)
        }.show()
    }
}
