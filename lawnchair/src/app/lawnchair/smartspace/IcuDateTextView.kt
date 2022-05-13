package app.lawnchair.smartspace

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.icu.text.DateFormat
import android.icu.text.DisplayContext
import android.os.SystemClock
import android.util.AttributeSet
import com.android.launcher3.R
import java.util.*

class IcuDateTextView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : DoubleShadowTextView(context, attrs) {

    private var formatter: DateFormat? = null
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
            if (formatter == null || updateFormatter) {
                val formatter: DateFormat = DateFormat.getInstanceForSkeleton(
                    context.getString(R.string.smartspace_icu_date_pattern), Locale.getDefault()
                )
                this.formatter = formatter
                formatter.setContext(DisplayContext.CAPITALIZATION_FOR_BEGINNING_OF_SENTENCE)
            }
            val format = formatter!!.format(java.lang.Long.valueOf(System.currentTimeMillis()))
            if (text != format) {
                text = format
                contentDescription = format
            }
        }
    }

    private fun onTimeTick() {
        onTimeChanged(false)
        val uptimeMillis: Long = SystemClock.uptimeMillis()
        handler?.postAtTime(ticker, uptimeMillis + (1000 - uptimeMillis % 1000))
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        handler?.removeCallbacks(ticker)
        ticker()
    }
}
