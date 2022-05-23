package app.lawnchair.util

import android.content.Context
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

private const val MILLIS_PER_MINUTE = 1000 * 60

private val Context.locale: Locale?
    get() = resources.configuration.locales[0]

fun formatShortElapsedTime(context: Context, millis: Long): String? {

    val duration = millis.milliseconds
    val locale = context.locale
    val measureFormat = MeasureFormat.getInstance(locale, MeasureFormat.FormatWidth.SHORT)

    return duration.toComponents { days, hours, minutes, seconds, _ ->
        when {
            days >= 2 || days > 0 && hours == 0 -> {
                val roundedDays = duration.toDouble(DurationUnit.DAYS).round()
                measureFormat.format(Measure(roundedDays, MeasureUnit.DAY))
            }
            days >= 2 || days > 0 && hours == 0 -> {
                val roundedDays = duration.toDouble(DurationUnit.DAYS).round()
                measureFormat.format(Measure(roundedDays, MeasureUnit.DAY))
            }
            days > 0 -> measureFormat.formatMeasures(
                Measure(days, MeasureUnit.DAY),
                Measure(hours, MeasureUnit.HOUR),
            )
            hours >= 2 || hours > 0 && minutes == 0 -> {
                val roundedHours = duration.toDouble(DurationUnit.HOURS).round()
                measureFormat.format(Measure(roundedHours, MeasureUnit.HOUR))
            }
            hours > 0 -> measureFormat.formatMeasures(
                Measure(hours, MeasureUnit.HOUR),
                Measure(minutes, MeasureUnit.MINUTE),
            )
            minutes >= 2 || minutes > 0 && seconds == 0 -> {
                val roundedMinutes = duration.toDouble(DurationUnit.MINUTES).round()
                measureFormat.format(Measure(roundedMinutes, MeasureUnit.MINUTE))
            }
            minutes > 0 -> measureFormat.formatMeasures(
                Measure(minutes, MeasureUnit.MINUTE),
                Measure(seconds, MeasureUnit.SECOND),
            )
            else -> measureFormat.format(Measure(seconds, MeasureUnit.SECOND))
        }
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
