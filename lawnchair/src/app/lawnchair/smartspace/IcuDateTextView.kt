package app.lawnchair.smartspace

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import android.text.format.DateFormat.is24HourFormat
import android.util.AttributeSet
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.util.broadcastReceiverFlow
import app.lawnchair.util.repeatOnAttached
import com.android.launcher3.R
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat

typealias FormatterFunction = (Long) -> String

class IcuDateTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : DoubleShadowTextView(context, attrs) {

    private val prefs = PreferenceManager2.getInstance(context)
    private var calendar: SmartspaceCalendar? = null
    private var dateTimeOptions = DateTimeOptions(true, false, SmartspaceTimeFormat.FollowSystem)
    private var appearance = CalendarDateOptions()
    private var formatterFunction: FormatterFunction? = null
    private val ticker = this::onTimeTick

    init {
        repeatOnAttached {
            combine(prefs.smartspaceCalendar.get(), DateTimeOptions.fromPrefs(prefs), calendarDateOptions(prefs)) { selected, options, style ->
                Triple(selected, options, style)
            }.onEach { (selected, options, style) ->
                calendar = selected
                dateTimeOptions = options
                appearance = style
                maxLines = if (selected == SmartspaceCalendar.Dual && options.showDate && style.stacked) 2 else 1
                onTimeChanged(true)
            }.launchIn(this)

            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_TIME_CHANGED)
            intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
            intentFilter.addAction(Intent.ACTION_LOCALE_CHANGED)
            intentFilter.addAction(Intent.ACTION_DATE_CHANGED)
            broadcastReceiverFlow(context, intentFilter)
                .onEach { onTimeChanged(it.action != Intent.ACTION_TIME_TICK) }
                .launchIn(this)
        }
    }

    private fun onTimeChanged(updateFormatter: Boolean) {
        if (isShown) {
            val timeText = getTimeText(updateFormatter)
            if (text != timeText) {
                textAlignment = if (shouldAlignToTextEnd()) TEXT_ALIGNMENT_TEXT_END else TEXT_ALIGNMENT_TEXT_START
                text = timeText
                contentDescription = timeText
            }
        } else if (updateFormatter) {
            formatterFunction = null
        }
    }

    private fun shouldAlignToTextEnd(): Boolean {
        val is24HourFormatManual = dateTimeOptions.timeFormat is SmartspaceTimeFormat.TwentyFourHourFormat
        val is24HourFormatOnSystem = dateTimeOptions.timeFormat is SmartspaceTimeFormat.FollowSystem && is24HourFormat(context)
        val is24HourFormat = is24HourFormatManual || is24HourFormatOnSystem
        val shouldNotAlignToEnd = dateTimeOptions.showTime && is24HourFormat && !dateTimeOptions.showDate
        return calendar == SmartspaceCalendar.Persian && !shouldNotAlignToEnd
    }

    private fun getTimeText(updateFormatter: Boolean): String {
        val formatter = getFormatterFunction(updateFormatter)
        return formatter(System.currentTimeMillis())
    }

    private fun getFormatterFunction(updateFormatter: Boolean): FormatterFunction {
        if (formatterFunction != null && !updateFormatter) {
            return formatterFunction!!
        }
        val formatter = when (calendar) {
            SmartspaceCalendar.Persian -> createPersianFormatter()
            else -> createGregorianFormatter()
        }
        formatterFunction = formatter
        return formatter
    }

    private fun createPersianFormatter(): FormatterFunction {
        var format: String
        if (dateTimeOptions.showTime) {
            format = context.getString(
                when {
                    dateTimeOptions.timeFormat is SmartspaceTimeFormat.TwelveHourFormat -> R.string.smartspace_icu_date_pattern_persian_time_12h
                    dateTimeOptions.timeFormat is SmartspaceTimeFormat.TwentyFourHourFormat -> R.string.smartspace_icu_date_pattern_persian_time
                    is24HourFormat(context) -> R.string.smartspace_icu_date_pattern_persian_time
                    else -> R.string.smartspace_icu_date_pattern_persian_time_12h
                },
            )
            if (dateTimeOptions.showDate) format = context.getString(R.string.smartspace_icu_date_pattern_persian_date) + format
        } else {
            format = context.getString(R.string.smartspace_icu_date_pattern_persian_wday_month_day_no_year)
        }
        val formatter = PersianDateFormat(format, PersianDateFormat.PersianDateNumberCharacter.FARSI)
        return { formatter.format(PersianDate(it)) }
    }

    private fun createGregorianFormatter(): FormatterFunction {
        val formatter = CalendarDateFormatter(
            mode = calendar?.toString() ?: "gregorian",
            options = appearance,
            showDate = dateTimeOptions.showDate,
            showTime = dateTimeOptions.showTime,
            twentyFourHour = when (dateTimeOptions.timeFormat) {
                SmartspaceTimeFormat.TwelveHourFormat -> false
                SmartspaceTimeFormat.TwentyFourHourFormat -> true
                else -> is24HourFormat(context)
            },
        )
        return formatter::format
    }

    private fun onTimeTick() {
        onTimeChanged(false)
        val uptimeMillis: Long = SystemClock.uptimeMillis()
        val interval = 60_000L
        val delay = interval - System.currentTimeMillis() % interval
        handler?.postAtTime(ticker, uptimeMillis + delay)
    }

    override fun onDetachedFromWindow() {
        handler?.removeCallbacks(ticker)
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        handler?.removeCallbacks(ticker)
        if (isVisible) {
            ticker()
        }
    }
}

data class DateTimeOptions(
    val showDate: Boolean,
    val showTime: Boolean,
    val timeFormat: SmartspaceTimeFormat,
) {
    companion object {
        fun fromPrefs(prefs: PreferenceManager2) = combine(
            prefs.smartspaceShowDate.get(),
            prefs.smartspaceShowTime.get(),
            prefs.smartspaceTimeFormat.get(),
        ) { showDate, showTime, timeFormat ->
            DateTimeOptions(showDate, showTime, timeFormat)
        }
    }
}

internal fun calendarDateOptions(prefs: PreferenceManager2) = combine(
    combine(prefs.smartspaceDateStyle.get(), prefs.smartspaceDateLanguage.get(), prefs.smartspaceDateDigits.get(), prefs.smartspaceHijriMethod.get(), prefs.smartspaceHijriOffset.get()) { style, language, digits, method, offset ->
        CalendarDateOptions(style, language, digits, method, offset)
    },
    combine(prefs.smartspaceDateYear.get(), prefs.smartspaceDateWeekday.get(), prefs.smartspaceDateEra.get(), prefs.smartspaceHijriFirst.get(), prefs.smartspaceDateStacked.get()) { year, weekday, era, first, stacked ->
        CalendarDateOptions(year = year, weekday = weekday, era = era, hijriFirst = first, stacked = stacked)
    },
) { date, display -> date.copy(year = display.year, weekday = display.weekday, era = display.era, hijriFirst = display.hijriFirst, stacked = display.stacked) }
