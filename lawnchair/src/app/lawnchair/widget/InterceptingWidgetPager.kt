package app.lawnchair.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.viewpager.widget.ViewPager

/**
 * Custom ViewPager for widget stacks that properly handles touch interception.
 * Handles horizontal scrolling while allowing long-press to bubble up to parent for drag.
 * Based on smartspace InterceptingViewPager but simplified for widget stacks.
 */
class InterceptingWidgetPager @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewPager(context, attrs) {

    private var initialX = 0f
    private var initialY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                initialX = ev.x
                initialY = ev.y
                // Allow parent to handle long-press by not intercepting immediately
                parent?.requestDisallowInterceptTouchEvent(false)
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(ev.x - initialX)
                val dy = Math.abs(ev.y - initialY)

                // If horizontal movement is greater than vertical, intercept for scrolling
                if (dx > touchSlop && dx > dy) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return super.onInterceptTouchEvent(ev)
                }
                // Otherwise, let parent handle it (for drag operations)
                return false
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return super.onTouchEvent(ev)
    }
}
