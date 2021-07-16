package de.westnordost.streetcomplete.quests.bike_parking_capacity

import android.os.Bundle
import android.view.View
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestBikeParkingCapacityBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.util.TextChangedWatcher

class AddBikeParkingCapacityForm : AbstractQuestFormAnswerFragment<Int>() {

    override val contentLayoutResId = R.layout.quest_bike_parking_capacity

    private val binding by viewBinding(QuestBikeParkingCapacityBinding::bind)

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
