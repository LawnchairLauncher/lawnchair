package app.lawnchair.ui.preferences

import android.app.Activity
import android.content.Intent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.gestures.handlers.OpenAppTarget
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.App
import app.lawnchair.util.appsList
import com.android.launcher3.R
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

fun NavGraphBuilder.pickAppForGestureGraph(route: String) {
    preferenceGraph(route, { PickAppForGesture() })
}

@Composable
fun PickAppForGesture() {
    val optionalApps by appsList()
    val state = rememberLazyListState()

    val activity = LocalContext.current as Activity
    fun onSelectApp(app: App) {
        val config: GestureHandlerConfig = GestureHandlerConfig.OpenApp(
            appName = app.label,
            target = OpenAppTarget.App(app.key)
        )
        val configString = Json.encodeToString(config)
        activity.setResult(Activity.RESULT_OK, Intent().putExtra("config", configString))
        activity.finish()
    }

    PreferenceScaffold(
        label = stringResource(id = R.string.pick_app_for_gesture),
    ) {
        Crossfade(targetState = optionalApps.isPresent) { present ->
            if (present) {
                PreferenceLazyColumn(state = state) {
                    val apps = optionalApps.get()
                    preferenceGroupItems(
                        items = apps,
                        isFirstChild = true,
                    ) { _, app ->
                        AppItem(
                            app = app,
                            onClick = { onSelectApp(app) },
                        )
                    }
                }
            } else {
                PreferenceLazyColumn(enabled = false) {
                    preferenceGroupItems(
                        count = 20,
                        isFirstChild = true,
                    ) {
                        AppItemPlaceholder()
                    }
                }
            }
        }
    }
}
