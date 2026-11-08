package app.lawnchair.search.algorithms.engine.provider

import java.text.DateFormat
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** How far before the current time a search starts */
internal val CALENDAR_SEARCH_LOOKBACK = 1.days

/** How far after the current time a search ends */
internal val CALENDAR_SEARCH_LOOKAHEAD = 366.days

/**
 * A calendar search window and its event filters.
 *
 * @property start Inclusive window start, in epoch milliseconds.
 * @property end Exclusive window end, in epoch milliseconds.
 * @property includeEnded Whether to include events that have already ended.
 * @property includeAllDay Whether to include all-day events.
 */
internal data class CalendarTemporalQuery(
    val start: Long,
    val end: Long,
    val includeEnded: Boolean,
    val includeAllDay: Boolean,
)

internal object CalendarTemporalQueryParser {
    /**
     * Convert calendar search phrase into a time window.
     *
     * Recognises the supplied day and calendar keywords, full weekday names, ISO or localised
     * dates, times, and a date or named day followed by a time from [input] based off selected
     * [locale] and English.
     *
     * Weekday refer to today or the next occurrence, date without a year use the current year.
     * Day searches cover the local day, time search cover two hours, and calendar keywords use the
     * general search range.
     *
     * @param input Search phrase to parse
     * @param now Current time in epoch milliseconds, used to resolve relative dates.
     * @param locale Locale used to recognise weekday names and datetime formats. (This will be combined with the English locale)
     * @param timezone Time timezone used to resolve local datetime.
     * @param todayWords Phrases that represent today.
     * @param yesterdayWords Phrases that represent yesterday.
     * @param tomorrowWords Phrases that represent tomorrow.
     * @param calendarWords Phrases that request the general calendar search range.
     * @return The search window and event filters, or null if the phrase is not recognised.
     */
    fun parse(
        input: String,
        now: Long,
        locale: Locale,
        timezone: ZoneId,
        todayWords: Collection<String>,
        yesterdayWords: Collection<String>,
        tomorrowWords: Collection<String>,
        calendarWords: Collection<String>,
    ): CalendarTemporalQuery? {
        val text = input.trim()
        val today = Instant.ofEpochMilli(now).atZone(timezone).toLocalDate()
        fun day(date: LocalDate, includeEnded: Boolean) = CalendarTemporalQuery(
            date.atStartOfDay(timezone).toInstant().toEpochMilli(),
            date.plusDays(1).atStartOfDay(timezone).toInstant().toEpochMilli(),
            includeEnded,
            true,
        )
        fun matches(value: String, words: Collection<String>) = words.any {
            value.equals(it.trim(), ignoreCase = true)
        }
        fun namedDay(value: String): Pair<LocalDate, Boolean>? {
            if (matches(value, todayWords)) return today to false
            if (matches(value, yesterdayWords)) return today.minusDays(1) to true
            if (matches(value, tomorrowWords)) return today.plusDays(1) to false
            for (weekday in DayOfWeek.values()) {
                val names = listOf(locale, Locale.ENGLISH).flatMap { language ->
                    listOf(
                        weekday.getDisplayName(TextStyle.FULL, language),
                        weekday.getDisplayName(TextStyle.FULL_STANDALONE, language),
                    )
                }
                if (matches(value, names)) {
                    val daysAhead = (weekday.value - today.dayOfWeek.value + 7) % 7
                    return today.plusDays(daysAhead.toLong()) to false
                }
            }
            return null
        }

        namedDay(text)?.let { return day(it.first, it.second) }
        if (matches(text, calendarWords)) {
            return CalendarTemporalQuery(
                now - CALENDAR_SEARCH_LOOKBACK.inWholeMilliseconds,
                now + CALENDAR_SEARCH_LOOKAHEAD.inWholeMilliseconds,
                false,
                true,
            )
        }

        parseDate(text, today, locale)?.let { return day(it, true) }
        parseTime(text, locale)?.let { return timeWindow(today, it, timezone) }

        // Try each separator so dates containing spaces remain valid.
        text.indices.reversed().filter { text[it].isWhitespace() || text[it] == 'T' }.forEach { index ->
            val dateText = text.substring(0, index).trim()
            val date = namedDay(dateText)?.first ?: parseDate(dateText, today, locale)
            val time = parseTime(text.substring(index + 1).trim(), locale)
            if (date != null && time != null) return timeWindow(date, time, timezone)
        }
        return null
    }

    private fun timeWindow(date: LocalDate, time: LocalTime, zone: ZoneId): CalendarTemporalQuery {
        val start = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        return CalendarTemporalQuery(start, start + TIME_SEARCH_WINDOW.inWholeMilliseconds, true, false)
    }

    private fun parseDate(text: String, today: LocalDate, locale: Locale): LocalDate? {
        try {
            return LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (_: DateTimeParseException) {
            // Try locale-specific formats below.
        }
        for (style in listOf(DateFormat.SHORT, DateFormat.MEDIUM, DateFormat.LONG)) {
            val localized = DateFormat.getDateInstance(style, locale) as? SimpleDateFormat ?: continue
            parseStrict(text, localized, locale)?.let { return it.toInstant().atZone(UTC).toLocalDate() }

            val pattern = localized.toPattern()
            val withoutYear = pattern
                .replace(Regex("^y+[^A-Za-z]*"), "")
                .replace(Regex("[^A-Za-z]*y+$"), "")
            if (withoutYear != pattern && withoutYear.isNotBlank()) {
                val format = SimpleDateFormat("$withoutYear y", locale)
                parseStrict("$text ${today.year}", format, locale)?.let { return it.toInstant().atZone(UTC).toLocalDate() }
            }
        }
        return null
    }

    private fun parseTime(text: String, locale: Locale): LocalTime? {
        val formats = listOf(locale, Locale.ENGLISH).distinct().flatMap { language ->
            listOf("H:mm", "h:mm a", "h a").map { SimpleDateFormat(it, language) to language }
        }
        for ((format, language) in formats) {
            val parsed = parseStrict(text, format, language) ?: continue
            return parsed.toInstant().atZone(UTC).toLocalTime()
        }
        return null
    }

    private fun parseStrict(text: String, format: SimpleDateFormat, locale: Locale): Date? {
        format.calendar = GregorianCalendar(TimeZone.getTimeZone(UTC), locale)
        format.isLenient = false
        val position = ParsePosition(0)
        val parsed = format.parse(text, position)
        return parsed?.takeIf { position.index == text.length }
    }

    /** How far before or after the current time before the event is excluded from the result  */
    private val TIME_SEARCH_WINDOW = 2.hours
    private val UTC: ZoneId = ZoneId.of("UTC")
}
