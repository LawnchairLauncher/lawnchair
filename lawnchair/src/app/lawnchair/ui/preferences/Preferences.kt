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

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.lawnchair.ui.preferences.about.aboutGraph
import app.lawnchair.ui.preferences.components.SystemUi
import app.lawnchair.ui.util.portal.ProvidePortalNode
import app.lawnchair.util.ProvideLifecycleState
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import soup.compose.material.motion.materialSharedAxisX
import soup.compose.material.motion.rememberSlideDistance

object Routes {
    const val GENERAL: String = "general"
    const val ABOUT: String = "about"
    const val HOME_SCREEN: String = "homeScreen"
    const val DOCK: String = "dock"
    const val APP_DRAWER: String = "appDrawer"
    const val FOLDERS: String = "folders"
    const val QUICKSTEP: String = "quickstep"
    const val FONT_SELECTION: String = "fontSelection"
    const val DEBUG_MENU: String = "debugMenu"
    const val SELECT_ICON: String = "selectIcon"
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

val LocalPreferenceInteractor = staticCompositionLocalOf<PreferenceInteractor> {
    error("CompositionLocal LocalPreferenceInteractor not present")
}

@ExperimentalMaterialApi
@ExperimentalAnimationApi
@Composable
fun Preferences(interactor: PreferenceInteractor = viewModel<PreferenceViewModel>()) {
    val navController = rememberAnimatedNavController()
    val slideDistance = rememberSlideDistance()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val forwardSpec = materialSharedAxisX(forward = !isRtl, slideDistance = slideDistance)
    val backwardSpec = materialSharedAxisX(forward = isRtl, slideDistance = slideDistance)

    SystemUi()
    Providers {
        Surface {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalPreferenceInteractor provides interactor,
            ) {
                AnimatedNavHost(
                    navController = navController,
                    startDestination = "/",
                    enterTransition = { _, _ -> forwardSpec.enter.transition },
                    exitTransition = { _, _ -> forwardSpec.exit.transition },
                    popEnterTransition = { _, _ -> backwardSpec.enter.transition },
                    popExitTransition = { _, _ -> backwardSpec.exit.transition },
                ) {
                    preferenceGraph(route = "/", { PreferencesDashboard() }) { subRoute ->
                        generalGraph(route = subRoute(Routes.GENERAL))
                        homeScreenGraph(route = subRoute(Routes.HOME_SCREEN))
                        dockGraph(route = subRoute(Routes.DOCK))
                        appDrawerGraph(route = subRoute(Routes.APP_DRAWER))
                        folderGraph(route = subRoute(Routes.FOLDERS))
                        quickstepGraph(route = subRoute(Routes.QUICKSTEP))
                        aboutGraph(route = subRoute(Routes.ABOUT))
                        fontSelectionGraph(route = subRoute(Routes.FONT_SELECTION))
                        debugMenuGraph(route = subRoute(Routes.DEBUG_MENU))
                        selectIconGraph(route = subRoute(Routes.SELECT_ICON))
                    }
                }
            }
        }
    }
}

@Composable
private fun Providers(content: @Composable () -> Unit) {
    ProvidePortalNode {
        ProvideLifecycleState {
            content()
        }
    }
}
