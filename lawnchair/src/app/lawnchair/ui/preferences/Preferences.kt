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

import androidx.compose.material3.Surface
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.lawnchair.backup.ui.CreateBackupScreen
import app.lawnchair.backup.ui.restoreBackupGraph
import app.lawnchair.preferences.BasePreferenceManager
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.preferences.about.About
import app.lawnchair.ui.preferences.about.AboutRoutes
import app.lawnchair.ui.preferences.about.acknowledgements.Acknowledgements
import app.lawnchair.ui.preferences.components.colorpreference.ColorPreferenceModelList
import app.lawnchair.ui.preferences.components.colorpreference.ColorSelection
import app.lawnchair.ui.preferences.destinations.AppDrawerPreferences
import app.lawnchair.ui.preferences.destinations.AppDrawerRoutes
import app.lawnchair.ui.preferences.destinations.CustomIconShapePreference
import app.lawnchair.ui.preferences.destinations.DebugMenuPreferences
import app.lawnchair.ui.preferences.destinations.DockPreferences
import app.lawnchair.ui.preferences.destinations.DockRoutes
import app.lawnchair.ui.preferences.destinations.ExperimentalFeaturesPreferences
import app.lawnchair.ui.preferences.destinations.FolderPreferences
import app.lawnchair.ui.preferences.destinations.FontSelection
import app.lawnchair.ui.preferences.destinations.GeneralPreferences
import app.lawnchair.ui.preferences.destinations.GeneralRoutes
import app.lawnchair.ui.preferences.destinations.GesturePreferences
import app.lawnchair.ui.preferences.destinations.HiddenAppsPreferences
import app.lawnchair.ui.preferences.destinations.HomeScreenGridPreferences
import app.lawnchair.ui.preferences.destinations.HomeScreenPreferences
import app.lawnchair.ui.preferences.destinations.HomeScreenRoutes
import app.lawnchair.ui.preferences.destinations.IconPackPreferences
import app.lawnchair.ui.preferences.destinations.IconPickerPreference
import app.lawnchair.ui.preferences.destinations.IconShapePreference
import app.lawnchair.ui.preferences.destinations.IconShapeRoutes
import app.lawnchair.ui.preferences.destinations.PickAppForGesture
import app.lawnchair.ui.preferences.destinations.PreferencesDashboard
import app.lawnchair.ui.preferences.destinations.QuickstepPreferences
import app.lawnchair.ui.preferences.destinations.SearchPreferences
import app.lawnchair.ui.preferences.destinations.SearchProviderPreferences
import app.lawnchair.ui.preferences.destinations.SelectIconPreference
import app.lawnchair.ui.preferences.destinations.SmartspacePreferences
import app.lawnchair.ui.util.ProvideBottomSheetHandler
import app.lawnchair.util.ProvideLifecycleState
import com.android.launcher3.util.ComponentKey
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
    const val SEARCH = "search"
}

val LocalNavController = staticCompositionLocalOf<NavController> {
    error("CompositionLocal LocalNavController not present")
}

val LocalPreferenceInteractor = staticCompositionLocalOf<PreferenceInteractor> {
    error("CompositionLocal LocalPreferenceInteractor not present")
}

