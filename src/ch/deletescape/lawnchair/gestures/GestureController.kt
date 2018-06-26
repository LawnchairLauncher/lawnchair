package ch.deletescape.lawnchair.gestures

import android.text.TextUtils
import android.view.MotionEvent
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.gestures.DoubleTapGesture
import com.android.launcher3.util.TouchController

class GestureController(val launcher: LawnchairLauncher) : TouchController {

    private val doubleTapGesture = DoubleTapGesture(this)

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
}
