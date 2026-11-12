package app.lawnchair.search.algorithms.engine.provider.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.text.format.DateUtils
import android.util.Log
import app.lawnchair.calendar.CalendarEventSource
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.data.CalendarSearchEvent
import app.lawnchair.search.algorithms.engine.SearchPermission
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import com.android.launcher3.R
import java.time.ZoneId
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
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
                val temporal = CalendarTemporalQueryParser.parse(
                    input = query,
                    now = now,
                    locale = context.resources.configuration.locales[0] ?: Locale.getDefault(),
                    timezone = ZoneId.systemDefault(),
                    todayWords = listOf(
                        "today",
                        "now",
                        context.getString(R.string.calendar_search_today),
                        context.getString(R.string.calendar_search_keyword_now),
                    ),
                    yesterdayWords = listOf("yesterday", context.getString(R.string.calendar_search_yesterday)),
                    tomorrowWords = listOf("tomorrow", context.getString(R.string.calendar_search_tomorrow)),
                    calendarWords = listOf("calendar", context.getString(R.string.search_pref_result_calendar_title)),
                )
                val temporalEvents = temporal?.let {
                    CalendarEventSource.query(
                        context = context,
                        windowStart = it.start,
                        windowEnd = it.end,
                        now = now,
                        maxResults = maxResults,
                        showCanceled = showCanceled,
                        includeEnded = it.includeEnded,
                        matchWindow = true,
                        includeAllDay = it.includeAllDay,
                    )
                }.orEmpty()
                val literalEvents = CalendarEventSource.query(
                    context = context,
                    query = query,
                    windowStart = now - CALENDAR_SEARCH_LOOKBACK.inWholeMilliseconds,
                    windowEnd = now + CALENDAR_SEARCH_LOOKAHEAD.inWholeMilliseconds,
                    now = now,
                    maxResults = maxResults,
                    showCanceled = showCanceled,
                )
                (temporalEvents + literalEvents)
                    .distinctBy { it.eventId to it.startTime }
                    .take(maxResults)
                    .map { event ->
                        CalendarSearchEvent(
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
        val localStartTime = if (allDay) CalendarSearchEvent.utcMidnightToLocal(startTime) else startTime
        val displayEndTime = if (allDay && endTime > startTime) {
            val lastDay = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                timeInMillis = endTime
                add(Calendar.DAY_OF_MONTH, -1)
            }
            CalendarSearchEvent.utcMidnightToLocal(lastDay.timeInMillis)
        } else if (allDay) {
            CalendarSearchEvent.utcMidnightToLocal(endTime)
        } else {
            endTime
        }
        val startDate = Calendar.getInstance().apply { timeInMillis = localStartTime }
        val endDate = Calendar.getInstance().apply { timeInMillis = displayEndTime }
        val sameYear = startDate.get(Calendar.ERA) == endDate.get(Calendar.ERA) &&
            startDate.get(Calendar.YEAR) == endDate.get(Calendar.YEAR)
        val currentDate = Calendar.getInstance()
        val currentYear = startDate.get(Calendar.ERA) == currentDate.get(Calendar.ERA) &&
            startDate.get(Calendar.YEAR) == currentDate.get(Calendar.YEAR)
        val sameDay = sameYear && startDate.get(Calendar.DAY_OF_YEAR) == endDate.get(Calendar.DAY_OF_YEAR)
        val dateTime = if (sameDay) {
            formatSingleDay(context, localStartTime, allDay)
        } else {
            val flags = DateUtils.FORMAT_SHOW_DATE or
                (if (allDay) 0 else DateUtils.FORMAT_SHOW_TIME) or
                (if (sameYear && currentYear) 0 else DateUtils.FORMAT_SHOW_YEAR)
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

            isRelativeDay(startTime, -1) -> context.getString(R.string.calendar_search_yesterday)

            isRelativeDay(startTime, 1) -> context.getString(R.string.calendar_search_tomorrow)

            else -> {
                val eventYear = Calendar.getInstance().apply { timeInMillis = startTime }.get(Calendar.YEAR)
                val currentYear = Calendar.getInstance().get(Calendar.YEAR)
                DateUtils.formatDateTime(
                    context,
                    startTime,
                    DateUtils.FORMAT_SHOW_DATE or
                        if (eventYear == currentYear) DateUtils.FORMAT_NO_YEAR else DateUtils.FORMAT_SHOW_YEAR,
                )
            }
        }
        val time = if (allDay) {
            context.getString(R.string.calendar_search_all_day)
        } else {
            DateUtils.formatDateTime(context, startTime, DateUtils.FORMAT_SHOW_TIME)
        }
        return context.getString(R.string.calendar_search_date_time, day, time)
    }

    private fun isRelativeDay(timeMillis: Long, offset: Int): Boolean {
        val relativeDay = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, offset)
        }
        val date = Calendar.getInstance().apply {
            timeInMillis = timeMillis
        }
        return relativeDay.get(Calendar.ERA) == date.get(Calendar.ERA) &&
            relativeDay.get(Calendar.YEAR) == date.get(Calendar.YEAR) &&
            relativeDay.get(Calendar.DAY_OF_YEAR) == date.get(Calendar.DAY_OF_YEAR)
    }
}
