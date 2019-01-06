package de.westnordost.streetcomplete.quests.bike_parking_capacity

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.TextInputQuestAnswerFragment
import kotlinx.android.synthetic.main.quest_bike_parking_capacity.*

class AddBikeParkingCapacityForm : TextInputQuestAnswerFragment() {

    override val contentLayoutResId = R.layout.quest_bike_parking_capacity

    override val editText = capacityInput!!
}
