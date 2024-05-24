package app.lawnchair.ui.preferences.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navigation
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
import app.lawnchair.ui.preferences.destinations.DummyPreference
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
import com.android.launcher3.util.ComponentKey
import soup.compose.material.motion.animation.materialSharedAxisXIn
import soup.compose.material.motion.animation.materialSharedAxisXOut

@Composable
fun InnerNavigation(
    navController: NavHostController,
    isRtl: Boolean,
    slideDistance: Int,
    isExpandedScreen: Boolean,
) {
    NavHost(
        navController = navController,
        startDestination = if (isExpandedScreen) Routes.GENERAL else "/",
        enterTransition = { materialSharedAxisXIn(!isRtl, slideDistance) },
        exitTransition = { materialSharedAxisXOut(!isRtl, slideDistance) },
        popEnterTransition = { materialSharedAxisXIn(isRtl, slideDistance) },
        popExitTransition = { materialSharedAxisXOut(isRtl, slideDistance) },
    ) {
        composable(route = "/") {
            PreferencesDashboard(
                // Ignore as PreferenceDashboard will not be shown on navigate
                currentRoute = "",
                onNavigate = {
                    navController.navigate(it)
                },
            )
        }
        composable(route = "dummy") {
            DummyPreference()
        }

        navigation(route = Routes.GENERAL, startDestination = "main") {
            composable(route = "main") {
                GeneralPreferences()
            }
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
            composable(route = GeneralRoutes.ICON_PACK) { IconPackPreferences() }
            composable(route = GeneralRoutes.ICON_SHAPE) { IconShapePreference() }
            composable(route = IconShapeRoutes.CUSTOM_ICON_SHAPE_CREATOR) { CustomIconShapePreference() }
        }

        navigation(route = Routes.HOME_SCREEN, startDestination = "main") {
            composable(route = "main") { HomeScreenPreferences() }
            composable(route = HomeScreenRoutes.GRID) { HomeScreenGridPreferences() }
        }

        navigation(route = Routes.DOCK, startDestination = "main") {
            composable(route = "main") { DockPreferences() }
            composable(route = DockRoutes.SEARCH_PROVIDER) { SearchProviderPreferences() }
        }

        composable(route = Routes.SMARTSPACE) { SmartspacePreferences(fromWidget = false) }
        composable(route = Routes.SMARTSPACE_WIDGET) { SmartspacePreferences(fromWidget = true) }

        navigation(route = Routes.APP_DRAWER, startDestination = "main") {
            composable(route = "main") { AppDrawerPreferences() }
            composable(route = AppDrawerRoutes.HIDDEN_APPS) { HiddenAppsPreferences() }
        }

        composable(route = Routes.SEARCH) { SearchPreferences() }
        composable(route = Routes.FOLDERS) { FolderPreferences() }

        composable(route = Routes.GESTURES) { GesturePreferences() }
        composable(route = Routes.PICK_APP_FOR_GESTURE) { PickAppForGesture() }

        composable(route = Routes.QUICKSTEP) { QuickstepPreferences() }

        composable(route = Routes.ABOUT) { About() }
        composable(route = AboutRoutes.LICENSES) { Acknowledgements() }

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

        composable(route = Routes.CREATE_BACKUP) { CreateBackupScreen(viewModel()) }
        restoreBackupGraph(route = Routes.RESTORE_BACKUP)
    }
}

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
