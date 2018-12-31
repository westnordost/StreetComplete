package de.westnordost.streetcomplete.quests.fire_hydrant

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.quests.ImageListQuestAnswerFragment
import de.westnordost.streetcomplete.view.Item

class AddFireHydrantTypeForm : ImageListQuestAnswerFragment() {

    override val items = listOf(
        Item("pillar", R.drawable.fire_hydrant_pillar, R.string.quest_fireHydrant_type_pillar),
        Item("underground", R.drawable.fire_hydrant_underground, R.string.quest_fireHydrant_type_underground),
        Item("wall", R.drawable.fire_hydrant_wall, R.string.quest_fireHydrant_type_wall),
        Item("pond", R.drawable.fire_hydrant_pond, R.string.quest_fireHydrant_type_pond)
    )

    override val itemsPerRow = 2
    override val maxNumberOfInitiallyShownItems = 2
}
