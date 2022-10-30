package app.lawnchair.gestures.handlers

import android.annotation.SuppressLint
import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.GestureHandler

class OpenNotificationsHandler(context: Context) : GestureHandler(context) {

    @SuppressLint("WrongConstant")
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        runCatching {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .invoke(context.getSystemService("statusbar"))
        }
    }
}
