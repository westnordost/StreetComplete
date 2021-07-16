package de.westnordost.streetcomplete.quests.opening_hours.parser

import ch.poole.openinghoursparser.*
import de.westnordost.streetcomplete.quests.opening_hours.adapter.OffDaysRow
import de.westnordost.streetcomplete.quests.opening_hours.adapter.OpeningHoursRow
import de.westnordost.streetcomplete.quests.opening_hours.adapter.OpeningMonthsRow
import de.westnordost.streetcomplete.quests.opening_hours.adapter.OpeningWeekdaysRow
import de.westnordost.streetcomplete.quests.opening_hours.model.*
import de.westnordost.streetcomplete.quests.opening_hours.model.Weekdays.Companion.OSM_ABBR_WEEKDAYS
import de.westnordost.streetcomplete.quests.opening_hours.model.Weekdays.Companion.PUBLIC_HOLIDAY
import java.io.ByteArrayInputStream

/** Returns null if the opening hours string is invalid */
fun String.toOpeningHoursRules(): OpeningHoursRuleList? {
    val rules: List<Rule>
    try {
        val parser = OpeningHoursParser(ByteArrayInputStream(toByteArray()))
        rules = parser.rules(false)
    } catch (e: ParseException) {
        // parsing failed, value is malformed
        return null
    }
    return OpeningHoursRuleList(rules)
}

/** returns null if the list of rules cannot be displayed by the opening hours widget */
fun OpeningHoursRuleList.toOpeningHoursRows(): List<OpeningHoursRow>? {
    if (!isSupported()) {
        // parsable, but not supported by StreetComplete
        return null
    }

    val result = mutableListOf<OpeningHoursRow>()

    var currentMonths: Months? = null

    for (rule in rules) {
        val dates = rule.dates

        val months = dates?.toMonths()
        if (months != currentMonths) {
            result.add(OpeningMonthsRow(months ?: Months()))
        }
        currentMonths = months

        if (rule.modifier != null && rule.modifier!!.isSimpleOff()) {
            result.add(rule.createOffDays())
        } else {
            result.addAll(rule.createOpeningWeekdays())
        }
    }

    return result
}

/** returns null if the list of rules cannot be displayed by the opening hours widget */
fun OpeningHoursRuleList.toCollectionTimesRows(): List<OpeningHoursRow>? {
    if (!isSupportedCollectionTimes()) {
        // parsable, but not supported by StreetComplete
        return null
    }

    val result = mutableListOf<OpeningHoursRow>()

    for (rule in rules) {
        if (rule.modifier != null && rule.modifier!!.isSimpleOff()) {
            result.add(rule.createOffDays())
        } else {
            result.addAll(rule.createOpeningWeekdays())
        }
    }

    return result
}

/* ---------------------------------- Checks if it is supported --------------------------------- */

fun OpeningHoursRuleList.isSupported(): Boolean = rules.isSupported()
fun OpeningHoursRuleList.isSupportedCollectionTimes(): Boolean = rules.isSupportedCollectionTimes()

/** Returns true if supported by StreetComplete
 * Returns false otherwise, in cases where it is not directly representable
 *
 * It is first checking each rule (parts of opening_hours tag separated by ; sign)
 * is it possible to recreate it by taking only supported parts
 * later it checks also some additional limitations imposed by SC */
@JvmName("isSupportedRuleList")
fun List<Rule>.isSupported(): Boolean =
    // all rules must be supported
    all { it.isSupported() } &&
    // this kind of opening hours specification likely require fix
    // anyway, it is not representable directly by SC
    (!weekdaysCollideWithAnother())

/** Returns true if the collection times are supported by StreetComplete
 * Returns false otherwise, in cases where it is not directly representable */
@JvmName("isSupportedCollectionTimesRuleList")
fun List<Rule>.isSupportedCollectionTimes(): Boolean =
    // all rules must be supported
    all { it.isSupportedCollectionTimes() } &&
        // this kind of collection times specification likely require fix
        // anyway, it is not representable directly by SC
        (!weekdaysCollideWithAnother())

fun Rule.isSupported(): Boolean =
    !isEmpty &&
    // 24/7 not supported
    !isTwentyfourseven &&
    // comments not supported
    comment == null &&
    // fallback rules are not supported
    !isFallBack &&
    // "1995-1998 08:00-11:00" not supported
    years == null &&
    // "05-08 08:00-11:00" not supported
    weeks == null &&
    (
        // for normal rules, only "Mo-Fr" not supported. "open" modifier is ok as long as it does not have a comment
        (modifier == null || modifier!!.isSimpleOpen()) && !times.isNullOrEmpty() ||
        // "off"/"closed" only compatible without comment and no times
        (modifier != null && times.isNullOrEmpty() && modifier!!.isSimpleOff())
    ) &&
    // all sub-elements must be supported if specified
    holidays?.all { it.isSupported() } ?: true &&
    days?.all { it.isSupported() } ?: true &&
    times?.all { it.isSupported() } ?: true &&
    dates?.all { it.isSupported() } ?: true

