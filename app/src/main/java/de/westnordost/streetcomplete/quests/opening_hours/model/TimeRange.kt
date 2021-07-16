package de.westnordost.streetcomplete.quests.opening_hours.model

import kotlinx.serialization.Serializable
import java.text.DateFormat
import java.time.*
import java.util.Locale

/** A time range from [start,end). The times are specified in minutes. */
@Serializable
data class TimeRange(val start: Int, val end: Int = UNDEFINED_TIME, val isOpenEnded: Boolean = false) : Comparable<TimeRange> {

    fun intersects(other: TimeRange): Boolean =
        isOpenEnded && other.start >= start ||
        other.isOpenEnded && start >= other.start ||
        loops && other.loops ||
        if (loops || other.loops) other.end > start || other.start < end
        else                      other.end > start && other.start < end

    val loops get() = end < start

    override fun compareTo(other: TimeRange): Int {
        // loopers come first,
        if (loops && !other.loops) return -1
        if (!loops && other.loops) return +1
        // then by start
        val result = start - other.start
        if (result != 0) return result
        // then by end
        return end - other.end
    }

    fun toStringUsing(locale: Locale, range: String): String {
        val sb = StringBuilder()
        sb.append(timeOfDayToString(locale, start))
        if (end != UNDEFINED_TIME && (start != end || !isOpenEnded)) {
            sb.append(range)
            var displayEnd = timeOfDayToString(locale, end)
            if (displayEnd == "00:00") displayEnd = "24:00"
            sb.append(displayEnd)
        }
        if (isOpenEnded) sb.append("+")
        return sb.toString()
    }

    override fun toString() = toStringUsing(Locale.GERMANY, "-")

    private fun timeOfDayToString(locale: Locale, minutes: Int): String {
        val todayAt = LocalDateTime.of(LocalDate.now(), LocalTime.ofSecondOfDay(minutes * 60L))
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        return DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(todayAt)
    }

    companion object {
        const val UNDEFINED_TIME = Int.MIN_VALUE
    }
}
