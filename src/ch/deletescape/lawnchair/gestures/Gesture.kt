package ch.deletescape.lawnchair.gestures

import android.view.MotionEvent

abstract class Gesture(val controller: GestureController) {

    abstract val isEnabled: Boolean

    open fun onTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    open fun onEvent(): Boolean {
        return false
    }
}