fun Rule.isSupportedCollectionTimes(): Boolean =
    !isEmpty &&
    // 24/7 not supported
    !isTwentyfourseven &&
    // comments not supported
    comment == null &&
    // fallback rules are not supported
    !isFallBack &&
    // "1995-1998 08:00-11:00" not supported
    years == null &&
    // "05-08 08:00-11:00" not supported
    weeks == null &&
    (
        // for normal rules, only "Mo-Fr" not supported. "open" modifier is ok as long as it does not have a comment
        (modifier == null || modifier!!.isSimpleOpen()) && !times.isNullOrEmpty() ||
        // "off"/"closed" only compatible without comment and no times
        (modifier != null && times.isNullOrEmpty() && modifier!!.isSimpleOff())
    ) &&
    // all sub-elements must be supported if specified
    holidays?.all { it.isSupported() } ?: true &&
    days?.all { it.isSupported() } ?: true &&
    // only time points are supported for collection times
    times?.all { it.isSupportedCollectionTimes() } ?: true &&
    // months not supported
    dates == null

fun DateRange.isSupported(): Boolean =
    startDate.isSupported() && (endDate?.isSupported() ?: true) && interval == 0

fun RuleModifier.isSimpleOpen(): Boolean =
    comment == null && modifier == RuleModifier.Modifier.OPEN

fun RuleModifier.isSimpleOff(): Boolean =
    comment == null && (modifier == RuleModifier.Modifier.OFF || modifier == RuleModifier.Modifier.CLOSED)

fun DateWithOffset.isSupported(): Boolean =
    // "Jan+" not supported
    !isOpenEnded &&
    // "Jan +Fr" not supported
    nthWeekDay == null && weekDayOffset == null && nth == 0 &&
    // "Jan +Fr + 3 days" not supported
    dayOffset == 0 &&
    // "1995 Jan" not supported
    year == YearRange.UNDEFINED_YEAR &&
    // "Jan 5" not supported
    day == DateWithOffset.UNDEFINED_MONTH_DAY &&
    // "easter" not supported
    varDate == null &&
    // month is required
    month != null

fun Holiday.isSupported(): Boolean =
    // only PH is supported
    type == Holiday.Type.PH &&
    // "PH Mo" (public holidays on Monday) not supported
    useAsWeekDay &&
    // "PH +5 days" not supported
    offset == 0

fun WeekDayRange.isSupported(): Boolean =
    nths == null && offset == 0 &&
    // not sure how/if this can be null, just to be sure
    startDay != null

fun TimeSpan.isSupported(): Boolean =
    // "sunrise" etc not supported
    startEvent == null && endEvent == null &&
    interval == 0 &&
    start != TimeSpan.UNDEFINED_TIME

fun TimeSpan.isSupportedCollectionTimes(): Boolean =
    // "sunrise" etc not supported
    startEvent == null && endEvent == null &&
    interval == 0 &&
    start != TimeSpan.UNDEFINED_TIME &&
    // only support time points
    end == TimeSpan.UNDEFINED_TIME

/* ------------------------------ Collision/Intersection checking ------------------------------- */

/** For example, "Mo-Fr 10:00-12:00; We 14:00-16:00" self-collides: Wednesday is overwritten
 *  to only be open 14:00 to 16:00. A rule collides with another whenever the days overlap. When
 *  non-additive rules and additive rules mix, it becomes a bit difficult to find it out */
@JvmName("weekdaysCollideWithAnotherInRuleList")
fun List<Rule>.weekdaysCollideWithAnother(): Boolean {
    for (i in 0 until size) {
        val rule1 = get(i)
        var additive = true
        for (j in i+1 until size) {
            val rule2 = get(j)
            // off/closed rules may collide because they overwrite their whole weekdays anyway
            // (because we do not support times for off-rules in StreetComplete)
            if (rule2.modifier?.isSimpleOff() == true) {
                continue
            }
            // additive rules do not collide
            if (!rule2.isAdditive) {
                additive = false
            }
            if (!additive) {
                if (rule1.collidesWith(rule2)) return true
            }
        }
    }
    return false
}

