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
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
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
    const val ICON_PACK: String = "iconPack"
}

fun getRoutesToLabels(context: Context) =
    mapOf(
        Routes.PREFERENCES to context.getString(R.string.settings),
        Routes.GENERAL to context.getString(R.string.general_label),
        Routes.ABOUT to context.getString(R.string.about_label),
        Routes.HOME_SCREEN to context.getString(R.string.home_screen_label),
        Routes.DOCK to context.getString(R.string.dock_label),
        Routes.APP_DRAWER to context.getString(R.string.app_drawer_label),
        Routes.FOLDERS to context.getString(R.string.folders_label),
        Routes.ICON_PACK to context.getString(R.string.icon_pack)
    )

sealed class PreferenceCategory(
    val label: String,
    val description: String,
    @DrawableRes val iconResource: Int,
    val route: String
) {
    class General(context: Context) : PreferenceCategory(
        label = getRoutesToLabels(context)[Routes.GENERAL]!!,
        description = context.getString(R.string.general_description),
        iconResource = R.drawable.ic_general,
        route = Routes.GENERAL
    )

    class HomeScreen(context: Context) : PreferenceCategory(
        label = getRoutesToLabels(context)[Routes.HOME_SCREEN]!!,
        description = context.getString(R.string.home_screen_description),
        iconResource = R.drawable.ic_home_screen,
        route = Routes.HOME_SCREEN
    )

    class Dock(context: Context) : PreferenceCategory(
        label = getRoutesToLabels(context)[Routes.DOCK]!!,
        description = context.getString(R.string.dock_description),
        iconResource = R.drawable.ic_dock,
        route = Routes.DOCK
    )

    class AppDrawer(context: Context) : PreferenceCategory(
        label = getRoutesToLabels(context)[Routes.APP_DRAWER]!!,
        description = context.getString(R.string.app_drawer_description),
        iconResource = R.drawable.ic_app_drawer,
        route = Routes.APP_DRAWER
    )

    class Folders(context: Context) : PreferenceCategory(
        label = getRoutesToLabels(context)[Routes.FOLDERS]!!,
        description = context.getString(R.string.folders_description),
        iconResource = R.drawable.ic_folder,
        route = Routes.FOLDERS
    )

    class About(context: Context) : PreferenceCategory(
        label = getRoutesToLabels(context)[Routes.ABOUT]!!,
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

@ExperimentalAnimationApi
@Composable
fun Preferences(interactor: PreferenceInteractor = viewModel<PreferenceViewModel>(), window: Window) {
    val navController = rememberNavController()

    SystemUi(window = window)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        TopBar(navController = navController)
        NavHost(navController = navController, startDestination = "preferences") {
            composable(route = Routes.PREFERENCES) { PreferenceCategoryList(navController) }
            composable(route = Routes.HOME_SCREEN) { HomeScreenPreferences(interactor = interactor) }
            composable(route = Routes.ICON_PACK) { IconPackPreferences(interactor = interactor) }
            composable(route = Routes.DOCK) { DockPreferences(interactor = interactor) }
            composable(route = Routes.APP_DRAWER) { AppDrawerPreferences(interactor = interactor) }
            composable(route = Routes.FOLDERS) { FolderPreferences(interactor = interactor) }
            composable(route = Routes.ABOUT) { About() }
            composable(route = Routes.GENERAL) {
                GeneralPreferences(
                    navController = navController,
                    interactor = interactor
                )
            }
        }
    }
}