package app.lawnchair.gestures.handlers

import android.content.Context
import app.lawnchair.LawnchairLauncher

class OpenAppSearchGestureHandler(context: Context) : OpenAppDrawerGestureHandler(context) {

    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        super.onTrigger(launcher)
        launcher.appsView.searchUiManager.editText?.showKeyboard()
    }
}
