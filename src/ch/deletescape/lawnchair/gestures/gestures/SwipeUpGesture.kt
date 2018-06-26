package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.R

class SwipeUpGesture(private val controller: GestureController) : Gesture {

    private val prefs = controller.launcher.lawnchairPrefs
    private val handlerClass get() = prefs.swipeUpHandler
    val isCustom get() = handlerClass != controller.launcher.getString(R.string.action_open_drawer_class)
    override val isEnabled = true

    override fun onEvent(): Boolean {
        if (isCustom){
            controller.createGestureHandler(handlerClass)?.onGestureTrigger()
            return true
        }
        return false
    }
}
