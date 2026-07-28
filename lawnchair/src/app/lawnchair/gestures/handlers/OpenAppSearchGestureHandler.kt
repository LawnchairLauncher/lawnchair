package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher

class OpenAppSearchGestureHandler(context: Context) : OpenAppDrawerGestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        super.onTrigger(launcher)
        val searchUiManager = launcher.appsView.searchUiManager
        searchUiManager.setDirectFocus(true)
        searchUiManager.editText?.showKeyboard()
    }
}
