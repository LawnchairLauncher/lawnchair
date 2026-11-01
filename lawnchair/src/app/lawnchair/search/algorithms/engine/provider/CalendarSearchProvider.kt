package app.lawnchair.search.algorithms.engine.provider

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.format.DateUtils
import android.util.Log
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.firstCached
import app.lawnchair.search.algorithms.data.CalendarEvent
import app.lawnchair.search.algorithms.engine.SearchPermission
import app.lawnchair.search.algorithms.engine.SearchProvider
import app.lawnchair.search.algorithms.engine.SearchResult
import com.android.launcher3.R
import java.util.Calendar
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
            findEvents(
                context = context,
                query = query,
                maxResults = maxResults,
                showCanceled = prefs2.showCalendarCanceledEvents.firstCached(),
            )
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

    private suspend fun findEvents(
        context: Context,
        query: String,
        maxResults: Int,
        showCanceled: Boolean,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        if (maxResults <= 0) return@withContext emptyList()

        val now = System.currentTimeMillis()
        // The window starts a day back so that all-day events, which are stored in UTC, still
        // overlap it in every time zone. Rows that already ended locally are skipped below.
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath((now - ONE_DAY_MILLIS).toString())
            .appendPath((now + ONE_YEAR_MILLIS).toString())
            .build()
        val canceledFilter = if (showCanceled) {
            ""
        } else {
            "(${CalendarContract.Instances.STATUS} IS NULL OR " +
                "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}) AND "
        }
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            canceledFilter +
            "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}) AND " +
            "(${CalendarContract.Instances.TITLE} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Instances.DESCRIPTION} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Instances.EVENT_LOCATION} LIKE ? ESCAPE '\\')"
        val pattern = "%${escapeLike(query)}%"
        val selectionArgs = arrayOf(pattern, pattern, pattern)
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            // The event's own colour if it has one, otherwise its calendar's colour.
            CalendarContract.Instances.DISPLAY_COLOR,
        )

        context.contentResolver.query(
            instancesUri,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            val eventIdIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Instances.TITLE)
            val beginIndex = cursor.getColumnIndex(CalendarContract.Instances.BEGIN)
            val endIndex = cursor.getColumnIndex(CalendarContract.Instances.END)
            val allDayIndex = cursor.getColumnIndex(CalendarContract.Instances.ALL_DAY)
            val locationIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
            val colorIndex = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)

            if (eventIdIndex < 0 || titleIndex < 0 || beginIndex < 0 || endIndex < 0 || allDayIndex < 0) {
                return@withContext emptyList()
            }

            // Instances has one row per occurrence. Rows are sorted by BEGIN, so the first row
            // kept for each event is its ongoing or next occurrence.
            val seenEventIds = mutableSetOf<Long>()
            buildList {
                while (cursor.moveToNext() && size < maxResults) {
                    val title = cursor.getString(titleIndex)?.trim().orEmpty()
                    if (title.isBlank()) continue

                    val allDay = cursor.getInt(allDayIndex) != 0
                    val endTime = cursor.getLong(endIndex)
                    val localEndTime = if (allDay) CalendarEvent.utcMidnightToLocal(endTime) else endTime
                    if (localEndTime <= now) continue

                    val eventId = cursor.getLong(eventIdIndex)
                    if (!seenEventIds.add(eventId)) continue

                    val startTime = cursor.getLong(beginIndex)
                    val localStartTime = if (allDay) CalendarEvent.utcMidnightToLocal(startTime) else startTime
                    val location = locationIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    add(
                        CalendarEvent(
                            eventId = eventId,
                            title = title,
                            calendarColor = colorIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt),
                            description = formatDescription(
                                context = context,
                                startTime = localStartTime,
                                allDay = allDay,
                                location = location,
                            ),
                            startTime = startTime,
                            endTime = endTime,
                            allDay = allDay,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    private fun formatDescription(
        context: Context,
        startTime: Long,
        allDay: Boolean,
        location: String?,
    ): String {
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
        val dateTime = context.getString(R.string.calendar_search_date_time, day, time)
        return listOf(dateTime, location?.trim().orEmpty())
            .filter(String::isNotBlank)
            .joinToString(" \u2022 ")
    }

    /** Escapes LIKE wildcards so `%` and `_` in the query match literally. Pair with ESCAPE '\'. */
    private fun escapeLike(query: String): String = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")

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
