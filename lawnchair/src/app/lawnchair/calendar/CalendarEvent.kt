package app.lawnchair.calendar

import java.util.Calendar
import java.util.TimeZone

/** An occurrence returned by the Android calendar provider. All-day times are UTC dates. */
data class CalendarEvent(
    val eventId: Long,
    val title: String,
    val location: String?,
    val calendarColor: Int?,
    val startTime: Long,
    val endTime: Long,
    val allDay: Boolean,
    val isCanceled: Boolean,
) {
    val localStartTime: Long
        get() = if (allDay) utcMidnightToLocal(startTime) else startTime

    companion object {
        /** Returns local midnight of the calendar date that [utcMillis] falls on in UTC. */
        fun utcMidnightToLocal(
            utcMillis: Long,
            timeZone: TimeZone = TimeZone.getDefault(),
        ): Long {
            val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = utcMillis
            }
            return Calendar.getInstance(timeZone).apply {
                clear()
                set(
                    utc.get(Calendar.YEAR),
                    utc.get(Calendar.MONTH),
                    utc.get(Calendar.DAY_OF_MONTH),
                )
            }.timeInMillis
        }
    }
}
