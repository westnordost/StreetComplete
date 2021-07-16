package de.westnordost.streetcomplete.quests.motorcycle_parking_capacity

import android.os.Bundle
import android.view.View
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestMotorcycleParkingCapacityBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.util.TextChangedWatcher

class AddMotorcycleParkingCapacityForm : AbstractQuestFormAnswerFragment<Int>() {

    override val contentLayoutResId = R.layout.quest_motorcycle_parking_capacity

    private val binding by viewBinding(QuestMotorcycleParkingCapacityBinding::bind)

    private val capacity get() = binding.capacityInput?.text?.toString().orEmpty().trim()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.capacityInput.addTextChangedListener(TextChangedWatcher { checkIsFormComplete() })
    }

    override fun isFormComplete() = capacity.isNotEmpty() && capacity.toInt() > 0

    override fun onClickOk() {
        applyAnswer(capacity.toInt())
    }
}
