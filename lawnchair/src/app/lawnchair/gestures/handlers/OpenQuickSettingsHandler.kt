package app.lawnchair.gestures.handlers

import android.accessibilityservice.AccessibilityService
import android.content.Context
import app.lawnchair.LawnchairLauncher
import com.android.launcher3.R

class OpenQuickSettingsHandler(
    context: Context,
) : GestureHandler(context) {
    override suspend fun onTrigger(launcher: LawnchairLauncher) {
        GestureWithAccessibilityHandler.onTrigger(
            launcher,
            R.string.quick_settings_a11y_hint,
            AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS,
        )
    }
}
