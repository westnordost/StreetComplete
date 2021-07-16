package de.westnordost.streetcomplete.quests.diet_type

import android.os.Bundle
import android.view.View
import androidx.core.os.bundleOf

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.databinding.QuestButtonpanelYesNoOnlyBinding
import de.westnordost.streetcomplete.databinding.QuestDietTypeExplanationBinding
import de.westnordost.streetcomplete.ktx.viewBinding
import de.westnordost.streetcomplete.quests.AbstractQuestAnswerFragment
import de.westnordost.streetcomplete.quests.diet_type.DietAvailability.*

class AddDietTypeForm : AbstractQuestAnswerFragment<DietAvailability>() {

    override val contentLayoutResId = R.layout.quest_diet_type_explanation
    override val buttonsResId = R.layout.quest_buttonpanel_yes_no_only

    private val questDietTypeBinding by viewBinding(QuestDietTypeExplanationBinding::bind)
    private val questButtonPanelBinding by viewBinding(QuestButtonpanelYesNoOnlyBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        questButtonPanelBinding.yesButton.setOnClickListener { applyAnswer(DIET_YES) }
        questButtonPanelBinding.noButton.setOnClickListener { applyAnswer(DIET_NO) }
        questButtonPanelBinding.onlyButton.setOnClickListener { applyAnswer(DIET_ONLY) }

        val resId = arguments?.getInt(ARG_DIET) ?: 0
        if (resId > 0) {
            questDietTypeBinding.descriptionLabel.setText(resId)
        } else {
            questDietTypeBinding.descriptionLabel.visibility = View.GONE
        }
    }

    companion object {
        private const val ARG_DIET = "diet_explanation"

        fun create(dietExplanationResId: Int): AddDietTypeForm {
            val form = AddDietTypeForm()
            form.arguments = bundleOf(ARG_DIET to dietExplanationResId)
            return form
        }
    }
}
