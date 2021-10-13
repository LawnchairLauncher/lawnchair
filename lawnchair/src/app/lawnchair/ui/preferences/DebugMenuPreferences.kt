package app.lawnchair.ui.preferences

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.navigation.NavGraphBuilder
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
    PreferenceLayout(
        label = "Debug Menu"
    ) {

    }
}
