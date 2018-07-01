package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.handlers.OpenOverviewGestureHandler

class LongPressGesture(controller: GestureController) : Gesture(controller) {

    private val handler by controller.createHandlerPref("pref_gesture_long_press",
            OpenOverviewGestureHandler(controller.launcher, null))
    override val isEnabled = true

    override fun onEvent(): Boolean {
        handler.onGestureTrigger(controller)
        return true
    }
}