private fun Rule.collidesWith(other: Rule): Boolean {
    // if both rules have date ranges and no date range of the first collides with any date range
    // of the second, they do not collide
    val dates1 = dates
    if (dates1 != null) {
        val dates2 = other.dates
        if (dates2 != null) {
            if (dates1.all { date1 -> dates2.none { date2 -> date2.intersectWith(date1) }})
                return false
        }
    }
    // if no weekdays specified: collides with everything
    if (days.isNullOrEmpty() && holidays.isNullOrEmpty()) return true
    if (other.days.isNullOrEmpty() && other.holidays.isNullOrEmpty()) return true

    // handle times looping into next day
    val compareDays =
        if (times?.any { it.expandsIntoNextDay() } == true) days?.map { it.expandedToNextDay() }
        else days
    val compareOtherDays =
        if (other.times?.any { it.expandsIntoNextDay() } == true) other.days?.map { it.expandedToNextDay() }
        else other.days

    if (holidays.orEmpty().intersectsWith(other.holidays.orEmpty())) return true
    if (compareDays.orEmpty().intersectsWith(compareOtherDays.orEmpty())) return true

    return false
}

private fun DateRange.intersectWith(other: DateRange): Boolean {
    return toCircularSection().intersects(other.toCircularSection())
}

@JvmName("intersectsWithWeekDayRange")
private fun List<WeekDayRange>.intersectsWith(other: List<WeekDayRange>): Boolean =
    any{ range1 -> other.any { range2 -> range1.intersectsWith(range2) } }

private fun WeekDayRange.intersectsWith(other: WeekDayRange): Boolean {
    return toCircularSection().intersects(other.toCircularSection())
}

@JvmName("intersectsWithHolidays")
private fun List<Holiday>.intersectsWith(other: List<Holiday>): Boolean =
    any{ holiday1 -> other.any { holiday2 -> holiday1.intersectsWith(holiday2) } }

private fun Holiday.intersectsWith(other: Holiday): Boolean {
    require(isSupported())
    require(other.isSupported())
    return type == other.type
}

private fun WeekDayRange.expandedToNextDay(): WeekDayRange = WeekDayRange().also {
    it.startDay = startDay
    val end = if (endDay == null) startDay else endDay
    val values = WeekDay.values()
    it.endDay = values[(end.ordinal+1) % values.size]
}

private fun TimeSpan.expandsIntoNextDay(): Boolean =
    end != TimeSpan.UNDEFINED_TIME && (end < start || end > 24 * 60)

private fun Rule.createOpeningWeekdays(): List<OpeningWeekdaysRow> {
    val weekdays = WeekDayRangesAndHolidays(days, holidays).toWeekdays()
    val timeRanges = times!!.map { it.toTimeRange() }
    return timeRanges.map { OpeningWeekdaysRow(weekdays, it) }
}

private fun Rule.createOffDays(): OffDaysRow {
    val weekdays = WeekDayRangesAndHolidays(days, holidays).toWeekdays()
    return OffDaysRow(weekdays)
}

private fun WeekDayRangesAndHolidays.toWeekdays(): Weekdays {
    val dayData = BooleanArray(OSM_ABBR_WEEKDAYS.size) { false }
    if (weekdayRanges != null) {
        for (weekDayRange in weekdayRanges) {
            val range = weekDayRange.toCircularSection()
            if (!range.loops) { // ranges like Tuesday-Saturday
                for (i in range.start..range.end) dayData[i] = true
            } else { // ranges like Saturday-Tuesday
                for (i in range.start until Weekdays.WEEKDAY_COUNT) dayData[i] = true
                for (i in 0..range.end) dayData[i] = true
            }
        }
    }
    if (holidays?.singleOrNull() != null) {
        dayData[PUBLIC_HOLIDAY] = true
    }
    return Weekdays(dayData)
}

private fun List<DateRange>.toMonths(): Months {
    val monthsData = BooleanArray(Months.MONTHS_COUNT)
    for (dateRange in this) {
        val range = dateRange.toCircularSection()
        if (!range.loops) { // ranges like Jan-Feb
            for (i in range.start..range.end) monthsData[i] = true
        } else { // ranges like Oct-Jan
            for (i in range.start until Months.MONTHS_COUNT) monthsData[i] = true
            for (i in 0..range.end) monthsData[i] = true
        }
    }
    return Months(monthsData)
}

private fun TimeSpan.toTimeRange() = TimeRange(
    start,
    if (end != TimeSpan.UNDEFINED_TIME) end % (24 * 60) else TimeRange.UNDEFINED_TIME,
    isOpenEnded
)

private fun DateRange.toCircularSection(): CircularSection {
    require(isSupported())
    return CircularSection(startDate.month.ordinal, (endDate ?: startDate).month.ordinal)
}

private fun WeekDayRange.toCircularSection(): CircularSection {
    require(isSupported())
    return CircularSection(startDay.ordinal, (endDay ?: startDay).ordinal)
}
