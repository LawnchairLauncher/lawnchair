package app.lawnchair.gestures.ui

import android.content.Context
import android.os.UserHandle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.gestures.AppShortcutEntry
import app.lawnchair.gestures.AppShortcuts
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.config.GestureHandlerOption
import app.lawnchair.gestures.config.buildConfigFrom
import app.lawnchair.gestures.config.filterGestureHandlerOptions
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.AppItem
import app.lawnchair.ui.preferences.components.controls.ClickablePreference
import app.lawnchair.ui.preferences.components.layout.PreferenceLayoutLazyColumn
import app.lawnchair.ui.preferences.components.layout.preferenceGroupItems
import com.android.launcher3.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @param appPackage The app the gesture belongs to (an icon's app), if any. Its shortcuts are
 * offered at the top, so e.g. a specific chat can be picked with one tap.
 */
@Composable
fun CreateActionsScreen(
    modifier: Modifier = Modifier,
    appPackage: String? = null,
    appUser: UserHandle? = null,
    onSelect: (GestureHandlerConfig) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val prefs2 = preferenceManager2()
    val newOptions =
        filterGestureHandlerOptions(deckLayoutEnabled = prefs2.deckLayout.getAdapter().state.value)

    // Only the icon's own app is queried, which keeps this sheet fast.
    val appShortcuts by produceState(initialValue = emptyList<AppShortcutEntry>(), appPackage, appUser) {
        if (appPackage != null) {
            value = withContext(Dispatchers.IO) { AppShortcuts.load(context, appPackage, appUser) }
        }
    }
    val appLabel = remember(appPackage) { appPackage?.let { appLabelOf(context, it) } }

    fun onClick(option: GestureHandlerOption) {
        scope.launch {
            val config = option.buildConfigFrom(context) ?: return@launch
            onSelect(config)
        }
    }

    PreferenceLayoutLazyColumn(
        label = stringResource(id = R.string.lawnchair_actions),
        modifier = modifier,
    ) {
        if (appShortcuts.isEmpty()) {
            preferenceGroupItems(items = newOptions, isFirstChild = true) { _, it ->
                ClickablePreference(
                    label = it.getLabel(context),
                    onClick = { onClick(it) },
                )
            }
        } else {
            preferenceGroupItems(
                items = appShortcuts,
                isFirstChild = true,
                heading = { stringResource(id = R.string.gesture_app_shortcuts_heading, appLabel.orEmpty()) },
            ) { _, entry ->
                AppItem(
                    label = entry.label,
                    icon = entry.icon,
                    onClick = { onSelect(entry.toGestureConfig()) },
                )
            }
            preferenceGroupItems(
                items = newOptions,
                isFirstChild = false,
                heading = { stringResource(id = R.string.gesture_actions_general_heading) },
            ) { _, it ->
                ClickablePreference(
                    label = it.getLabel(context),
                    onClick = { onClick(it) },
                )
            }
        }
    }
}

private fun appLabelOf(context: Context, packageName: String): String = try {
    val pm = context.packageManager
    pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
} catch (_: Exception) {
    packageName
}
