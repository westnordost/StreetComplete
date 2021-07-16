package de.westnordost.streetcomplete.quests.parking_access

import android.os.Bundle
import android.view.View

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestParkingAccessBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.parking_access.ParkingAccess.*

class AddParkingAccessForm : AbstractQuestFormAnswerFragment<ParkingAccess>() {

    override val contentLayoutResId = R.layout.quest_parking_access

    private val binding by viewBinding(QuestParkingAccessBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.radioButtonGroup.setOnCheckedChangeListener { _, _ -> checkIsFormComplete() }
    }

    override fun onClickOk() {
        applyAnswer(when (binding.radioButtonGroup.checkedRadioButtonId) {
            R.id.yes            -> YES
            R.id.customers      -> CUSTOMERS
            R.id.private_access -> PRIVATE
            else -> throw NullPointerException()
        })
    }

    override fun isFormComplete() = binding.radioButtonGroup.checkedRadioButtonId != -1
}
