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
import app.lawnchair.util.viewAttachedScope
import com.android.launcher3.R
import com.patrykmichalik.preferencemanager.firstBlocking
import saman.zamani.persiandate.PersianDate
import saman.zamani.persiandate.PersianDateFormat
import java.util.*

class IcuDateTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : DoubleShadowTextView(context, attrs) {

    private lateinit var preferenceManager2: PreferenceManager2
    private var calendar: SmartspaceCalendar? = null
    private var formatterGregorian: DateFormat? = null
    private var formatterPersian: PersianDateFormat? = null
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
            val timeText = when (calendar) {
                SmartspaceCalendar.Persian -> getTimeTextPersian(updateFormatter = updateFormatter)
                else -> getTimeTextGregorian(updateFormatter = updateFormatter)
            }
            if (text != timeText) {
                textAlignment =
                    if (calendar == SmartspaceCalendar.Persian) TEXT_ALIGNMENT_TEXT_END else TEXT_ALIGNMENT_TEXT_START
                text = timeText
                contentDescription = timeText
            }
        }
    }

    private fun getTimeTextPersian(updateFormatter: Boolean): String {
        val formatter = formatterPersian.takeIf { updateFormatter.not() } ?: PersianDateFormat(
            context.getString(R.string.smartspace_icu_date_pattern_persian),
            PersianDateFormat.PersianDateNumberCharacter.FARSI,
        ).also { formatterPersian = it }
        return formatter.format(PersianDate(System.currentTimeMillis()))
    }

    private fun getTimeTextGregorian(updateFormatter: Boolean): String {
        val formatter = formatterGregorian.takeIf { updateFormatter.not() }
            ?: DateFormat.getInstanceForSkeleton(
                context.getString(R.string.smartspace_icu_date_pattern_gregorian),
                Locale.getDefault()
            ).also {
                it.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
                formatterGregorian = it
            }
        return formatter.format(System.currentTimeMillis())
    }

    private fun onTimeTick() {
        onTimeChanged(false)
        val uptimeMillis: Long = SystemClock.uptimeMillis()
        handler?.postAtTime(ticker, uptimeMillis + (1000 - uptimeMillis % 1000))
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        preferenceManager2 = PreferenceManager2.getInstance(context)
        val calendarSelectionEnabled =
            preferenceManager2.enableSmartspaceCalendarSelection.firstBlocking()
        if (calendarSelectionEnabled) {
            preferenceManager2.smartspaceCalendar.subscribeBlocking(scope = viewAttachedScope) {
                calendar = it
            }
        } else {
            calendar = preferenceManager2.smartspaceCalendar.defaultValue
        }
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        handler?.removeCallbacks(ticker)
        ticker()
    }
}
