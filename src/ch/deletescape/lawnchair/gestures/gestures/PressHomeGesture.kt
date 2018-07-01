package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController

class PressHomeGesture(controller: GestureController) : Gesture(controller) {

    private val handler by controller.createHandlerPref("pref_gesture_press_home")
    override val isEnabled = true

    override fun onEvent(): Boolean {
        handler.onGestureTrigger(controller)
        return true
    }
}
