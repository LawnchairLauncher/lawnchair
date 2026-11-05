package app.lawnchair.search.algorithms.data

import app.lawnchair.calendar.CalendarEvent
import java.util.TimeZone

data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val description: String,
    val calendarColor: Int?,
    val startTime: Long,
    val endTime: Long,
    val allDay: Boolean,
) {
    val localStartTime: Long
        get() = if (allDay) utcMidnightToLocal(startTime) else startTime

    companion object {
        /** Returns local midnight of the calendar date that [utcMillis] falls on in UTC. */
        fun utcMidnightToLocal(
            utcMillis: Long,
            timeZone: TimeZone = TimeZone.getDefault(),
        ): Long {
            return CalendarEvent.utcMidnightToLocal(utcMillis, timeZone)
        }
    }
}
