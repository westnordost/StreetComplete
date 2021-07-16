package de.westnordost.streetcomplete.quests

import android.os.Bundle
import android.view.View

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestButtonpanelYesNoBinding
import de.westnordost.streetcomplete.ktx.viewBinding

/** Abstract base class for dialogs in which the user answers a yes/no quest  */
abstract class AYesNoQuestAnswerFragment<T> : AbstractQuestAnswerFragment<T>() {

    override val buttonsResId = R.layout.quest_buttonpanel_yes_no

    private val binding by viewBinding(QuestButtonpanelYesNoBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.yesButton.setOnClickListener { onClick(true) }
        binding.noButton.setOnClickListener { onClick(false) }
    }

    protected abstract fun onClick(answer: Boolean)
}
