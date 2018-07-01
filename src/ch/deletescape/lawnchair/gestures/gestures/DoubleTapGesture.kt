package ch.deletescape.lawnchair.gestures.gestures

import android.view.MotionEvent
import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.lawnchairPrefs

class DoubleTapGesture(controller: GestureController) : Gesture(controller) {

    override val isEnabled = true
    private val prefs = controller.launcher.lawnchairPrefs
    private val delay get() = prefs.doubleTapDelay
    private val handler by controller.createHandlerPref("pref_gesture_double_tap")

    private var lastDown = 0L

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                lastDown = if (ev.eventTime - lastDown <= delay) {
                    handler.onGestureTrigger(controller)
                    0L
                } else {
                    ev.downTime
                }
            }
        }
        return false
    }
}
