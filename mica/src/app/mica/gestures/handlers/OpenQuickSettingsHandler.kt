package app.mica.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import app.mica.MicaLauncher
import com.android.launcher3.R

class OpenQuickSettingsHandler(
    context: Context,
) : GestureHandler(context) {

    @SuppressLint("WrongConstant")
    override suspend fun onTrigger(launcher: MicaLauncher) {
        try {
            Log.v(OpenQuickSettingsHandler::class.java.simpleName, "(Tried reflection)")
            Class.forName("android.app.StatusBarManager")
                .getMethod("expandSettingsPanel")
                .apply { isAccessible = true }
                .invoke(context.getSystemService("statusbar"))
        } catch (e: Exception) {
            e.printStackTrace()

            // Fallback to a11y service
            GestureWithAccessibilityHandler.onTrigger(
                launcher,
                R.string.quick_settings_a11y_hint,
                AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
            )
        }
    }
}
