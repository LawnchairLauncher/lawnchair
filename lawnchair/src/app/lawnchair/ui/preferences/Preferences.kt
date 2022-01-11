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

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.ModalBottomSheetLayout
import androidx.compose.material.ModalBottomSheetValue
import androidx.compose.material.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.lawnchair.ui.preferences.about.aboutGraph
import app.lawnchair.ui.preferences.components.SystemUi
import app.lawnchair.ui.util.BottomSheetContent
import app.lawnchair.ui.util.BottomSheetHandler
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import app.lawnchair.ui.util.emptyBottomSheetContent
import app.lawnchair.ui.util.portal.ProvidePortalNode
import app.lawnchair.util.ProvideLifecycleState
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import kotlinx.coroutines.launch
import soup.compose.material.motion.materialSharedAxisX

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
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val motionSpec = materialSharedAxisX()
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val bottomSheetState = rememberModalBottomSheetState(initialValue = ModalBottomSheetValue.Hidden)
    var bottomSheetContent by remember { mutableStateOf(emptyBottomSheetContent) }
    val bottomSheetHandler = BottomSheetHandler(
        show = { content ->
            bottomSheetContent = BottomSheetContent(content = content)
            coroutineScope.launch { bottomSheetState.show() }
        },
        hide = {
            coroutineScope.launch { bottomSheetState.hide() }
        }
    )

    SystemUi()
    Providers(bottomSheetHandler = bottomSheetHandler) {
        ModalBottomSheetLayout(
            sheetContent = {
                val isSheetShown = bottomSheetState.isAnimationRunning || bottomSheetState.isVisible
                BackHandler(enabled = isSheetShown) {
                    bottomSheetHandler.hide()
                }
                bottomSheetContent.content()
            },
            sheetState = bottomSheetState
        ) {
            Surface {
                CompositionLocalProvider(
                    LocalNavController provides navController,
                    LocalPreferenceInteractor provides interactor,
                ) {
                    AnimatedNavHost(
                        navController = navController,
                        startDestination = "/",
                        enterTransition = { motionSpec.enter.transition(!isRtl, density) },
                        exitTransition = { motionSpec.exit.transition(!isRtl, density) },
                        popEnterTransition = { motionSpec.enter.transition(isRtl, density) },
                        popExitTransition = { motionSpec.exit.transition(isRtl, density) },
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
}

@Composable
private fun Providers(
    bottomSheetHandler: BottomSheetHandler,
    content: @Composable () -> Unit
) {
    ProvidePortalNode {
        ProvideLifecycleState {
            ProvideBottomSheetHandler(handler = bottomSheetHandler) {
                content()
            }
        }
    }
}
