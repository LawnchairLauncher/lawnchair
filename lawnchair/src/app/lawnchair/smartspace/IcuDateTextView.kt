package app.lawnchair.smartspace

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.os.SystemClock
import android.text.format.DateFormat.is24HourFormat
import android.util.AttributeSet
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.smartspace.model.SmartspaceTimeFormat
import app.lawnchair.util.broadcastReceiverFlow
import app.lawnchair.util.repeatOnAttached
import app.lawnchair.util.subscribeBlocking
import com.android.launcher3.R
import com.patrykmichalik.opto.core.firstBlocking
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat
import java.util.*

typealias FormatterFunction = (Long) -> String

class IcuDateTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : DoubleShadowTextView(context, attrs) {

    private val prefs = PreferenceManager2.getInstance(context)
    private var calendar: SmartspaceCalendar? = null
    private lateinit var dateTimeOptions: DateTimeOptions
    private var formatterFunction: FormatterFunction? = null
    private val ticker = this::onTimeTick

    init {
        repeatOnAttached {
            val calendarSelectionEnabled = prefs.enableSmartspaceCalendarSelection.firstBlocking()
            val calendarFlow =
                if (calendarSelectionEnabled) prefs.smartspaceCalendar.get()
                else flowOf(prefs.smartspaceCalendar.defaultValue)
            val optionsFlow = DateTimeOptions.fromPrefs(prefs)
            combine(calendarFlow, optionsFlow) { calendar, options -> calendar to options }
                .subscribeBlocking(this) {
                    calendar = it.first
                    dateTimeOptions = it.second
                    onTimeChanged(true)
                }

            val intentFilter = IntentFilter()
            intentFilter.addAction(Intent.ACTION_TIME_CHANGED)
            intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
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
                }
            )
            if (dateTimeOptions.showDate) format = context.getString(R.string.smartspace_icu_date_pattern_persian_date) + format
        } else {
            format = context.getString(R.string.smartspace_icu_date_pattern_persian_wday_month_day_no_year)
        }
        val formatter = PersianDateFormat(format, PersianDateFormat.PersianDateNumberCharacter.FARSI)
        return { formatter.format(PersianDate(it)) }
    }

    private fun createGregorianFormatter(): FormatterFunction {
        var format: String
        if (dateTimeOptions.showTime) {
            format = context.getString(
                when {
                    dateTimeOptions.timeFormat is SmartspaceTimeFormat.TwelveHourFormat -> R.string.smartspace_icu_date_pattern_gregorian_time_12h
                    dateTimeOptions.timeFormat is SmartspaceTimeFormat.TwentyFourHourFormat -> R.string.smartspace_icu_date_pattern_gregorian_time
                    is24HourFormat(context) -> R.string.smartspace_icu_date_pattern_gregorian_time
                    else -> R.string.smartspace_icu_date_pattern_gregorian_time_12h
                }
            )
            if (dateTimeOptions.showDate) format += context.getString(R.string.smartspace_icu_date_pattern_gregorian_date)
        } else {
            format = context.getString(R.string.smartspace_icu_date_pattern_gregorian_wday_month_day_no_year)
        }
        val formatter = DateFormat.getInstanceForSkeleton(format, Locale.getDefault())
        formatter.setContext(DisplayContext.CAPITALIZATION_FOR_STANDALONE)
        return { formatter.format(it) }
    }

    private fun onTimeTick() {
        onTimeChanged(false)
        val uptimeMillis: Long = SystemClock.uptimeMillis()
        handler?.postAtTime(ticker, uptimeMillis + (1000 - uptimeMillis % 1000))
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
        fun fromPrefs(prefs: PreferenceManager2) =
            combine(
                prefs.smartspaceShowDate.get(),
                prefs.smartspaceShowTime.get(),
                prefs.smartspaceTimeFormat.get(),
            ) { showDate, showTime, timeFormat ->
                DateTimeOptions(showDate, showTime, timeFormat)
            }
    }
}
