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
        val windowStart = now - ONE_DAY_MILLIS
        val windowEnd = now + ONE_YEAR_MILLIS
        val events = queryInstances(
            context = context,
            query = query,
            now = now,
            windowStart = windowStart,
            windowEnd = windowEnd,
        ) + if (showCanceled) {
            queryCanceledEvents(
                context = context,
                query = query,
                now = now,
                windowStart = windowStart,
                windowEnd = windowEnd,
            )
        } else {
            emptyList()
        }

        events
            .sortedBy(CalendarEvent::localStartTime)
            .distinctBy(CalendarEvent::eventId)
            .take(maxResults)
    }

    private fun queryInstances(
        context: Context,
        query: String,
        now: Long,
        windowStart: Long,
        windowEnd: Long,
    ): List<CalendarEvent> {
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(windowStart.toString())
            .appendPath(windowEnd.toString())
            .build()
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "(${CalendarContract.Instances.STATUS} IS NULL OR " +
            "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}) AND " +
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

        return context.contentResolver.query(
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
                return@use emptyList()
            }

            buildList {
                while (cursor.moveToNext()) {
                    val title = cursor.getString(titleIndex)?.trim().orEmpty()
                    if (title.isBlank()) continue

                    val allDay = cursor.getInt(allDayIndex) != 0
                    val startTime = cursor.getLong(beginIndex)
                    val endTime = cursor.getLong(endIndex)
                    val localEndTime = if (allDay) CalendarEvent.utcMidnightToLocal(endTime) else endTime
                    if (localEndTime <= now) continue

                    val localStartTime = if (allDay) CalendarEvent.utcMidnightToLocal(startTime) else startTime
                    val location = locationIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    add(
                        CalendarEvent(
                            eventId = cursor.getLong(eventIdIndex),
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

    private fun queryCanceledEvents(
        context: Context,
        query: String,
        now: Long,
        windowStart: Long,
        windowEnd: Long,
    ): List<CalendarEvent> {
        val visibleCalendarIds = queryVisibleCalendarIds(context)
        if (visibleCalendarIds.isEmpty()) return emptyList()

        val effectiveStartTime = "CASE WHEN ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} IS NOT NULL " +
            "AND ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} != 0 " +
            "THEN ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} ELSE ${CalendarContract.Events.DTSTART} END"
        val pattern = "%${escapeLike(query)}%"
        val selection = "${CalendarContract.Events.STATUS} = ? AND " +
            "(${CalendarContract.Events.SELF_ATTENDEE_STATUS} IS NULL OR " +
            "${CalendarContract.Events.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}) AND " +
            "$effectiveStartTime >= ? AND $effectiveStartTime <= ? AND " +
            "(${CalendarContract.Events.TITLE} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Events.DESCRIPTION} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Events.EVENT_LOCATION} LIKE ? ESCAPE '\\')"
        val selectionArgs = arrayOf(
            CalendarContract.Events.STATUS_CANCELED.toString(),
            windowStart.toString(),
            windowEnd.toString(),
            pattern,
            pattern,
            pattern,
        )
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.ORIGINAL_ALL_DAY,
        )

        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            val eventIdIndex = cursor.getColumnIndex(CalendarContract.Events._ID)
            val calendarIdIndex = cursor.getColumnIndex(CalendarContract.Events.CALENDAR_ID)
            val titleIndex = cursor.getColumnIndex(CalendarContract.Events.TITLE)
            val beginIndex = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
            val endIndex = cursor.getColumnIndex(CalendarContract.Events.DTEND)
            val allDayIndex = cursor.getColumnIndex(CalendarContract.Events.ALL_DAY)
            val locationIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
            val colorIndex = cursor.getColumnIndex(CalendarContract.Events.DISPLAY_COLOR)
            val originalStartIndex = cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME)
            val originalAllDayIndex = cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_ALL_DAY)

            if (eventIdIndex < 0 || calendarIdIndex < 0 || titleIndex < 0 ||
                beginIndex < 0 || endIndex < 0 || allDayIndex < 0
            ) {
                return@use emptyList()
            }

            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.getLong(calendarIdIndex) !in visibleCalendarIds) continue

                    val title = cursor.getString(titleIndex)?.trim().orEmpty()
                    if (title.isBlank()) continue

                    val originalStartTime = originalStartIndex
                        .takeIf { it >= 0 && !cursor.isNull(it) }
                        ?.let(cursor::getLong)
                        ?.takeIf { it != 0L }
                    val eventStartTime = cursor.getLong(beginIndex)
                    val startTime = originalStartTime ?: eventStartTime
                    val allDay = if (
                        originalStartTime != null &&
                        originalAllDayIndex >= 0 &&
                        !cursor.isNull(originalAllDayIndex)
                    ) {
                        cursor.getInt(originalAllDayIndex) != 0
                    } else {
                        cursor.getInt(allDayIndex) != 0
                    }
                    val eventEndTime = cursor.getLong(endIndex)
                    val endTime = if (originalStartTime != null) {
                        startTime + (eventEndTime - eventStartTime).coerceAtLeast(0L)
                    } else {
                        eventEndTime
                    }
                    val localEndTime = if (allDay) CalendarEvent.utcMidnightToLocal(endTime) else endTime
                    if (localEndTime <= now) continue

                    val localStartTime = if (allDay) CalendarEvent.utcMidnightToLocal(startTime) else startTime
                    val location = locationIndex.takeIf { it >= 0 }?.let(cursor::getString)
                    add(
                        CalendarEvent(
                            eventId = cursor.getLong(eventIdIndex),
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

    private fun queryVisibleCalendarIds(context: Context): Set<Long> =
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            arrayOf(CalendarContract.Calendars._ID),
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            null,
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(CalendarContract.Calendars._ID)
            if (idIndex < 0) {
                emptySet()
            } else {
                buildSet {
                    while (cursor.moveToNext()) {
                        add(cursor.getLong(idIndex))
                    }
                }
            }
        } ?: emptySet()

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
