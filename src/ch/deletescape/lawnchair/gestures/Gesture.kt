package ch.deletescape.lawnchair.gestures

import android.view.MotionEvent

interface Gesture {

    val isEnabled: Boolean

    fun onTouchEvent(ev: MotionEvent): Boolean {
        return false
    }

    fun onEvent(): Boolean {
        return false
    }
}
