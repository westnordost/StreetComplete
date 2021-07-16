package de.westnordost.streetcomplete.quests.postbox_collection_times

import de.westnordost.streetcomplete.quests.opening_hours.model.OpeningHoursRuleList

sealed class CollectionTimesAnswer

data class CollectionTimes(val times: OpeningHoursRuleList) : CollectionTimesAnswer()
object NoCollectionTimesSign : CollectionTimesAnswer()
