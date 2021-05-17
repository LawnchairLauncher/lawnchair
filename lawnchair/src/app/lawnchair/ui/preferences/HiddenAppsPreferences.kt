package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.*
import app.lawnchair.util.preferences.getAdapter
import app.lawnchair.util.preferences.preferenceManager
import com.android.launcher3.R
import java.util.*
import java.util.Comparator.comparing

@ExperimentalAnimationApi
fun NavGraphBuilder.hiddenAppsGraph(route: String) {
    preferenceGraph(route, { HiddenAppsPreferences() })
}

@ExperimentalAnimationApi
@Composable
fun HiddenAppsPreferences() {
    pageMeta.provide(Meta(title = stringResource(id = R.string.hidden_apps_label)))
    var hiddenApps by preferenceManager().hiddenAppSet.getAdapter()
    val optionalApps by appsList(
        filter = defaultAppFilter(),
        comparator = hiddenAppsComparator(hiddenApps)
    )
    LoadingScreen(isLoading = !optionalApps.isPresent) {
        val apps = optionalApps.get()
        val toggleHiddenApp = { app: App ->
            val key = app.key.toString()
            hiddenApps = if (hiddenApps.contains(key)) {
                hiddenApps - key
            } else {
                hiddenApps + key
            }
        }
        PreferenceLayoutLazyColumn {
            preferenceGroupItems(apps, isFirstChild = true) { index, app ->
                AppItem(
                    app = app,
                    onClick = toggleHiddenApp,
                    showDivider = index != apps.lastIndex
                ) {
                    AnimatedCheck(visible = hiddenApps.contains(app.key.toString()))
                }
            }
        }
    }
}

@Composable
fun hiddenAppsComparator(hiddenApps: Set<String>): Comparator<App> {
    return remember {
        comparing<App, Int> { if (hiddenApps.contains(it.key.toString())) 0 else 1 }
            .thenComparing<String> { it.info.label.toString().toLowerCase(Locale.getDefault()) }
    }
}
