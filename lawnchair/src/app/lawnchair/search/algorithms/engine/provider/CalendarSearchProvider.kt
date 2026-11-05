package app.lawnchair.search.algorithms.engine.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import android.util.Log
import app.lawnchair.calendar.CalendarEventSource
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.data.CalendarEvent
import app.lawnchair.search.algorithms.engine.SearchPermission
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import com.android.launcher3.R
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

object CalendarSearchProvider : SearchProvider, SearchPermission {
    override val id: String = "calendar"

    override fun search(
        context: Context,
        query: String,
    ): Flow<List<SearchResult>> = flow {
        val prefs = PreferenceManager.getInstance(context)
        val prefs2 = PreferenceManager2.getInstance(context)

        if (query.isBlank() || !prefs.searchResultCalendar.get() || !checkPermission(context)) {
            emit(emptyList())
            return@flow
        }

        val maxResults = prefs2.maxCalendarResultCount.firstCached()
        val events = try {
            val now = System.currentTimeMillis()
            val showCanceled = prefs2.showCalendarCanceledEvents.firstCached()
            withContext(Dispatchers.IO) {
                CalendarEventSource.query(
                    context = context,
                    query = query,
                    windowStart = now - ONE_DAY_MILLIS,
                    windowEnd = now + ONE_YEAR_MILLIS,
                    now = now,
                    maxResults = maxResults,
                    showCanceled = showCanceled,
                ).map { event ->
                    CalendarEvent(
                        eventId = event.eventId,
                        title = event.title,
                        description = formatDescription(
                            context = context,
                            startTime = event.startTime,
                            endTime = event.endTime,
                            allDay = event.allDay,
                            location = event.location,
                            isCanceled = event.isCanceled,
                        ),
                        calendarColor = event.calendarColor,
                        startTime = event.startTime,
                        endTime = event.endTime,
                        allDay = event.allDay,
                        isCanceled = event.isCanceled,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: SecurityException) {
            Log.w("CalendarSearchProvider", "Calendar permission was revoked while searching", e)
            emptyList()
        } catch (e: Exception) {
            // wtf
            Log.e("CalendarSearchProvider", "Failed to query calendar events", e)
            emptyList()
        }

        emit(events.map { SearchResult.Calendar(it) })
    }

    override fun checkPermission(context: Context): Boolean {
        return context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
    }

    private fun formatDescription(
        context: Context,
        startTime: Long,
        endTime: Long,
        allDay: Boolean,
        location: String?,
        isCanceled: Boolean,
    ): String {
        val localStartTime = if (allDay) CalendarEvent.utcMidnightToLocal(startTime) else startTime
        val displayEndTime = if (allDay && endTime > startTime) {
            val lastDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = endTime
                add(Calendar.DAY_OF_MONTH, -1)
            }
            CalendarEvent.utcMidnightToLocal(lastDay.timeInMillis)
        } else if (allDay) {
            CalendarEvent.utcMidnightToLocal(endTime)
        } else {
            endTime
        }
        val startDate = Calendar.getInstance().apply { timeInMillis = localStartTime }
        val endDate = Calendar.getInstance().apply { timeInMillis = displayEndTime }
        val sameYear = startDate.get(Calendar.ERA) == endDate.get(Calendar.ERA) &&
            startDate.get(Calendar.YEAR) == endDate.get(Calendar.YEAR)
        val sameDay = sameYear && startDate.get(Calendar.DAY_OF_YEAR) == endDate.get(Calendar.DAY_OF_YEAR)
        val dateTime = if (sameDay) {
            formatSingleDay(context, localStartTime, allDay)
        } else {
            val flags = DateUtils.FORMAT_SHOW_DATE or
                (if (allDay) 0 else DateUtils.FORMAT_SHOW_TIME) or
                (if (sameYear) 0 else DateUtils.FORMAT_SHOW_YEAR)
            val range = context.getString(
                R.string.calendar_search_date_range,
                DateUtils.formatDateTime(context, localStartTime, flags),
                DateUtils.formatDateTime(context, displayEndTime, flags),
            )
            if (allDay) {
                context.getString(
                    R.string.calendar_search_date_time,
                    range,
                    context.getString(R.string.calendar_search_all_day),
                )
            } else {
                range
            }
        }
        return listOfNotNull(
            if (isCanceled) context.getString(R.string.calendar_search_canceled) else null,
            dateTime,
            location?.trim()?.takeIf(String::isNotBlank),
        )
            .joinToString(" \u2022 ")
    }

    private fun formatSingleDay(context: Context, startTime: Long, allDay: Boolean): String {
        val day = when {
            DateUtils.isToday(startTime) -> context.getString(R.string.calendar_search_today)

            isTomorrow(startTime) -> context.getString(R.string.calendar_search_tomorrow)

            else -> DateUtils.formatDateTime(
                context,
                startTime,
                DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NO_YEAR,
            )
        }
        val time = if (allDay) {
            context.getString(R.string.calendar_search_all_day)
        } else {
            DateUtils.formatDateTime(context, startTime, DateUtils.FORMAT_SHOW_TIME)
        }
        return context.getString(R.string.calendar_search_date_time, day, time)
    }

    private fun isTomorrow(timeMillis: Long): Boolean {
        val tomorrow = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val date = Calendar.getInstance().apply {
            timeInMillis = timeMillis
        }
        return tomorrow.get(Calendar.ERA) == date.get(Calendar.ERA) &&
            tomorrow.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
            tomorrow.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    }

    private const val ONE_DAY_MILLIS = 1L * 24 * 60 * 60 * 1000
    private val ONE_YEAR_MILLIS = TimeUnit.DAYS.toMillis(366)
}