val LocalIsExpandedScreen = compositionLocalOf { false }

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

    Providers {
        Surface(
            modifier = modifier,
        ) {
            CompositionLocalProvider(
                LocalNavController provides navController,
                LocalPreferenceInteractor provides interactor,
                LocalIsExpandedScreen provides isExpandedScreen,
            ) {
                NavHost(
                    navController = navController,
                    startDestination = "/",
                    enterTransition = { materialSharedAxisXIn(!isRtl, slideDistance) },
                    exitTransition = { materialSharedAxisXOut(!isRtl, slideDistance) },
                    popEnterTransition = { materialSharedAxisXIn(isRtl, slideDistance) },
                    popExitTransition = { materialSharedAxisXOut(isRtl, slideDistance) },
                ) {
                    composable(route = "/") { PreferencesDashboard() }
                    composable(
                        route = "${Routes.COLOR_SELECTION}/{prefKey}",
                        arguments = listOf(
                            navArgument("prefKey") { type = NavType.StringType },
                        ),
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments!!
                        val prefKey = args.getString("prefKey")!!
                        val modelList = ColorPreferenceModelList.INSTANCE.get(LocalContext.current)
                        val model = modelList[prefKey]
                        ColorSelection(
                            label = stringResource(id = model.labelRes),
                            preference = model.prefObject,
                            dynamicEntries = model.dynamicEntries,
                        )
                    }
                    composable(route = Routes.GENERAL) { GeneralPreferences() }
                    composable(route = GeneralRoutes.ICON_PACK) { IconPackPreferences() }
                    composable(route = GeneralRoutes.ICON_SHAPE) { IconShapePreference() }
                    composable(route = IconShapeRoutes.CUSTOM_ICON_SHAPE_CREATOR) { CustomIconShapePreference() }
                    composable(route = Routes.HOME_SCREEN) { HomeScreenPreferences() }
                    composable(route = HomeScreenRoutes.GRID) { HomeScreenGridPreferences() }
                    composable(route = Routes.DOCK) { DockPreferences() }
                    composable(route = DockRoutes.SEARCH_PROVIDER) { SearchProviderPreferences() }
                    composable(route = Routes.APP_DRAWER) { AppDrawerPreferences() }
                    composable(route = AppDrawerRoutes.HIDDEN_APPS) { HiddenAppsPreferences() }
                    composable(route = Routes.SEARCH) { SearchPreferences() }
                    composable(route = Routes.FOLDERS) { FolderPreferences() }
                    composable(route = Routes.QUICKSTEP) { QuickstepPreferences() }
                    composable(route = Routes.ABOUT) { About() }
                    composable(route = AboutRoutes.LICENSES) { Acknowledgements() }
                    composable(
                        route = "${Routes.FONT_SELECTION}/{prefKey}",
                        arguments = listOf(
                            navArgument("prefKey") { type = NavType.StringType },
                        ),
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments!!
                        val prefKey = args.getString("prefKey")!!
                        val pref = preferenceManager().prefsMap[prefKey]
                            as? BasePreferenceManager.FontPref ?: return@composable
                        FontSelection(pref)
                    }
                    composable(route = Routes.DEBUG_MENU) { DebugMenuPreferences() }
                    composable(
                        route = "${Routes.SELECT_ICON}/{packageName}/{nameAndUser}/",
                        arguments = listOf(
                            navArgument("packageName") { type = NavType.StringType },
                            navArgument("nameAndUser") { type = NavType.StringType },
                        ),
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments!!
                        val packageName = args.getString("packageName")
                        val nameAndUser = args.getString("nameAndUser")
                        val key = ComponentKey.fromString("$packageName/$nameAndUser")!!
                        SelectIconPreference(key)
                    }
                    composable(route = Routes.ICON_PICKER) { IconPickerPreference(packageName = "") }
                    composable(
                        route = "${Routes.ICON_PICKER}/{packageName}",
                        arguments = listOf(
                            navArgument("packageName") { type = NavType.StringType },
                        ),
                    ) { backStackEntry ->
                        val args = backStackEntry.arguments!!
                        val packageName = args.getString("packageName")!!
                        IconPickerPreference(packageName)
                    }
                    composable(route = Routes.EXPERIMENTAL_FEATURES) { ExperimentalFeaturesPreferences() }
                    composable(route = Routes.SMARTSPACE) { SmartspacePreferences(fromWidget = false) }
                    composable(route = Routes.SMARTSPACE_WIDGET) { SmartspacePreferences(fromWidget = true) }
                    composable(route = Routes.CREATE_BACKUP) { CreateBackupScreen(viewModel()) }
                    restoreBackupGraph(route = Routes.RESTORE_BACKUP)
                    composable(route = Routes.PICK_APP_FOR_GESTURE) { PickAppForGesture() }
                    composable(route = Routes.GESTURES) { GesturePreferences() }
                }
            }
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
