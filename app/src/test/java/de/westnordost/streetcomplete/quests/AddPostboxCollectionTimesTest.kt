package de.westnordost.streetcomplete.quests

import ch.poole.openinghoursparser.Rule
import ch.poole.openinghoursparser.TimeSpan
import ch.poole.openinghoursparser.WeekDay
import ch.poole.openinghoursparser.WeekDayRange
import de.westnordost.streetcomplete.data.osm.edits.update_tags.StringMapEntryAdd
import de.westnordost.streetcomplete.quests.opening_hours.model.*
import de.westnordost.streetcomplete.quests.postbox_collection_times.*
import org.junit.Test

class AddPostboxCollectionTimesTest {

    private val questType = AddPostboxCollectionTimes()

    @Test fun `apply no signed times answer`() {
        questType.verifyAnswer(
            NoCollectionTimesSign,
            StringMapEntryAdd("collection_times:signed", "no")
        )
    }

    @Test fun `apply collection times answer`() {
        questType.verifyAnswer(
            CollectionTimes(OpeningHoursRuleList(listOf(
                Rule().apply {
                    days = listOf(WeekDayRange().also {
                        it.startDay = WeekDay.MO
                    })
                    times = listOf(TimeSpan().also {
                        it.start = 60
                    })
                },
                Rule().apply {
                    days = listOf(WeekDayRange().also {
                        it.startDay = WeekDay.TU
                    })
                    times = listOf(TimeSpan().also {
                        it.start = 60*2
                    })
                    isAdditive = true
                },
            ))),
            // here ; would be fine as well instead of ,
            // see https://github.com/streetcomplete/StreetComplete/pull/2604#issuecomment-783823068
            StringMapEntryAdd("collection_times", "Mo 01:00, Tu 02:00")
        )
    }

    // for non-overlapping day ranges it does not matter whether
    // comma or semicolon is used - but for overlapping ones it matters
    // see https://github.com/streetcomplete/StreetComplete/pull/2604#issuecomment-783823068
    @Test fun `require comma where this matters`() {
        questType.verifyAnswer(
            CollectionTimes(OpeningHoursRuleList(listOf(
                Rule().apply {
                    days = listOf(WeekDayRange().also {
                        it.startDay = WeekDay.MO
                        it.endDay = WeekDay.WE
                    })
                    times = listOf(TimeSpan().also {
                        it.start = 60
                    })
                },
                Rule().apply {
                    days = listOf(WeekDayRange().also {
                        it.startDay = WeekDay.TU
                    })
                    times = listOf(TimeSpan().also {
                        it.start = 60*2
                    })
                    isAdditive = true
                },
            ))),
            StringMapEntryAdd("collection_times", "Mo-We 01:00, Tu 02:00")
        )
    }

    @Test fun `require comma where this matters and conflict is between nonadjacent ranges`() {
        questType.verifyAnswer(
            CollectionTimes(OpeningHoursRuleList(listOf(
                Rule().apply {
                    days = listOf(WeekDayRange().also {
                        it.startDay = WeekDay.MO
                        it.endDay = WeekDay.WE
                    })
                    times = listOf(TimeSpan().also {
                        it.start = 60
                    })
                },
                Rule().apply {
                    days = listOf(WeekDayRange().also {
                        it.startDay = WeekDay.TH
                        it.endDay = WeekDay.SU
                    })
                    times = listOf(TimeSpan().also {
                        it.start = 60*2
                    })
                    isAdditive = true
                },
                Rule().apply {
                    days = listOf(WeekDayRange().also {
                        it.startDay = WeekDay.MO
                    })
                    times = listOf(TimeSpan().also {
                        it.start = 60*3
                    })
                    isAdditive = true
                },
            ))),
            StringMapEntryAdd("collection_times", "Mo-We 01:00, Th-Su 02:00, Mo 03:00")
        )
    }
}
