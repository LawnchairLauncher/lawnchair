package app.lawnchair.search.algorithms.data

import java.util.Calendar
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
    /**
     * The start time to show to the user. The calendar provider stores all-day events at UTC
     * midnight, so they're moved to local midnight of the same date. Otherwise, they show up a
     * day early west of UTC.
     */
    val localStartTime: Long
        get() = if (allDay) utcMidnightToLocal(startTime) else startTime

    companion object {
        /** Returns local midnight of the calendar date that [utcMillis] falls on in UTC. */
        fun utcMidnightToLocal(utcMillis: Long): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = utcMillis
            }
            return Calendar.getInstance().apply {
                clear()
                set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
            }.timeInMillis
        }
    }
}
