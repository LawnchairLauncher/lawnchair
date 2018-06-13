package ch.deletescape.lawnchair.gestures

import ch.deletescape.lawnchair.LawnchairLauncher

abstract class GestureHandler(val launcher: LawnchairLauncher) {

    abstract fun onGestureTrigger()
}