package app.lawnchair.ui.preferences

import android.app.Activity
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.smartspace.SmartspaceViewContainer
import app.lawnchair.smartspace.provider.SmartspaceProvider
import app.lawnchair.ui.preferences.components.PreferenceGroup
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SwitchPreference
import app.lawnchair.ui.theme.isSelectedThemeDark
import com.android.launcher3.R

fun NavGraphBuilder.smartspaceGraph(route: String) {
    preferenceGraph(route, { SmartspacePreferences() })
}

@Composable
fun SmartspacePreferences() {
    val prefs = preferenceManager2()
    PreferenceLayout(label = stringResource(id = R.string.smartspace_widget)) {
        SmartspacePreview()
        PreferenceGroup(heading = stringResource(id = R.string.what_to_show)) {
            SwitchPreference(
                adapter = prefs.smartspaceAagWidget.getAdapter(),
                label = stringResource(id = R.string.smartspace_weather)
            )
            SwitchPreference(
                adapter = prefs.smartspaceNowPlaying.getAdapter(),
                label = stringResource(id = R.string.smartspace_now_playing)
            )
            SwitchPreference(
                adapter = prefs.smartspaceBatteryStatus.getAdapter(),
                label = stringResource(id = R.string.smartspace_battery_status)
            )
        }
    }
}

@Composable
fun SmartspacePreview() {
    PreferenceGroup(
        isFirstChild = true,
        heading = stringResource(id = R.string.preview_label),
    ) {
        val themeRes = if (isSelectedThemeDark()) R.style.AppTheme_Dark else R.style.AppTheme_DarkText
        val context = LocalContext.current
        val themedContext = remember(themeRes) { ContextThemeWrapper(context, themeRes) }
        CompositionLocalProvider(LocalContext provides themedContext) {
            AndroidView(
                factory = {
                    val view = SmartspaceViewContainer(it, previewMode = true)
                    val height =
                        it.resources.getDimensionPixelSize(R.dimen.enhanced_smartspace_height)
                    view.layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, height)
                    view
                },
                modifier = Modifier
                    .padding(start = 8.dp, top = 8.dp, end = 0.dp, bottom = 16.dp)
            )
        }
        LaunchedEffect(key1 = null) {
            SmartspaceProvider.INSTANCE.get(context).startSetup(context as Activity)
        }
    }
}
