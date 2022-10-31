package app.lawnchair.gestures.handlers

import android.annotation.SuppressLint
import android.content.Context
import app.lawnchair.LawnchairLauncher
import app.lawnchair.gestures.GestureHandler
import java.lang.reflect.InvocationTargetException

class OpenNotificationsHandler(context: Context) : GestureHandler(context) {

    @SuppressLint("WrongConstant")
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        try {
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
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
