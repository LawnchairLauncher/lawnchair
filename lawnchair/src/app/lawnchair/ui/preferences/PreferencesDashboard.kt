package app.lawnchair.ui.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.Versioning
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.ClickableIcon
import app.lawnchair.ui.preferences.components.PreferenceCategory
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.util.restartLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@ExperimentalAnimationApi
@Composable
fun PreferencesDashboard() {
    PreferenceLayout(
        label = stringResource(id = R.string.settings),
        backArrowVisible = false,
        actions = { PreferencesOverflowMenu() }
    ) {
        PreferenceCategory(
            label = stringResource(R.string.general_label),
            description = stringResource(R.string.general_description),
            iconResource = R.drawable.ic_general,
            route = Routes.GENERAL
        )

        PreferenceCategory(
            label = stringResource(R.string.home_screen_label),
            description = stringResource(R.string.home_screen_description),
            iconResource = R.drawable.ic_home_screen,
            route = Routes.HOME_SCREEN
        )

        PreferenceCategory(
            label = stringResource(R.string.dock_label),
            description = stringResource(R.string.dock_description),
            iconResource = R.drawable.ic_dock,
            route = Routes.DOCK
        )

        PreferenceCategory(
            label = stringResource(R.string.app_drawer_label),
            description = stringResource(R.string.app_drawer_description),
            iconResource = R.drawable.ic_app_drawer,
            route = Routes.APP_DRAWER
        )

        PreferenceCategory(
            label = stringResource(R.string.folders_label),
            description = stringResource(R.string.folders_description),
            iconResource = R.drawable.ic_folder,
            route = Routes.FOLDERS
        )

        if (LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
            PreferenceCategory(
                label = stringResource(id = R.string.quickstep_label),
                description = stringResource(id = R.string.quickstep_description),
                iconResource = R.drawable.ic_quickstep,
                route = Routes.QUICKSTEP
            )
        }

        val context = LocalContext.current
        PreferenceCategory(
            label = stringResource(R.string.about_label),
            description = "${context.getString(R.string.derived_app_name)} ${Versioning.majorVersion}",
            iconResource = R.drawable.ic_about,
            route = Routes.ABOUT
        )
    }
}

@Composable
fun PreferencesOverflowMenu() {
    val enableDebug by preferenceManager().enableDebugMenu.observeAsState()
    if (enableDebug) {
        val navController = LocalNavController.current
        val resolvedRoute = subRoute(name = Routes.DEBUG_MENU)
        ClickableIcon(
            imageVector = Icons.Rounded.Build,
            onClick = { navController.navigate(resolvedRoute) },
        )
    }
    OverflowMenu {
        val context = LocalContext.current
        DropdownMenuItem(onClick = {
            openAppInfo(context)
            hideMenu()
        }) {
            Text(text = stringResource(id = R.string.app_info_drop_target_label))
        }
        DropdownMenuItem(onClick = {
            restartLauncher(context)
            hideMenu()
        }) {
            Text(text = stringResource(id = R.string.debug_restart_launcher))
        }
    }
}

private fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, LawnchairLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}
