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

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.lawnchair.ui.preferences.destinations.PreferencesDashboard
import app.lawnchair.ui.preferences.navigation.InnerNavigation
import app.lawnchair.ui.preferences.navigation.PreferencePane
import app.lawnchair.ui.preferences.navigation.Routes
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import app.lawnchair.util.ProvideLifecycleState
import com.google.accompanist.adaptive.HorizontalTwoPaneStrategy
import com.google.accompanist.adaptive.TwoPane
import soup.compose.material.motion.animation.rememberSlideDistance

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

val LocalPreferenceInteractor = staticCompositionLocalOf<PreferenceInteractor> {
    error("CompositionLocal LocalPreferenceInteractor not present")
}

val LocalIsExpandedScreen = compositionLocalOf { false }

val twoPaneBlacklist = listOf(
    Routes.ICON_PICKER,
    Routes.SELECT_ICON,
)

@Composable
fun Preferences(
    windowSizeClass: WindowSizeClass,
    modifier: Modifier = Modifier,
    interactor: PreferenceInteractor = viewModel<PreferenceViewModel>(),
) {
    val navController = rememberNavController()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slideDistance = rememberSlideDistance()
    val isExpandedScreen = windowSizeClass.widthSizeClass == WindowWidthSizeClass.Expanded

    val currentDestination =
        navController.currentBackStackEntryAsState()
    // get parent and normal route
    val currentRoute =
        "${currentDestination.value?.destination?.parent?.route}/${currentDestination.value?.destination?.route}"

    val blacklistedRoute = twoPaneBlacklist.any { currentRoute.contains(it) }

    val useTwoPane = !blacklistedRoute && isExpandedScreen

    Providers {
        Surface(
            color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
            modifier = modifier,
        ) {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalPreferenceInteractor provides interactor,
                LocalIsExpandedScreen provides isExpandedScreen,
            ) {
                PreferenceScreen(
                    currentRoute = currentRoute,
                    useTwoPane = useTwoPane,
                    isExpandedScreen = isExpandedScreen,
                    navController = navController,
                ) {
                    InnerNavigation(
                        navController = navController,
                        isRtl = isRtl,
                        slideDistance = slideDistance,
                        isExpandedScreen = isExpandedScreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreferenceScreen(
    currentRoute: String,
    useTwoPane: Boolean,
    isExpandedScreen: Boolean,
    navController: NavHostController,
    navHost: @Composable () -> Unit,
) {
    when {
        // Assume that twopane means we are in an expanded screen
        useTwoPane -> {
            TwoPane(
                first = {
                    PreferencePane {
                        PreferencesDashboard(
                            currentRoute = currentRoute,
                            onNavigate = {
                                navController.navigate(it) {
                                    launchSingleTop = true
                                    popUpTo(navController.graph.id)
                                }
                            },
                        )
                    }
                },
                second = {
                    PreferencePane {
                        navHost()
                    }
                },
                strategy = HorizontalTwoPaneStrategy(splitOffset = 420.dp),
                displayFeatures = listOf(),
                modifier = Modifier.safeContentPadding(),
            )
        }
        isExpandedScreen -> {
            Surface(
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .safeContentPadding(),
            ) {
                PreferencePane(
                    modifier = Modifier.requiredWidth(640.dp),
                ) {
                    navHost()
                }
            }
        }
        else -> {
            navHost()
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
