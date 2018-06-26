package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.lawnchairPrefs

class PressHomeGesture(private val controller: GestureController) : Gesture {

    private val prefs = controller.launcher.lawnchairPrefs
    private val handlerClass get() = prefs.pressHomeHandler
    override val isEnabled = true

    override fun onEvent(): Boolean {
        controller.createGestureHandler(handlerClass)?.onGestureTrigger()
        return true
    }
}
