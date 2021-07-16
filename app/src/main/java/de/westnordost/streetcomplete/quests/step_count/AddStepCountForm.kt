package de.westnordost.streetcomplete.quests.step_count

import android.os.Bundle
import android.view.View
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestStepCountBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.util.TextChangedWatcher

class AddStepCountForm : AbstractQuestFormAnswerFragment<Int>() {

    override val contentLayoutResId = R.layout.quest_step_count

    private val binding by viewBinding(QuestStepCountBinding::bind)

    private val count get() = binding.countInput.text?.toString().orEmpty().trim()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.countInput.addTextChangedListener(TextChangedWatcher { checkIsFormComplete() })
    }

    override fun isFormComplete() = count.isNotEmpty() && count.toInt() > 0

    override fun onClickOk() {
        applyAnswer(count.toInt())
    }
}
