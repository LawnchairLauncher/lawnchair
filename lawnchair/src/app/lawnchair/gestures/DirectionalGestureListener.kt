package app.lawnchair.gestures

import android.annotation.SuppressLint
import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import kotlin.math.abs

open class DirectionalGestureListener(ctx: Context?) : OnTouchListener {
    private val mGestureDetector: GestureDetector

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return mGestureDetector.onTouchEvent(event)
    }

    @Suppress("PrivatePropertyName")
    private inner class GestureListener : SimpleOnGestureListener() {
        private val SWIPE_THRESHOLD = 100
        private val SWIPE_VELOCITY_THRESHOLD = 100

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        private fun shouldReactToSwipe(diff: Float, velocity: Float): Boolean =
            abs(diff) > SWIPE_THRESHOLD && abs(velocity) > SWIPE_VELOCITY_THRESHOLD

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            return try {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x

                when {
                    abs(diffX) > abs(diffY) && shouldReactToSwipe(diffX, velocityX) -> {
                        if (diffX > 0) onSwipeRight() else onSwipeLeft()
                        true
                    }
                    shouldReactToSwipe(diffY, velocityY) -> {
                        if (diffY > 0) onSwipeBottom() else onSwipeTop()
                        true
                    }
                    else -> false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    fun onSwipeRight() {}
    fun onSwipeLeft() {}
    fun onSwipeTop() {}
    open fun onSwipeBottom() {}

    init {
        mGestureDetector = GestureDetector(ctx, GestureListener())
    }
}
