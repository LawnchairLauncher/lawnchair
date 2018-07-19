package ch.deletescape.lawnchair.gestures

import ch.deletescape.lawnchair.Launcher

abstract class GestureHandler(val launcher: Launcher) {

    abstract fun onGestureTrigger()
}