package app.lawnchair.ui.preferences.components

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.LawnchairApp
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.controls.SwitchPreference
import com.android.launcher3.R

@SuppressLint("WrongConstant")
@Composable
fun SuggestionsPreference() {
    val context = LocalContext.current
    val intent = Intent("android.settings.ACTION_CONTENT_SUGGESTIONS_SETTINGS")
    val hasPkgUsagePermission = context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED
    val canResolveToSuggestionPreference = context.packageManager.resolveActivity(intent, 0) != null
    val suggestionSettingsAvailable = hasPkgUsagePermission && canResolveToSuggestionPreference

    if (suggestionSettingsAvailable) {
        ClickablePreference(
            label = stringResource(id = R.string.suggestion_pref_screen_title),
            onClick = {
                context.startActivity(intent)
            },
        )
    } else if (suggestionSettingsAvailable || LawnchairApp.isRecentsEnabled) {
        /* On some devices, the Suggestions activity could not be found or PACKAGE_USAGE_STATS is
          not granted. And on some devices (non-Google especially), the suggestions preference shows
          nothing at all */

        val prefs2 = preferenceManager2()
        val showRecentAppsInDrawer = prefs2.showSuggestedAppsInDrawer.getAdapter()
        SwitchPreference(
            label = stringResource(id = R.string.show_suggested_apps_at_drawer_top),
            adapter = showRecentAppsInDrawer,
        )
    }
}
