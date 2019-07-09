package de.westnordost.streetcomplete.quests.leaf_detail

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.AImageListQuestAnswerFragment
import de.westnordost.streetcomplete.view.Item

class AddForestLeafTypeForm : AImageListQuestAnswerFragment<String, String>() {

    override val items = listOf(
            Item("needleleaved", R.drawable.needleleaved, R.string.quest_leaf_type_needles),
            Item("broadleaved", R.drawable.broadleaved, R.string.quest_leaf_type_broadleaved),
            Item("mixed", R.drawable.mixed, R.string.quest_leaf_type_mixed)
    )

    override val itemsPerRow = 3

    override fun onClickOk(selectedItems: List<String>) {
        applyAnswer(selectedItems.single())
    }
}
