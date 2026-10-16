package app.lawnchair.smartspace

import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Locale

/** Immutable formatting inputs; no file access and no calendar/network service. */
data class CalendarDateOptions(
    val style: String = "long",
    val language: String = "system",
    val digits: String = "system",
    val method: String = "islamic-umalqura",
    val offset: Int = 0,
    val year: Boolean = false,
    val weekday: Boolean = true,
    val era: Boolean = false,
    val hijriFirst: Boolean = true,
    val stacked: Boolean = true,
)

/** Cached per view/options/zone. Each mutable ICU formatter belongs to this instance's UI thread. */
class CalendarDateFormatter(
    private val mode: String,
    private val options: CalendarDateOptions,
    private val showDate: Boolean,
    private val showTime: Boolean,
    twentyFourHour: Boolean,
    locale: Locale = Locale.getDefault(),
    zoneId: String = java.util.TimeZone.getDefault().id,
) {
    private val zone = TimeZone.getTimeZone(zoneId)
    private val base = when (options.language) {
        "ar" -> ULocale("ar")
        "en" -> ULocale("en")
        else -> ULocale.forLocale(locale)
    }
    private fun localeFor(calendar: String): ULocale {
        var value = base.setKeywordValue("calendar", calendar)
        if (options.digits in setOf("arab", "latn")) value = value.setKeywordValue("numbers", options.digits)
        return value
    }
    private val dateSkeleton = buildString {
        if (options.weekday) append("EEE")
        append(
            when (options.style) {
                "numeric" -> "Md"
                "short" -> "MMMd"
                else -> "MMMMd"
            },
        )
        if (options.year) append('y')
        if (options.era) append('G')
    }
    private fun formatter(calendar: String, skeleton: String): DateFormat = DateFormat.getInstanceForSkeleton(skeleton, localeFor(calendar)).apply {
        timeZone = zone
        setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
    }
    private val method = options.method.takeIf { it in setOf("islamic-umalqura", "islamic-civil", "islamic") } ?: "islamic-umalqura"
    private val gregorian = formatter("gregorian", dateSkeleton)
    private val hijri = formatter(method, dateSkeleton)
    private val weekday = formatter("gregorian", "EEE")
    private val clock = formatter("gregorian", if (twentyFourHour) "Hm" else "hm")
    private val adjustment = Calendar.getInstance(zone, localeFor(method))

    // Weekday represents today even when the locally observed Hijri date has an adjustment.
    private val shiftedHijri = if (options.weekday && options.offset != 0) formatter(method, dateSkeleton.removePrefix("EEE")) else hijri

    fun format(millis: Long): String {
        val parts = mutableListOf<String>()
        if (showTime) parts += isolate(clock.format(millis))
        if (showDate) {
            val gregorianText = isolate(gregorian.format(millis))
            adjustment.timeInMillis = millis
            adjustment.add(Calendar.DATE, options.offset.coerceIn(-2, 2))
            val hijriDate = shiftedHijri.format(adjustment.timeInMillis)
            val hijriText = isolate(if (options.weekday && options.offset != 0) "${weekday.format(millis)}، $hijriDate" else hijriDate)
            parts += when (mode) {
                "hijri" -> hijriText

                "dual" -> listOf(hijriText, gregorianText).let { dates ->
                    (if (options.hijriFirst) dates else dates.reversed()).joinToString(if (options.stacked) "\n" else " · ")
                }

                else -> gregorianText
            }
        }
        return parts.joinToString(" · ")
    }

    // Isolate mixed Arabic/Latin calendar fragments in either layout direction.
    private fun isolate(value: String) = "\u2068$value\u2069"
}
