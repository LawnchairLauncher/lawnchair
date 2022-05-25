package app.lawnchair.smartspace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.os.SystemClock
import android.util.AttributeSet
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.subscribeBlocking
import app.lawnchair.smartspace.model.SmartspaceCalendar
import app.lawnchair.util.subscribeBlocking
import app.lawnchair.util.viewAttachedScope
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.firstBlocking
import kotlinx.coroutines.flow.combine
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
    private val intentReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            onTimeChanged(intent.action != Intent.ACTION_TIME_TICK)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_TIME_CHANGED)
        intentFilter.addAction(Intent.ACTION_TIMEZONE_CHANGED)
        context.registerReceiver(intentReceiver, intentFilter)
        onTimeChanged(true)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.unregisterReceiver(intentReceiver)
    }

    private fun onTimeChanged(updateFormatter: Boolean) {
        if (isShown) {
            val timeText = getTimeText(updateFormatter)
            if (text != timeText) {
                textAlignment =
                    if (calendar == SmartspaceCalendar.Persian) TEXT_ALIGNMENT_TEXT_END else TEXT_ALIGNMENT_TEXT_START
                text = timeText
                contentDescription = timeText
            }
        }
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
        val formatter = PersianDateFormat(
            context.getString(R.string.smartspace_icu_date_pattern_persian),
            PersianDateFormat.PersianDateNumberCharacter.FARSI,
        )
        return { formatter.format(PersianDate(it)) }
    }

    private fun createGregorianFormatter(): FormatterFunction {
        var format: String
        if (dateTimeOptions.showTime) {
            format = context.getString(
                if (dateTimeOptions.time24HourFormat) R.string.smartspace_icu_date_pattern_gregorian_time
                else R.string.smartspace_icu_date_pattern_gregorian_time_12h
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

    override fun onFinishInflate() {
        super.onFinishInflate()

        val calendarSelectionEnabled =
            prefs.enableSmartspaceCalendarSelection.firstBlocking()
        if (calendarSelectionEnabled) {
            prefs.smartspaceCalendar.subscribeBlocking(scope = viewAttachedScope) {
                calendar = it
                onTimeChanged(true)
            }
        } else {
            calendar = prefs.smartspaceCalendar.defaultValue
        }

        DateTimeOptions.fromPrefs(prefs).subscribeBlocking(viewAttachedScope) {
            dateTimeOptions = it
            onTimeChanged(true)
        }
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
    val time24HourFormat: Boolean,
) {
    companion object {
        fun fromPrefs(prefs: PreferenceManager2) =
            combine(
                prefs.smartspaceShowDate.get(),
                prefs.smartspaceShowTime.get(),
                prefs.smartspace24HourFormat.get()
            ) { showDate, showTime, time24HourFormat ->
                DateTimeOptions(showDate, showTime, time24HourFormat)
            }
    }
}
