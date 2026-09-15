package app.mica.gestures.handlers

import android.content.Context
import app.mica.MicaLauncher
import app.mica.animateToAllApps

class OpenAppDrawerGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: MicaLauncher) {
        launcher.animateToAllApps()
    }
}
