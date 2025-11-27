package app.lawnchair.smartspace

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.viewpager.widget.ViewPager

typealias EventProxy = (ev: MotionEvent) -> Boolean

class InterceptingViewPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewPager(context, attrs) {

    private var hasPerformedLongPress = false
    private var hasPostedLongPress = false
    private val longPressCallback = this::triggerLongPress
    private val superOnIntercept: EventProxy = { super.onInterceptTouchEvent(it) }
    private val superOnTouch: EventProxy = { super.onTouchEvent(it) }

    private fun handleTouchOverride(ev: MotionEvent, proxy: EventProxy): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                hasPerformedLongPress = false
                if (isLongClickable) {
                    cancelScheduledLongPress()
                    hasPostedLongPress = true
                    postDelayed(longPressCallback, ViewConfiguration.getLongPressTimeout().toLong())
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                cancelScheduledLongPress()
            }
        }

        if (hasPerformedLongPress) {
            cancelScheduledLongPress()
            return true
        }

        if (proxy(ev)) {
            cancelScheduledLongPress()
            return true
        }

        return false
    }

    private fun triggerLongPress() {
        hasPerformedLongPress = true
        if (performLongClick()) {
            parent.requestDisallowInterceptTouchEvent(true)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        return handleTouchOverride(ev, superOnIntercept)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return handleTouchOverride(ev, superOnTouch)
    }

    private fun cancelScheduledLongPress() {
        if (hasPostedLongPress) {
            hasPostedLongPress = false
            removeCallbacks(longPressCallback)
        }
    }
}
