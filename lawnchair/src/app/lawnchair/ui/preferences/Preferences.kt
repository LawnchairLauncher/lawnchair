/*
 * Copyright 2021, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair.ui.preferences

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.window.layout.DisplayFeature
import app.lawnchair.ui.preferences.destinations.PreferencesDashboard
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.ui.preferences.navigation.IconPicker
import app.lawnchair.ui.preferences.navigation.PreferenceNavigation
import app.lawnchair.ui.preferences.navigation.PreferenceRootRoute
import app.lawnchair.ui.preferences.navigation.PreferenceRoute
import app.lawnchair.ui.preferences.navigation.Root
import app.lawnchair.ui.preferences.navigation.SelectIcon
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import app.lawnchair.util.ProvideLifecycleState
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane

// todo migrate away from implicit navcontroller
val LocalNavController = staticCompositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

val LocalPreferenceInteractor = staticCompositionLocalOf<PreferenceInteractor> {
    error("CompositionLocal LocalPreferenceInteractor not present")
}

val LocalIsExpandedScreen = compositionLocalOf { false }

val twoPaneBlacklist = setOf(
    IconPicker::class,
    SelectIcon::class,
)

@Composable
fun Preferences(
    windowSizeClass: WindowSizeClass,
    displayFeatures: List<DisplayFeature>,
    modifier: Modifier = Modifier,
    startDestination: PreferenceRoute? = null,
    interactor: PreferenceInteractor = viewModel<PreferenceViewModel>(),
) {
    val navController = rememberNavController()
    val isExpandedScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded &&
        windowSizeClass.heightSizeClass in
        setOf(WindowHeightSizeClass.Expanded, WindowHeightSizeClass.Medium)

    val defaultStartingRoute = if (isExpandedScreen) General else Root
    val startingRoute = startDestination ?: defaultStartingRoute

    val blacklistedRoute = startingRoute::class in twoPaneBlacklist
    val useTwoPane = !blacklistedRoute && isExpandedScreen

    var currentTopRoute by remember { mutableStateOf(defaultStartingRoute) }

    Providers {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            modifier = modifier,
        ) {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalPreferenceInteractor provides interactor,
                LocalIsExpandedScreen provides isExpandedScreen,
            ) {
                PreferenceScreen(
                    currentTopRoute = currentTopRoute,
                    onRouteChange = {
                        currentTopRoute = it
                    },
                    useTwoPane = useTwoPane,
                    displayFeatures = displayFeatures,
                    isExpandedScreen = isExpandedScreen,
                    navController = navController,
                ) {
                    PreferenceNavigation(
                        navController = navController,
                        startDestination = startingRoute,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceScreen(
    currentTopRoute: PreferenceRootRoute,
    onRouteChange: (PreferenceRootRoute) -> Unit,
    useTwoPane: Boolean,
    displayFeatures: List<DisplayFeature>,
    isExpandedScreen: Boolean,
    navController: NavHostController,
    navHost: @Composable () -> Unit,
) {
    val moveableNavHost = remember { movableContentOf { navHost() } }
    when {
        useTwoPane -> {
            TwoPane(
                first = {
                    PreferencesDashboard(
                        currentRoute = currentTopRoute,
                        onNavigate = {
                            navController.navigate(it) {
                                launchSingleTop = true
                                popUpTo(navController.graph.id)
                            }
                            onRouteChange(it)
                        },
                    )
                },
                second = {
                    moveableNavHost()
                },
                strategy = HorizontalTwoPaneStrategy(splitOffset = 420.dp),
                displayFeatures = displayFeatures,
            )
        }

        isExpandedScreen -> {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.requiredWidth(640.dp),
                ) {
                    moveableNavHost()
                }
            }
        }

        else -> {
            moveableNavHost()
        }
    }
}

@Composable
private fun Providers(
    content: @Composable () -> Unit,
) {
    ProvideLifecycleState {
        ProvideBottomSheetHandler {
            content()
        }
    }
}
