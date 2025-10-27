package app.lawnchair.gestures.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairLauncher
import java.lang.reflect.InvocationTargetException

class OpenNotificationsHandler(context: Context) : GestureHandler(context) {

    @SuppressLint("WrongConstant")
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        try {
            Log.v(OpenNotificationsHandler::class.java.simpleName, "(Tried reflection)")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .apply { isAccessible = true }
                .invoke(context.getSystemService("statusbar"))
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: ClassNotFoundException) {
            e.printStackTrace()
        }
    }
}
