package ch.deletescape.lawnchair.gestures.handlers

import android.support.annotation.Keep
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.GestureHandler

@Keep
class OpenDrawerGestureHandler(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    override fun onGestureTrigger() {
        launcher.showAppsView(true, true)
    }
}

@Keep
class OpenWidgetsGestureHandler(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    override fun onGestureTrigger() {
        launcher.showWidgetsView(true, true)
    }
}

@Keep
class OpenOverviewGestureHandler(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    override fun onGestureTrigger() {
        launcher.showOverviewPopup(false)
    }
}

@Keep
class StartGlobalSearchGestureHandler(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    override fun onGestureTrigger() {
        launcher.startGlobalSearch(null,  false, null, null)
    }
}

@Keep
class StartAppSearchGestureHandler(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    override fun onGestureTrigger() {
        launcher.showAppsView(true, true, true)
    }
}
