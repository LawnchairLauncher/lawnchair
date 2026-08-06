package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.animateToAllApps

class OpenAppSearchGestureHandler(context: Context) : GestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        val searchUiManager = launcher.appsView.searchUiManager
        searchUiManager.setDirectFocus(true)
        searchUiManager.editText?.showKeyboard()
        launcher.animateToAllApps()
    }
}
