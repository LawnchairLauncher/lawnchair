package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.components.*

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
    PreferenceLayout(
        label = "Debug Menu"
    ) {
        PreferenceGroup {
            ListPreference(
                adapter = prefs.customIconShape.getAdapter(),
                entries = iconShapeEntries,
                label = "Icon Shape"
            )
        }
    }
}

private val iconShapeEntries = listOf(
    ListPreferenceEntry("") { "System" },
    ListPreferenceEntry("circle") { "Circle" },
    ListPreferenceEntry("roundedRect") { "Rounded Rectangle" },
    ListPreferenceEntry("squircle") { "Squircle" },
    ListPreferenceEntry("pebble") { "Pebble" },
)
