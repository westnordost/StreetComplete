package de.westnordost.streetcomplete.quests.fuel_service

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AYesNoQuestAnswerFragment
import de.westnordost.streetcomplete.quests.OtherAnswer


class AddFuelFullServiceForm : AYesNoQuestAnswerFragment<FuelFullService>() {

    override val otherAnswers = listOf(
        OtherAnswer(R.string.quest_fuelFullService_type_only) { applyAnswer(FuelFullService.ONLY) }
    )

    override fun onClick(answer: Boolean) {
        applyAnswer(answer.toFuelFullService())
    }
}
