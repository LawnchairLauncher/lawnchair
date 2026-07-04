package app.lawnchair.gestures

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import kotlin.math.abs

abstract class DirectionalGestureListener(ctx: Context?) : OnTouchListener {
    private val mGestureDetector = GestureDetector(ctx, GestureListener())
    private var handledGesture = false

    fun onTouchEvent(event: MotionEvent): Boolean {
        handledGesture = false
        mGestureDetector.onTouchEvent(event)
        return handledGesture
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouch(v: View, event: MotionEvent): Boolean {
        return onTouchEvent(event)
    }

    inner class GestureListener : SimpleOnGestureListener() {

        private fun shouldReactToSwipe(diff: Float, velocity: Float): Boolean = abs(diff) > SWIPE_THRESHOLD && abs(velocity) > SWIPE_VELOCITY_THRESHOLD

        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onFling(
            e1: MotionEvent?,
            e2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            val diffY = e2.y - (e1?.y ?: 0f)
            val diffX = e2.x - (e1?.x ?: 0f)

            Log.d("GESTURE_DETECTION", "onFling: y " + shouldReactToSwipe(diffY, velocityY))
            Log.d("GESTURE_DETECTION", "onFling: X " + shouldReactToSwipe(diffX, velocityX))

            handledGesture = when {
                shouldReactToSwipe(diffY, velocityY) -> {
                    if (diffY < 0) {
                        Log.d("GESTURE_DETECTION", "Swipe Up Detected")
                        onSwipeTop()
                    } else {
                        Log.d("GESTURE_DETECTION", "Swipe Down Detected")
                        onSwipeDown()
                    }
                }

                shouldReactToSwipe(diffX, velocityX) -> {
                    if (diffX > 0) {
                        Log.d("GESTURE_DETECTION", "Swipe Right Detected")
                        onSwipeRight()
                    } else {
                        Log.d("GESTURE_DETECTION", "Swipe Left Detected")
                        onSwipeLeft()
                    }
                }

                else -> false
            }
            return handledGesture
        }
    }

    abstract fun onSwipeRight(): Boolean
    abstract fun onSwipeLeft(): Boolean
    abstract fun onSwipeTop(): Boolean
    abstract fun onSwipeDown(): Boolean

    companion object {
        private const val SWIPE_THRESHOLD = 100
        private const val SWIPE_VELOCITY_THRESHOLD = 100
    }
}
