package ch.deletescape.lawnchair.gestures.handlers

import android.annotation.SuppressLint
import android.content.Context
import android.support.annotation.Keep
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.R
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException

@Keep
class NotificationsOpenGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_open_notifications)!!

    @SuppressLint("PrivateApi", "WrongConstant")
    override fun onGestureTrigger(controller: GestureController) {
        try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("expandNotificationsPanel")
                    .invoke(controller.launcher.getSystemService("statusbar"))
        } catch (ex: ClassNotFoundException) {
        } catch (ex: NoSuchMethodException) {
        } catch (ex: IllegalAccessException) {
        } catch (ex: InvocationTargetException) {
        }
    }
}

@Keep
class NotificationsCloseGestureHandler(context: Context, config: JSONObject?) : GestureHandler(context, config) {

    override val displayName = context.getString(R.string.action_close_notifications)!!

    @SuppressLint("PrivateApi", "WrongConstant")
    override fun onGestureTrigger(controller: GestureController) {
        try {
            Class.forName("android.app.StatusBarManager")
                    .getMethod("collapsePanels")
                    .invoke(controller.launcher.getSystemService("statusbar"))
        } catch (ex: ClassNotFoundException) {
        } catch (ex: NoSuchMethodException) {
        } catch (ex: IllegalAccessException) {
        } catch (ex: InvocationTargetException) {
        }

    }
}