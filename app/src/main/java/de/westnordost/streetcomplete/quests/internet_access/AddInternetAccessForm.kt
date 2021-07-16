package de.westnordost.streetcomplete.quests.internet_access

import android.os.Bundle
import android.view.View

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestInternetAccessBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestFormAnswerFragment
import de.westnordost.streetcomplete.quests.internet_access.InternetAccess.*
import kotlin.NullPointerException

class AddInternetAccessForm : AbstractQuestFormAnswerFragment<InternetAccess>() {

    override val defaultExpanded = false

    override val contentLayoutResId = R.layout.quest_internet_access

    private val binding by viewBinding(QuestInternetAccessBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.radioButtonGroup.setOnCheckedChangeListener { _, _ -> checkIsFormComplete() }
    }

    override fun onClickOk() {
        applyAnswer(
            when (binding.radioButtonGroup.checkedRadioButtonId) {
                R.id.wifi ->     WIFI
                R.id.no ->       NO
                R.id.terminal -> TERMINAL
                R.id.wired ->    WIRED
                else -> throw NullPointerException()
            }
        )
    }

    override fun isFormComplete() = binding.radioButtonGroup.checkedRadioButtonId != -1
}
