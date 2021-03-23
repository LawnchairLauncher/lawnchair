package app.lawnchair.gestures

import android.view.View.OnTouchListener
import android.view.GestureDetector
import android.annotation.SuppressLint
import android.content.Context
import android.view.MotionEvent
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.View
import java.lang.Exception
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

        override fun onFling(e1: MotionEvent, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            var result = false
            try {
                val diffY = e2.y - e1.y
                val diffX = e2.x - e1.x
                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > SWIPE_THRESHOLD && abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight()
                        } else {
                            onSwipeLeft()
                        }
                        result = true
                    }
                } else if (abs(diffY) > SWIPE_THRESHOLD && abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                    if (diffY > 0) {
                        onSwipeBottom()
                    } else {
                        onSwipeTop()
                    }
                    result = true
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
            }
            return result
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