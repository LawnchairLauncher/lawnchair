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
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.lawnchair.ui.preferences.about.aboutGraph
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.ui.preferences.components.SystemUi
import app.lawnchair.ui.preferences.components.TopBar
import app.lawnchair.ui.util.portal.ProvidePortalNode
import app.lawnchair.util.Meta
import app.lawnchair.util.pageMeta
import com.android.launcher3.R

object Routes {
    const val PREFERENCES: String = "preferences"
    const val GENERAL: String = "general"
    const val ABOUT: String = "about"
    const val HOME_SCREEN: String = "homeScreen"
    const val DOCK: String = "dock"
    const val APP_DRAWER: String = "appDrawer"
    const val FOLDERS: String = "folders"
    const val QUICKSTEP: String = "quickstep"
}

val LocalNavController = compositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

val LocalPreferenceInteractor = compositionLocalOf<PreferenceInteractor> {
    error("CompositionLocal LocalPreferenceInteractor not present")
}

@ExperimentalMaterialApi
@ExperimentalAnimationApi
@Composable
fun Preferences(interactor: PreferenceInteractor = viewModel<PreferenceViewModel>()) {
    val navController = rememberNavController()

    SystemUi()
    ProvidePortalNode {
        Surface(color = MaterialTheme.colors.surface) {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalPreferenceInteractor provides interactor,
            ) {
                NavHost(navController = navController, startDestination = "preferences") {
                    composable(route = Routes.PREFERENCES) {
                        pageMeta.provide(Meta(title = stringResource(id = R.string.settings)))
                        PreferenceLayout {
                            PreferencesDashboard()
                        }
                    }
                    generalGraph(route = Routes.GENERAL)
                    homeScreenGraph(route = Routes.HOME_SCREEN)
                    dockGraph(route = Routes.DOCK)
                    appDrawerGraph(route = Routes.APP_DRAWER)
                    folderGraph(route = Routes.FOLDERS)
                    quickstepGraph(route = Routes.QUICKSTEP)
                    aboutGraph(route = Routes.ABOUT)
                }
                TopBar()
            }
        }
    }
}
