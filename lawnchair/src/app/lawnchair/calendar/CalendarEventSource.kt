package app.lawnchair.calendar

import android.content.Context
import android.os.CancellationSignal
import android.os.OperationCanceledException
import android.provider.CalendarContract
import android.util.Log
import java.time.Duration
import java.time.format.DateTimeParseException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

// TODO: Integrate this with Smartspace Calendar provider
object CalendarEventSource {
    /**
     * Query calendar events
     *
     * To use this you need [android.Manifest.permission.READ_CALENDAR] permission.
     *
     * @return Events based on your [query] (or all events if null) sorted by start time,
     * distinct by event id to start time, limited to [maxResults].
     * With [matchWindow], events must overlap the half-open local time window.
     * [includeEnded] includes occurrences that ended before [now].
     * @throws IllegalArgumentException if [windowStart] exceeds [windowEnd] or [maxResults] is
     * a negative number
     */
    suspend fun query(
        context: Context,
        windowStart: Long,
        windowEnd: Long,
        now: Long,
        maxResults: Int,
        showCanceled: Boolean = false,
        query: String? = null,
        includeEnded: Boolean = false,
        matchWindow: Boolean = false,
        includeAllDay: Boolean = true,
    ): List<CalendarEvent> = withContext(Dispatchers.IO) {
        require(windowStart <= windowEnd) { "Calendar window start must not exceed its end" }
        require(maxResults >= 0) { "Calendar max results must not be negative number" }
        if (maxResults == 0) return@withContext emptyList()

        // All-day instance dates are stored in UTC, so include a margin before local filtering.
        val providerStart = if (matchWindow) windowStart - ALL_DAY_MARGIN.inWholeMilliseconds else windowStart
        val providerEnd = if (matchWindow) windowEnd + ALL_DAY_MARGIN.inWholeMilliseconds else windowEnd
        val events = queryInstances(context, query, now, providerStart, providerEnd, includeEnded) +
            if (showCanceled) {
                // Combining cancelled events with normal events into one function will make the
                // code needlessly more complicated than it alright is :(
                try {
                    queryCanceledEvents(context, query, now, providerStart, providerEnd, includeEnded, matchWindow)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("CalendarEventSource", "Failed to query canceled calendar events", e)
                    emptyList()
                }
            } else {
                emptyList()
            }
        events.asSequence()
            .filter { includeAllDay || !it.allDay }
            .filter { !matchWindow || it.overlaps(windowStart, windowEnd) }
            .sortedBy(CalendarEvent::localStartTime)
            .distinctBy { it.eventId to it.startTime }
            .take(maxResults)
            .toList()
    }

    private suspend fun queryInstances(
        context: Context,
        query: String?,
        now: Long,
        windowStart: Long,
        windowEnd: Long,
        includeEnded: Boolean,
    ): List<CalendarEvent> {
        val instancesUri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(windowStart.toString())
            .appendPath(windowEnd.toString())
            .build()
        val searchFilter = textSearchFilter(
            query,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.EVENT_LOCATION,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "(${CalendarContract.Instances.STATUS} IS NULL OR " +
            "${CalendarContract.Instances.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}) AND " +
            "(${CalendarContract.Instances.SELF_ATTENDEE_STATUS} IS NULL OR " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED})" +
            searchFilter.selection
        val selectionArgs = searchFilter.selectionArgs
            .takeIf { it.isNotEmpty() }
            ?.toTypedArray()
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

