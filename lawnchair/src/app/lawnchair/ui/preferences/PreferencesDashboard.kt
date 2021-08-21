package app.lawnchair.ui.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.preferences.getMajorVersion
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
            description = "${context.getString(R.string.derived_app_name)} ${getMajorVersion(context)}",
            iconResource = R.drawable.ic_about,
            route = Routes.ABOUT
        )
    }
}

@Composable
fun PreferencesOverflowMenu() {
    var showMenu by remember { mutableStateOf(false) }

    IconButton(onClick = { showMenu = true }) {
        Icon(Icons.Rounded.MoreVert, "")
    }
    DropdownMenu(
        expanded = showMenu,
        onDismissRequest = { showMenu = false }
    ) {
        val context = LocalContext.current
        DropdownMenuItem(onClick = {
            openAppInfo(context)
            showMenu = false
        }) {
            Text(text = stringResource(id = R.string.app_info_drop_target_label))
        }
        DropdownMenuItem(onClick = {
            restartLauncher(context)
            showMenu = false
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
