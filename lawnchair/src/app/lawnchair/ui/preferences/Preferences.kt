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
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import app.lawnchair.backup.ui.createBackupGraph
import app.lawnchair.backup.ui.restoreBackupGraph
import app.lawnchair.ui.preferences.about.aboutGraph
import app.lawnchair.ui.preferences.components.SystemUi
import app.lawnchair.ui.preferences.components.colorpreference.colorSelectionGraph
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import app.lawnchair.util.ProvideLifecycleState
import com.google.accompanist.navigation.animation.AnimatedNavHost
import com.google.accompanist.navigation.animation.rememberAnimatedNavController
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut
import soup.compose.material.motion.animation.rememberSlideDistance

object Routes {
    const val GENERAL = "general"
    const val ABOUT = "about"
    const val HOME_SCREEN = "homeScreen"
    const val DOCK = "dock"
    const val APP_DRAWER = "appDrawer"
    const val FOLDERS = "folders"
    const val QUICKSTEP = "quickstep"
    const val FONT_SELECTION = "fontSelection"
    const val COLOR_SELECTION = "colorSelection"
    const val DEBUG_MENU = "debugMenu"
    const val SELECT_ICON = "selectIcon"
    const val ICON_PICKER = "iconPicker"
    const val EXPERIMENTAL_FEATURES = "experimentalFeatures"
    const val SMARTSPACE = "smartspace"
    const val SMARTSPACE_WIDGET = "smartspaceWidget"
    const val CREATE_BACKUP = "createBackup"
    const val RESTORE_BACKUP = "restoreBackup"
    const val PICK_APP_FOR_GESTURE = "pickAppForGesture"
    const val GESTURES = "gestures"
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

val LocalPreferenceInteractor = staticCompositionLocalOf<PreferenceInteractor> {
    error("CompositionLocal LocalPreferenceInteractor not present")
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun Preferences(interactor: PreferenceInteractor = viewModel<PreferenceViewModel>()) {
    val navController = rememberAnimatedNavController()
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val slideDistance = rememberSlideDistance()

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
                    enterTransition = { materialSharedAxisXIn(!isRtl, slideDistance) },
                    exitTransition = { materialSharedAxisXOut(!isRtl, slideDistance) },
                    popEnterTransition = { materialSharedAxisXIn(isRtl, slideDistance) },
                    popExitTransition = { materialSharedAxisXOut(isRtl, slideDistance) },
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
                        colorSelectionGraph(route = subRoute(Routes.COLOR_SELECTION))
                        debugMenuGraph(route = subRoute(Routes.DEBUG_MENU))
                        selectIconGraph(route = subRoute(Routes.SELECT_ICON))
                        iconPickerGraph(route = subRoute(Routes.ICON_PICKER))
                        experimentalFeaturesGraph(route = subRoute(Routes.EXPERIMENTAL_FEATURES))
                        smartspaceGraph(route = subRoute(Routes.SMARTSPACE))
                        smartspaceWidgetGraph(route = subRoute(Routes.SMARTSPACE_WIDGET))
                        createBackupGraph(route = subRoute(Routes.CREATE_BACKUP))
                        restoreBackupGraph(route = subRoute(Routes.RESTORE_BACKUP))
                        pickAppForGestureGraph(route = subRoute(Routes.PICK_APP_FOR_GESTURE))
                        gesturesGraph(route = subRoute(Routes.GESTURES))
                    }
                }
            }
        }
    }
}

@Composable
private fun Providers(
    content: @Composable () -> Unit
) {
    ProvideLifecycleState {
        ProvideBottomSheetHandler {
            content()
        }
    }
}
