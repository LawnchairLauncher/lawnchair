package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.handlers.OpenDrawerGestureHandler

class SwipeUpGesture(controller: GestureController) : Gesture(controller) {

    private val handler by controller.createHandlerPref("pref_gesture_swipe_up",
            OpenDrawerGestureHandler(controller.launcher, null))
    val isCustom get() = handler !is OpenDrawerGestureHandler
    override val isEnabled = true

    override fun onEvent(): Boolean {
        if (isCustom){
            handler.onGestureTrigger(controller)
            return true
        }
        return false
    }
}
