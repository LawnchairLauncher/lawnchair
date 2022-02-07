package app.lawnchair.ui.preferences

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.PrefEntry
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.*
import com.android.launcher3.settings.DeveloperOptionsFragment
import com.android.launcher3.settings.SettingsActivity

@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun NavGraphBuilder.debugMenuGraph(route: String) {
    preferenceGraph(route, { DebugMenuPreferences() })
}

/**
 * A screen to house unfinished preferences and debug flags
 */
@ExperimentalMaterialApi
@ExperimentalAnimationApi
@Composable
fun DebugMenuPreferences() {
    val prefs = preferenceManager()
    val flags = rememberSaveable { prefs.getDebugFlags() }
    val context = LocalContext.current
    PreferenceLayout(label = "Debug Menu") {
        PreferenceGroup {
            ClickablePreference(
                label = "Feature Flags",
                onClick = {
                    Intent(context, SettingsActivity::class.java)
                        .putExtra(":settings:fragment", DeveloperOptionsFragment::class.java.name)
                        .also { context.startActivity(it) }
                },
            )
            ClickablePreference(
                label = "Crash Launcher",
                onClick = { throw RuntimeException("User triggered crash") },
            )
        }
        PreferenceGroup(heading = "Debug Flags") {
            flags.forEach {
                SwitchPreference(
                    adapter = it.getAdapter(),
                    label = it.key,
                )
            }
        }
    }
}

private fun PreferenceManager.getDebugFlags() =
    listOf(
        showComponentName,
        deviceSearch,
        searchResultShortcuts,
        searchResultPeople,
        searchResultPixelTips,
        searchResultSettings,
    )
