package ch.deletescape.lawnchair.gestures

import android.text.TextUtils
import android.view.MotionEvent
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.gestures.DoubleTapGesture
import ch.deletescape.lawnchair.gestures.gestures.PressHomeGesture
import ch.deletescape.lawnchair.gestures.gestures.SwipeDownGesture
import ch.deletescape.lawnchair.gestures.gestures.SwipeUpGesture
import com.android.launcher3.util.TouchController

class GestureController(val launcher: LawnchairLauncher) : TouchController {

    private val doubleTapGesture = DoubleTapGesture(this)
    private val pressHomeGesture = PressHomeGesture(this)
    private val swipeDownGesture = SwipeDownGesture(this)
    private val swipeUpGesture = SwipeUpGesture(this)

    override fun onControllerInterceptTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    override fun onControllerTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    fun createGestureHandler(className: String): GestureHandler? {
        if (!TextUtils.isEmpty(className)) {
            try {
                return Class.forName(className).getConstructor(LawnchairLauncher::class.java).newInstance(launcher) as? GestureHandler
            } catch (t: Throwable) {
            }
        }
        return null
    }

    fun onBlankAreaTouch(ev: MotionEvent) {
        doubleTapGesture.isEnabled && doubleTapGesture.onTouchEvent(ev)
    }

    fun onPressHome() {
        pressHomeGesture.isEnabled && pressHomeGesture.onEvent()
    }

    fun onSwipeDown() {
        swipeDownGesture.isEnabled && swipeDownGesture.onEvent()
    }

    fun onSwipeUp(): Boolean {
        return swipeUpGesture.isEnabled && swipeUpGesture.onEvent()
    }

    fun hasCustomSwipeUp():Boolean {
        return swipeUpGesture.isEnabled && swipeUpGesture.isCustom
    }
}
