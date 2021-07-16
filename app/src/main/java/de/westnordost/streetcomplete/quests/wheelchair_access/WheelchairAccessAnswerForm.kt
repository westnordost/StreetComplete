package de.westnordost.streetcomplete.quests.wheelchair_access

import android.os.Bundle
import android.view.View
import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestButtonpanelYesLimitedNoBinding
import de.westnordost.streetcomplete.ktx.viewBinding

import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment
import de.westnordost.streetcomplete.quests.wheelchair_access.WheelchairAccess.*

open class WheelchairAccessAnswerForm : AbstractQuestAnswerFragment<WheelchairAccess>() {

    override val buttonsResId = R.layout.quest_buttonpanel_yes_limited_no

    private val binding by viewBinding(QuestButtonpanelYesLimitedNoBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.yesButton.setOnClickListener { applyAnswer(YES) }
        binding.limitedButton.setOnClickListener { applyAnswer(LIMITED) }
        binding.noButton.setOnClickListener { applyAnswer(NO) }
    }
}
