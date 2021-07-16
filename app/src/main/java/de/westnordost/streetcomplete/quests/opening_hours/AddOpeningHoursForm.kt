package de.westnordost.streetcomplete.quests.opening_hours

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.appcompat.widget.PopupMenu
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.util.AdapterDataChangedWatcher

import android.view.Menu.NONE
import androidx.core.view.isGone
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.databinding.QuestOpeningHoursBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.quests.opening_hours.adapter.*
import de.westnordost.streetcomplete.quests.opening_hours.parser.toOpeningHoursRows
import de.westnordost.streetcomplete.quests.opening_hours.parser.toOpeningHoursRules
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AddOpeningHoursForm : AbstractQuestFormAnswerFragment<OpeningHoursAnswer>() {

    override val contentLayoutResId = R.layout.quest_opening_hours

    private val binding by viewBinding(QuestOpeningHoursBinding::bind)

    override val otherAnswers = listOf(
        OtherAnswer(R.string.quest_openingHours_no_sign) { confirmNoSign() },
        OtherAnswer(R.string.quest_openingHours_answer_no_regular_opening_hours) { showInputCommentDialog() },
        OtherAnswer(R.string.quest_openingHours_answer_247) { showConfirm24_7Dialog() },
        OtherAnswer(R.string.quest_openingHours_answer_seasonal_opening_hours) {
            setAsResurvey(false)
            openingHoursAdapter.changeToMonthsMode()
        }
    )

    private lateinit var openingHoursAdapter: RegularOpeningHoursAdapter

    private var isDisplayingPreviousOpeningHours: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openingHoursAdapter = RegularOpeningHoursAdapter(requireContext(), countryInfo)
        openingHoursAdapter.registerAdapterDataObserver(AdapterDataChangedWatcher { checkIsFormComplete() })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (savedInstanceState != null) {
            onLoadInstanceState(savedInstanceState)
        } else {
            initStateFromTags()
        }

        binding.openingHoursList.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        binding.openingHoursList.adapter = openingHoursAdapter
        binding.openingHoursList.isNestedScrollingEnabled = false
        checkIsFormComplete()

        binding.addTimesButton.setOnClickListener { onClickAddButton(it) }
    }

    private fun onClickAddButton(v: View) {
        val rows = openingHoursAdapter.rows

        val addMonthAvailable = rows.any { it is OpeningMonthsRow }
        val addTimeAvailable = rows.isNotEmpty() && rows.last() is OpeningWeekdaysRow
        val addOffDayAvailable = rows.isNotEmpty() && rows.last() is OpeningWeekdaysRow

        if (addMonthAvailable || addTimeAvailable || addOffDayAvailable) {
            val popup = PopupMenu(requireContext(), v)
            if (addTimeAvailable) popup.menu.add(NONE, 0, NONE, R.string.quest_openingHours_add_hours)
            popup.menu.add(NONE, 1, NONE, R.string.quest_openingHours_add_weekdays)
            if (addOffDayAvailable) popup.menu.add(NONE, 2, NONE, R.string.quest_openingHours_add_off_days)
            if (addMonthAvailable) popup.menu.add(NONE, 3, NONE, R.string.quest_openingHours_add_months)
            popup.setOnMenuItemClickListener { item ->
                when(item.itemId) {
                    0 -> openingHoursAdapter.addNewHours()
                    1 -> openingHoursAdapter.addNewWeekdays()
                    2 -> openingHoursAdapter.addNewOffDays()
                    3 -> openingHoursAdapter.addNewMonths()
                }
                true
            }
            popup.show()
        } else {
            openingHoursAdapter.addNewWeekdays()
        }
    }

    private fun initStateFromTags() {
        val oh = osmElement!!.tags["opening_hours"]
        val rows = oh?.toOpeningHoursRules()?.toOpeningHoursRows()
        if (rows != null) {
            openingHoursAdapter.rows = rows.toMutableList()
            setAsResurvey(true)
        } else {
            setAsResurvey(false)
        }
    }

    private fun onLoadInstanceState(savedInstanceState: Bundle) {
        openingHoursAdapter.rows = Json.decodeFromString(savedInstanceState.getString(OPENING_HOURS_DATA)!!)
        setAsResurvey(savedInstanceState.getBoolean(IS_DISPLAYING_PREVIOUS_HOURS))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putString(OPENING_HOURS_DATA, Json.encodeToString(openingHoursAdapter.rows))
        outState.putBoolean(IS_DISPLAYING_PREVIOUS_HOURS, isDisplayingPreviousOpeningHours)
    }

    override fun onClickOk() {
        applyAnswer(RegularOpeningHours(openingHoursAdapter.createOpeningHours()))
    }

    private fun showInputCommentDialog() {
        val view = LayoutInflater.from(activity).inflate(R.layout.quest_opening_hours_comment, null)
        val commentInput = view.findViewById<EditText>(R.id.commentInput)

        AlertDialog.Builder(requireContext())
            .setTitle(R.string.quest_openingHours_comment_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val txt = commentInput.text.toString().replace("\"","").trim()
                if (txt.isEmpty()) {
                    AlertDialog.Builder(requireContext())
                        .setMessage(R.string.quest_openingHours_emptyAnswer)
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                } else {
                    applyAnswer(DescribeOpeningHours(txt))
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setAsResurvey(resurvey: Boolean) {
        openingHoursAdapter.isEnabled = !resurvey
        isDisplayingPreviousOpeningHours = resurvey
        binding.addTimesButton.isGone = resurvey
        if (resurvey) {
            setButtonsView(R.layout.quest_buttonpanel_yes_no)
            requireView().findViewById<View>(R.id.noButton).setOnClickListener {
                setAsResurvey(false)
            }
            requireView().findViewById<View>(R.id.yesButton).setOnClickListener {
                applyAnswer(RegularOpeningHours(
                    osmElement!!.tags["opening_hours"]!!.toOpeningHoursRules()!!
                ))
            }
        } else {
            removeButtonsView()
        }
    }

    private fun showConfirm24_7Dialog() {
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.quest_openingHours_24_7_confirmation)
            .setPositiveButton(android.R.string.yes) { _, _ ->
                applyAnswer(AlwaysOpen)
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun confirmNoSign() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.quest_generic_confirmation_title)
            .setPositiveButton(R.string.quest_generic_confirmation_yes) { _, _ -> applyAnswer(NoOpeningHoursSign) }
            .setNegativeButton(R.string.quest_generic_confirmation_no, null)
            .show()
    }

    override fun isFormComplete() = openingHoursAdapter.rows.isNotEmpty() && !isDisplayingPreviousOpeningHours

    companion object {
        private const val OPENING_HOURS_DATA = "oh_data"
        private const val IS_DISPLAYING_PREVIOUS_HOURS = "oh_is_displaying_previous_hours"
    }
}
