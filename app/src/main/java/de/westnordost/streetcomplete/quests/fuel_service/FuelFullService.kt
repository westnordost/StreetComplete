package de.westnordost.streetcomplete.quests.fuel_service

enum class FuelFullService(val osmValue: String) {
    YES("yes"),
    NO("no"),
    ONLY("yes")
}

fun Boolean.toFuelFullService(): FuelFullService = if (this) FuelFullService.YES else FuelFullService.NO
