package app.lawnchair.util

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.*

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 60 * 60
private const val SECONDS_PER_DAY = 24 * 60 * 60
private const val MILLIS_PER_MINUTE = 1000 * 60

private val Context.locale: Locale?
    get() = resources.configuration.locales[0]

fun formatShortElapsedTime(context: Context, millis: Long): String? {

    var secondsLong = millis / 1000
    var days = 0
    var hours = 0
    var minutes = 0

    listOf(SECONDS_PER_DAY, SECONDS_PER_HOUR, SECONDS_PER_MINUTE).forEach {
        if (secondsLong >= it) {
            days = (secondsLong / it).toInt()
            secondsLong -= (days * it).toLong()
        }
    }

    val seconds = secondsLong.toInt()
    val locale = context.locale
    val measureFormat = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)

    return when {
        days >= 2 || days > 0 && hours == 0 -> {
            days += (hours + 12) / 24
            measureFormat.format(Measure(days, MeasureUnit.DAY))
        }
        days > 0 -> measureFormat.formatMeasures(
            Measure(days, MeasureUnit.DAY),
            Measure(hours, MeasureUnit.HOUR),
        )
        hours >= 2 || hours > 0 && minutes == 0 -> {
            hours += (minutes + 30) / 60
            measureFormat.format(Measure(hours, MeasureUnit.HOUR))
        }
        hours > 0 -> measureFormat.formatMeasures(
            Measure(hours, MeasureUnit.HOUR),
            Measure(minutes, MeasureUnit.MINUTE),
        )
        minutes >= 2 || minutes > 0 && seconds == 0 -> {
            minutes += (seconds + 30) / 60
            measureFormat.format(Measure(minutes, MeasureUnit.MINUTE))
        }
        minutes > 0 -> measureFormat.formatMeasures(
            Measure(minutes, MeasureUnit.MINUTE),
            Measure(seconds, MeasureUnit.SECOND),
        )
        else -> measureFormat.format(Measure(seconds, MeasureUnit.SECOND))
    }
}

fun formatShortElapsedTimeRoundingUpToMinutes(context: Context, millis: Long): String? {
    val minutesRoundedUp: Long = (millis + MILLIS_PER_MINUTE - 1) / MILLIS_PER_MINUTE
    if (minutesRoundedUp == 0L || minutesRoundedUp == 1L) {
        val locale = context.locale
        val measureFormat = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)
        return measureFormat.format(Measure(minutesRoundedUp, MeasureUnit.MINUTE))
    }
    return formatShortElapsedTime(context, minutesRoundedUp * MILLIS_PER_MINUTE)
}
