package app.lawnchair.ui.preferences

import android.content.Context
import android.view.Window
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.lawnchair.ui.preferences.PreferenceViewModel.Screen
import app.lawnchair.util.preferences.getMajorVersion
import com.android.launcher3.R

const val TOP_ROUTE = "top"
const val GENERAL_PREFERENCES_ROUTE = "generalPreferences"
const val HOME_SCREEN_PREFERENCES_ROUTE = "homeScreenPreferences"
const val ICON_PACK_PREFERENCES_ROUTE = "iconPackPreferences"
const val DOCK_PREFERENCES_ROUTE = "dockPreferences"
const val APP_DRAWER_PREFERENCES_ROUTE = "appDrawerPreferences"
const val FOLDER_PREFERENCES_ROUTE = "folderPreferences"
const val ABOUT_ROUTE = "about"

fun screens(context: Context) = listOf(
    Screen.GeneralPreferences(context.getString(R.string.general_description)),
    Screen.HomeScreenPreferences(context.getString(R.string.home_screen_description)),
    Screen.DockPreferences(context.getString(R.string.dock_description)),
    Screen.AppDrawerPreferences(context.getString(R.string.app_drawer_description)),
    Screen.FolderPreferences(context.getString(R.string.folders_description)),
    Screen.About("${context.getString(R.string.derived_app_name)} ${getMajorVersion(context)}")
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
        NavHost(navController = navController, startDestination = TOP_ROUTE) {
            composable(route = TOP_ROUTE) { PreferenceCategoryList(navController) }
            composable(route = HOME_SCREEN_PREFERENCES_ROUTE) { HomeScreenPreferences(interactor = interactor) }
            composable(route = ICON_PACK_PREFERENCES_ROUTE) { IconPackPreference(interactor = interactor) }
            composable(route = DOCK_PREFERENCES_ROUTE) { DockPreferences(interactor = interactor) }
            composable(route = APP_DRAWER_PREFERENCES_ROUTE) { AppDrawerPreferences(interactor = interactor) }
            composable(route = FOLDER_PREFERENCES_ROUTE) { FolderPreferences(interactor = interactor) }
            composable(route = ABOUT_ROUTE) { About() }
            composable(route = GENERAL_PREFERENCES_ROUTE) {
                GeneralPreferences(
                    navController = navController,
                    interactor = interactor
                )
            }
        }
    }
}