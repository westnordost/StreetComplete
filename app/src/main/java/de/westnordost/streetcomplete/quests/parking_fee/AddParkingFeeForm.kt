package de.westnordost.streetcomplete.quests.parking_fee

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.isGone
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.FragmentQuestAnswerBinding
import de.westnordost.streetcomplete.databinding.QuestButtonpanelYesNoBinding
import de.westnordost.streetcomplete.databinding.QuestFeeHoursBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer
import de.westnordost.streetcomplete.quests.opening_hours.adapter.RegularOpeningHoursAdapter
import de.westnordost.streetcomplete.quests.opening_hours.adapter.OpeningHoursRow
import de.westnordost.streetcomplete.util.AdapterDataChangedWatcher
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class AddParkingFeeForm : AbstractQuestFormAnswerFragment<FeeAnswer>() {

    override val contentLayoutResId = R.layout.quest_fee_hours
    override val buttonsResId = R.layout.quest_buttonpanel_yes_no

    private val fragmentQABinding by viewBinding(FragmentQuestAnswerBinding::bind)
    private val questButtonPanelBinding by viewBinding(QuestButtonpanelYesNoBinding::bind)
    private val questFeeHoursBinding by viewBinding(QuestFeeHoursBinding::bind)

    override val otherAnswers = listOf(
        OtherAnswer(R.string.quest_fee_answer_hours) { isDefiningHours = true }
    )

    private lateinit var openingHoursAdapter: RegularOpeningHoursAdapter

    private lateinit var content: ViewGroup

    private var isDefiningHours: Boolean = false
    set(value) {
        field = value

        content.isGone = !value
        questButtonPanelBinding.noButton?.isGone = value
        questButtonPanelBinding.yesButton?.isGone = value
    }
    private var isFeeOnlyAtHours: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        openingHoursAdapter = RegularOpeningHoursAdapter(requireContext(), countryInfo)
        openingHoursAdapter.rows = loadOpeningHoursData(savedInstanceState).toMutableList()
        openingHoursAdapter.registerAdapterDataObserver( AdapterDataChangedWatcher { checkIsFormComplete() })
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        content = view.findViewById(R.id.content)

        // must be read here because setting these values effects the UI
        isFeeOnlyAtHours = savedInstanceState?.getBoolean(IS_FEE_ONLY_AT_HOURS, true) ?: true
        isDefiningHours = savedInstanceState?.getBoolean(IS_DEFINING_HOURS) ?: false

        fragmentQABinding.okButton.setOnClickListener { onClickOk() }
        questButtonPanelBinding.yesButton.setOnClickListener { onClickYesNo(true) }
        questButtonPanelBinding.noButton.setOnClickListener { onClickYesNo(false) }

        questFeeHoursBinding.openingHoursList.layoutManager = LinearLayoutManager(activity, RecyclerView.VERTICAL, false)
        questFeeHoursBinding.openingHoursList.adapter = openingHoursAdapter
        questFeeHoursBinding.openingHoursList.isNestedScrollingEnabled = false
        checkIsFormComplete()

        questFeeHoursBinding.addTimesButton.setOnClickListener { openingHoursAdapter.addNewWeekdays() }

        val spinnerItems = listOf(
            getString(R.string.quest_fee_only_at_hours),
            getString(R.string.quest_fee_not_at_hours)
        )
        questFeeHoursBinding.selectFeeOnlyAtHours.adapter = ArrayAdapter(requireContext(), R.layout.spinner_item_centered, spinnerItems)
        questFeeHoursBinding.selectFeeOnlyAtHours.setSelection(if (isFeeOnlyAtHours) 0 else 1)
        questFeeHoursBinding.selectFeeOnlyAtHours.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                isFeeOnlyAtHours = position == 0
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    override fun onClickOk() {
        val times = openingHoursAdapter.createOpeningHours()
        if (times.rules.isNotEmpty()) {
            if(isFeeOnlyAtHours) {
                applyAnswer(HasFeeAtHours(times))
            } else {
                applyAnswer(HasFeeExceptAtHours(times))
            }
        } else {
            onClickYesNo(!isFeeOnlyAtHours)
        }
    }

    private fun onClickYesNo(answer: Boolean) {
        applyAnswer(if(answer) HasFee else HasNoFee)
    }

    private fun loadOpeningHoursData(savedInstanceState: Bundle?): List<OpeningHoursRow> =
        savedInstanceState?.let { Json.decodeFromString(it.getString(OPENING_HOURS_DATA)!!) } ?: emptyList()

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(OPENING_HOURS_DATA, Json.encodeToString(openingHoursAdapter.rows))
        outState.putBoolean(IS_DEFINING_HOURS, isDefiningHours)
        outState.putBoolean(IS_FEE_ONLY_AT_HOURS, isFeeOnlyAtHours)
    }

    override fun isFormComplete() =
        isDefiningHours && openingHoursAdapter.rows.isNotEmpty()

    companion object {
        private const val OPENING_HOURS_DATA = "oh_data"
        private const val IS_FEE_ONLY_AT_HOURS = "oh_fee_only_at"
        private const val IS_DEFINING_HOURS = "oh"
    }
}
