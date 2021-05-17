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

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
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
import app.lawnchair.ui.preferences.components.PreferenceCategoryList
import app.lawnchair.ui.preferences.components.SystemUi
import app.lawnchair.ui.preferences.components.TopBar
import app.lawnchair.util.Meta
import app.lawnchair.util.pageMeta
import app.lawnchair.util.preferences.getMajorVersion
import com.android.launcher3.R

object Routes {
    const val PREFERENCES: String = "preferences"
    const val GENERAL: String = "general"
    const val ABOUT: String = "about"
    const val HOME_SCREEN: String = "homeScreen"
    const val DOCK: String = "dock"
    const val APP_DRAWER: String = "appDrawer"
    const val FOLDERS: String = "folders"
}

sealed class PreferenceCategory(
    val label: String,
    val description: String,
    @DrawableRes val iconResource: Int,
    val route: String
) {
    class General(context: Context) : PreferenceCategory(
        label = context.getString(R.string.general_label),
        description = context.getString(R.string.general_description),
        iconResource = R.drawable.ic_general,
        route = Routes.GENERAL
    )

    class HomeScreen(context: Context) : PreferenceCategory(
        label = context.getString(R.string.home_screen_label),
        description = context.getString(R.string.home_screen_description),
        iconResource = R.drawable.ic_home_screen,
        route = Routes.HOME_SCREEN
    )

    class Dock(context: Context) : PreferenceCategory(
        label = context.getString(R.string.dock_label),
        description = context.getString(R.string.dock_description),
        iconResource = R.drawable.ic_dock,
        route = Routes.DOCK
    )

    class AppDrawer(context: Context) : PreferenceCategory(
        label = context.getString(R.string.app_drawer_label),
        description = context.getString(R.string.app_drawer_description),
        iconResource = R.drawable.ic_app_drawer,
        route = Routes.APP_DRAWER
    )

    class Folders(context: Context) : PreferenceCategory(
        label = context.getString(R.string.folders_label),
        description = context.getString(R.string.folders_description),
        iconResource = R.drawable.ic_folder,
        route = Routes.FOLDERS
    )

    class About(context: Context) : PreferenceCategory(
        label = context.getString(R.string.about_label),
        description = "${context.getString(R.string.derived_app_name)} ${getMajorVersion(context)}",
        iconResource = R.drawable.ic_about,
        route = Routes.ABOUT
    )
}

fun getPreferenceCategories(context: Context) = listOf(
    PreferenceCategory.General(context),
    PreferenceCategory.HomeScreen(context),
    PreferenceCategory.Dock(context),
    PreferenceCategory.AppDrawer(context),
    PreferenceCategory.Folders(context),
    PreferenceCategory.About(context)
)

val LocalNavController = compositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

val LocalPreferenceInteractor = compositionLocalOf<PreferenceInteractor> {
    error("CompositionLocal LocalPreferenceInteractor not present")
}

@ExperimentalAnimationApi
@Composable
fun Preferences(interactor: PreferenceInteractor = viewModel<PreferenceViewModel>()) {
    val navController = rememberNavController()

    SystemUi()
    Surface(color = MaterialTheme.colors.background) {
        CompositionLocalProvider(
            LocalNavController provides navController,
            LocalPreferenceInteractor provides interactor,
        ) {
            NavHost(navController = navController, startDestination = "preferences") {
                composable(route = Routes.PREFERENCES) {
                    pageMeta.provide(Meta(title = stringResource(id = R.string.settings)))
                    PreferenceCategoryList(navController)
                }
                generalGraph(route = Routes.GENERAL)
                homeScreenGraph(route = Routes.HOME_SCREEN)
                dockGraph(route = Routes.DOCK)
                appDrawerGraph(route = Routes.APP_DRAWER)
                folderGraph(route = Routes.FOLDERS)
                aboutGraph(route = Routes.ABOUT)
            }
            TopBar()
        }
    }
}
