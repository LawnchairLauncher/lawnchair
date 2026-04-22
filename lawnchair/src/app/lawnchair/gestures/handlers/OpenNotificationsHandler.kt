package app.lawnchair.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R

class OpenNotificationsHandler(context: Context) : GestureHandler(context) {

    @SuppressLint("WrongConstant")
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        try {
            Log.v(OpenNotificationsHandler::class.java.simpleName, "(Tried reflection)")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandNotificationsPanel")
                .apply { isAccessible = true }
                .invoke(context.getSystemService("statusbar"))
        } catch (e: Exception) {
            e.printStackTrace()

            // Fallback to a11y service
            GestureWithAccessibilityHandler.onTrigger(
                launcher,
                R.string.notifications_fallback_a11y_hint,
                AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS,
            )
        }
    }
}
