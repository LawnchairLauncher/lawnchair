package app.lawnchair.ui.preferences

import android.content.Intent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.PreferenceCollectorScope
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.preferences.components.*
import app.lawnchair.util.ifNotNull
import com.android.launcher3.settings.DeveloperOptionsFragment
import com.android.launcher3.settings.SettingsActivity
import com.patrykmichalik.preferencemanager.Preference
import com.patrykmichalik.preferencemanager.state

@ExperimentalMaterialApi
@ExperimentalAnimationApi
fun NavGraphBuilder.debugMenuGraph(route: String) {
    preferenceGraph(route, { DebugMenuPreferences() })
}

interface DebugMenuPreferenceCollectorScope : PreferenceCollectorScope {
    val showComponentNames: Boolean
}

@Composable
fun DebugMenuPreferenceCollector(content: @Composable DebugMenuPreferenceCollectorScope.() -> Unit) {
    val preferenceManager = preferenceManager2()
    val showComponentNames by preferenceManager.showComponentNames.state()
    ifNotNull(showComponentNames) {
        object : DebugMenuPreferenceCollectorScope {
            override val showComponentNames = it[0] as Boolean
            override val coroutineScope = rememberCoroutineScope()
            override val preferenceManager = preferenceManager
        }.content()
    }
}

/**
 * A screen to house unfinished preferences and debug flags
 */
@ExperimentalMaterialApi
@ExperimentalAnimationApi
@Composable
fun DebugMenuPreferences() {
    val prefs = preferenceManager()
    val flags = remember { prefs.getDebugFlags() }
    val context = LocalContext.current
    DebugMenuPreferenceCollector {
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
                preferenceManager.debugFlags.forEach { (preference, getCollectedPreference) ->
                    SwitchPreference2(
                        checked = getCollectedPreference(),
                        label = preference.key.name,
                        edit = { preference.set(value = it) },
                    )
                }
                flags.forEach {
                    SwitchPreference(
                        adapter = it.getAdapter(),
                        label = it.key,
                    )
                }
            }
        }
    }
}

private val PreferenceManager2.debugFlags: Map<Preference<Boolean, Boolean>, DebugMenuPreferenceCollectorScope.() -> Boolean>
    get() = mapOf(showComponentNames to { showComponentNames })

private fun PreferenceManager.getDebugFlags() =
    listOf(
        deviceSearch,
        searchResultShortcuts,
        searchResultPeople,
        searchResultPixelTips,
        searchResultSettings,
    )
