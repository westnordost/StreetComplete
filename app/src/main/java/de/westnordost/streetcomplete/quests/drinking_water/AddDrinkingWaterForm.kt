package de.westnordost.streetcomplete.quests.drinking_water

import android.os.Bundle
import android.view.View
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestDrinkingWaterBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.drinking_water.DrinkingWater.*

class AddDrinkingWaterForm : AbstractQuestFormAnswerFragment<DrinkingWater>() {

    override val defaultExpanded = false

    override val contentLayoutResId = R.layout.quest_drinking_water

    private val binding by viewBinding(QuestDrinkingWaterBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.radioButtonGroup.setOnCheckedChangeListener { _, _ -> checkIsFormComplete() }
    }

    override fun onClickOk() {
        applyAnswer(when (binding.radioButtonGroup.checkedRadioButtonId) {
            R.id.potable_signed -> POTABLE_SIGNED
            R.id.potable_unsigned -> POTABLE_UNSIGNED
            R.id.not_potable_signed -> NOT_POTABLE_SIGNED
            R.id.not_potable_unsigned -> NOT_POTABLE_UNSIGNED
            else -> throw NullPointerException()
        })
    }

    override fun isFormComplete() = binding.radioButtonGroup.checkedRadioButtonId != -1
}