        val coroutineContext = currentCoroutineContext()
        return withQueryCancellation { signal ->
            (
                context.contentResolver.query(
                    instancesUri,
                    projection,
                    selection,
                    selectionArgs,
                    "${CalendarContract.Instances.BEGIN} ASC",
                    signal,
                ) ?: error("Calendar instances query returned no cursor")
                ).use { cursor ->
                val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
                val locationIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                val colorIndex = cursor.getColumnIndex(CalendarContract.Instances.DISPLAY_COLOR)

                buildList {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        val title = cursor.getString(titleIndex)?.trim().orEmpty()
                        if (title.isBlank()) continue

                        val allDay = cursor.getInt(allDayIndex) != 0
                        val startTime = cursor.getLong(beginIndex)
                        val endTime = cursor.getLong(endIndex)
                        val localEndTime = if (allDay) CalendarEvent.utcMidnightToLocal(endTime) else endTime
                        if (!includeEnded && localEndTime <= now) continue

                        val location = locationIndex.takeIf { it >= 0 }?.let(cursor::getString)
                        add(
                            CalendarEvent(
                                eventId = cursor.getLong(eventIdIndex),
                                title = title,
                                location = location,
                                calendarColor = colorIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt),
                                startTime = startTime,
                                endTime = endTime,
                                allDay = allDay,
                                isCanceled = false,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun queryCanceledEvents(
        context: Context,
        query: String?,
        now: Long,
        windowStart: Long,
        windowEnd: Long,
        includeEnded: Boolean,
        matchWindow: Boolean,
    ): List<CalendarEvent> {
        val visibleCalendarIds = queryVisibleCalendarIds(context)
        if (visibleCalendarIds.isEmpty()) return emptyList()

        val effectiveStartTime = "CASE WHEN ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} IS NOT NULL " +
            "AND ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} != 0 " +
            "THEN ${CalendarContract.Events.ORIGINAL_INSTANCE_TIME} ELSE ${CalendarContract.Events.DTSTART} END"
        val windowSelection = if (matchWindow) {
            "$effectiveStartTime <= ? AND " +
                "(${CalendarContract.Events.DTEND} IS NULL OR " +
                "${CalendarContract.Events.DTEND} < ${CalendarContract.Events.DTSTART} OR " +
                "$effectiveStartTime + (${CalendarContract.Events.DTEND} - ${CalendarContract.Events.DTSTART}) >= ?)"
        } else {
            "$effectiveStartTime >= ? AND $effectiveStartTime <= ?"
        }
        val selection = "${CalendarContract.Events.STATUS} = ? AND " +
            "(${CalendarContract.Events.SELF_ATTENDEE_STATUS} IS NULL OR " +
            "${CalendarContract.Events.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}) AND " +
            windowSelection
        val searchFilter = textSearchFilter(
            query,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
        )
        val selectionArgs = mutableListOf(CalendarContract.Events.STATUS_CANCELED.toString())
        if (!matchWindow) selectionArgs.add(windowStart.toString())
        selectionArgs.add(windowEnd.toString())
        if (matchWindow) selectionArgs.add(windowStart.toString())
        selectionArgs.addAll(searchFilter.selectionArgs)
        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DISPLAY_COLOR,
            CalendarContract.Events.ORIGINAL_INSTANCE_TIME,
            CalendarContract.Events.ORIGINAL_ALL_DAY,
        )

        val coroutineContext = currentCoroutineContext()
        return withQueryCancellation { signal ->
            (
                context.contentResolver.query(
                    CalendarContract.Events.CONTENT_URI,
                    projection,
                    selection + searchFilter.selection,
                    selectionArgs.toTypedArray(),
                    "${CalendarContract.Events.DTSTART} ASC",
                    signal,
                ) ?: error("Canceled calendar events query returned no cursor")
                ).use { cursor ->
                val eventIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events._ID)
                val calendarIdIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
                val durationIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.DURATION)
                val allDayIndex = cursor.getColumnIndexOrThrow(CalendarContract.Events.ALL_DAY)
                val locationIndex = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
                val colorIndex = cursor.getColumnIndex(CalendarContract.Events.DISPLAY_COLOR)
                val originalStartIndex = cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_INSTANCE_TIME)
                val originalAllDayIndex = cursor.getColumnIndex(CalendarContract.Events.ORIGINAL_ALL_DAY)

                buildList {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        if (cursor.getLong(calendarIdIndex) !in visibleCalendarIds) continue

                        val title = cursor.getString(titleIndex)?.trim().orEmpty()
                        if (title.isBlank()) continue
                        if (cursor.isNull(beginIndex)) {
                            Log.w("CalendarEventSource", "Skipping canceled event with no start time")
                            continue
                        }

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
                        val duration = resolveDuration(
                            eventStartTime,
                            endIndex.takeUnless(cursor::isNull)?.let(cursor::getLong),
                            durationIndex.takeUnless(cursor::isNull)?.let(cursor::getString),
                        )
                        val endTime = try {
                            duration?.let { Math.addExact(startTime, it) }
                        } catch (e: ArithmeticException) {
                            // What??????? This will never happen, how do you overflow a long.
                            Log.e("CalendarEventSource", "Something related to arithmetic overflowed a long!", e)
                            null
                        }
                        if (endTime == null) {
                            Log.w("CalendarEventSource", "Skipping canceled event with invalid end or duration")
                            continue
                        }
                        val localEndTime = if (allDay) CalendarEvent.utcMidnightToLocal(endTime) else endTime
                        if (!includeEnded && localEndTime <= now) continue

                        val location = locationIndex.takeIf { it >= 0 }?.let(cursor::getString)
                        add(
                            CalendarEvent(
                                eventId = cursor.getLong(eventIdIndex),
                                title = title,
                                location = location,
                                calendarColor = colorIndex.takeIf { it >= 0 && !cursor.isNull(it) }?.let(cursor::getInt),
                                startTime = startTime,
                                endTime = endTime,
                                allDay = allDay,
                                isCanceled = true,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun queryVisibleCalendarIds(context: Context): Set<Long> {
        val coroutineContext = currentCoroutineContext()
        return withQueryCancellation { signal ->
            (
                context.contentResolver.query(
                    CalendarContract.Calendars.CONTENT_URI,
                    arrayOf(CalendarContract.Calendars._ID),
                    "${CalendarContract.Calendars.VISIBLE} = 1",
                    null,
                    null,
                    signal,
                ) ?: error("Visible calendars query returned no cursor")
                ).use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                buildSet {
                    while (cursor.moveToNext()) {
                        coroutineContext.ensureActive()
                        add(cursor.getLong(idIndex))
                    }
                }
            }
        }
    }

    private suspend fun <T> withQueryCancellation(block: (CancellationSignal) -> T): T = coroutineScope {
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            launch {
                try {
                    coroutineContext.ensureActive()
                    continuation.resume(block(signal))
                } catch (e: OperationCanceledException) {
                    val cancellation = CancellationException("Calendar provider query canceled")
                    cancellation.initCause(e)
                    continuation.cancel(cancellation)
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }
        }
    }

    private data class TextSearchFilter(
        val selection: String,
        val selectionArgs: List<String>,
    )

    private val ALL_DAY_MARGIN = 2.days

    private fun resolveDuration(start: Long, end: Long?, duration: String?): Long? {
        if (end != null && end >= start) {
            try {
                return Math.subtractExact(end, start)
            } catch (e: ArithmeticException) {
                // What??????? This will never happen, how do you overflow a long. Anyway.
                // Try the duration field if the interval is not representable.
                Log.e("CalendarEventSource", "Failed to subtract end time from start time for canceled event $end-$start", e)
            }
        }
        if (duration == null) return null
        return try {
            val normalized = duration.replace(Regex("^([+-]?)P(\\d+)W$"), "$1P$2D")
            val weeks = duration.matches(Regex("^[+-]?P\\d+W$"))
            val parsed = Duration.parse(normalized)
            val millis = parsed.toMillis()
            val result = if (weeks) Math.multiplyExact(millis, 7) else millis
            result.takeIf { it >= 0 }
        } catch (e: DateTimeParseException) {
            // What???????
            Log.e("CalendarEventSource", "Failed to parse text to duration $duration", e)
            null
        } catch (e: ArithmeticException) {
            // What??????? This will never happen, how do you overflow a long.
            Log.e("CalendarEventSource", "Something related to arithmetic overflowed a long!", e)
            null
        }
    }

    private fun textSearchFilter(
        query: String?,
        titleColumn: String,
        descriptionColumn: String,
        locationColumn: String,
    ): TextSearchFilter {
        if (query == null) return TextSearchFilter("", emptyList())

        val pattern = "%${escapeLike(query)}%"
        return TextSearchFilter(
            selection = " AND ($titleColumn LIKE ? ESCAPE '\\' OR " +
                "$descriptionColumn LIKE ? ESCAPE '\\' OR " +
                "$locationColumn LIKE ? ESCAPE '\\')",
            selectionArgs = listOf(pattern, pattern, pattern),
        )
    }

    /** Escapes LIKE wildcards so `%` and `_` in the query match literally. Pair with ESCAPE '\'. */
    private fun escapeLike(query: String): String = query
        .replace("\\", "\\\\")
        .replace("%", "\\%")
        .replace("_", "\\_")
}
