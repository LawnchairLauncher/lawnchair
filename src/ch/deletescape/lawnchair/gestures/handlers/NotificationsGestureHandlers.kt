package ch.deletescape.lawnchair.gestures.handlers

import android.annotation.SuppressLint
import android.support.annotation.Keep
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.GestureHandler
import java.lang.reflect.InvocationTargetException



@Keep
class NotificationsOpenGestureHandler(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    @SuppressLint("PrivateApi", "WrongConstant")
    override fun onGestureTrigger() {
        try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(launcher.getSystemService("statusbar"))
        } catch (ex: ClassNotFoundException) {
        } catch (ex: NoSuchMethodException) {
        } catch (ex: IllegalAccessException) {
        } catch (ex: InvocationTargetException) {
        }
    }
}

@Keep
class NotificationsCloseGestureHandler(launcher: LawnchairLauncher) : GestureHandler(launcher) {

    @SuppressLint("PrivateApi", "WrongConstant")
    override fun onGestureTrigger() {
        try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("collapsePanels")
                    .invoke(launcher.getSystemService("statusbar"))
        } catch (ex: ClassNotFoundException) {
        } catch (ex: NoSuchMethodException) {
        } catch (ex: IllegalAccessException) {
        } catch (ex: InvocationTargetException) {
        }

    }
}