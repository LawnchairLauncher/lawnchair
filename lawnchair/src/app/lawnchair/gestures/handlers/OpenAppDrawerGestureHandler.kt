package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.animateToAllApps
import app.lawnchair.gestures.GestureHandler

open class OpenAppDrawerGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        launcher.animateToAllApps()
    }
}
