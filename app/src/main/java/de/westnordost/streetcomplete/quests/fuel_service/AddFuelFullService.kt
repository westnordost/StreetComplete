package de.westnordost.streetcomplete.quests.fuel_service

import de.westnordost.streetcomplete.R
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapChangesBuilder
import de.westnordost.streetcomplete.data.osm.osmquests.OsmFilterQuestType
import de.westnordost.streetcomplete.data.quest.AllCountriesExcept

class AddFuelFullService : OsmFilterQuestType<FuelFullService>() {

    override val elementFilter = """
        nodes, ways with
          amenity = fuel
          and !full_service
          and ( self_service != only or automated = no )
    """
    override val commitMessage = "Add full service information to fuel station"
    override val wikiLink = "Tag:amenity=fuel"
    override val icon = R.drawable.ic_quest_fuel_full_service

    override fun getTitle(tags: Map<String, String>) : Int {
        val hasName = tags.containsKey("name")
        val hasBrand = tags.containsKey("brand")
        return when {
            hasName || hasBrand ->  R.string.quest_fuelFullService_name_title
            else ->                 R.string.quest_fuelFullService_title
        }
    }

    override val enabledInCountries = AllCountriesExcept("US-OR","US-NJ","BR","ZA", "AU") // Self service is illegal in Oregon, New Jersey, Brazil and South Africa

    override fun createForm() = AddFuelFullServiceForm()

    override fun applyAnswerTo(answer: FuelFullService, changes: StringMapChangesBuilder) {
        changes.add("full_service", answer.osmValue)
        if (answer.ordinal == 2) changes.addOrModify("self_service", "no")
    }
}
