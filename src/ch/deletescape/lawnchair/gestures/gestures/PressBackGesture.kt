package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController

class PressBackGesture(controller: GestureController) : Gesture(controller) {

    private val handler by controller.createHandlerPref("pref_gesture_press_back")
    override val isEnabled = true

    override fun onEvent(): Boolean {
        handler.onGestureTrigger(controller)
        return true
    }
}
