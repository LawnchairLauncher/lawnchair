package app.lawnchair.ui.preferences

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.preferences.Versioning
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.ClickableIcon
import app.lawnchair.ui.preferences.components.PreferenceCategory
import app.lawnchair.ui.preferences.components.PreferenceDivider
import app.lawnchair.ui.preferences.components.PreferenceLayout
import app.lawnchair.util.restartLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@Composable
fun PreferencesDashboard() {
    PreferenceLayout(
        label = stringResource(id = R.string.settings),
        verticalArrangement = Arrangement.Top,
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
            label = stringResource(id = R.string.smartspace_widget),
            description = stringResource(R.string.smartspace_widget_description),
            iconResource = R.drawable.ic_smartspace,
            route = Routes.SMARTSPACE,
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

        PreferenceCategory(
            label = stringResource(id = R.string.gestures_label),
            description = stringResource(R.string.gestures_description),
            iconResource = R.drawable.ic_gestures,
            route = Routes.GESTURES,
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
    val navController = LocalNavController.current
    val enableDebug by preferenceManager().enableDebugMenu.observeAsState()
    val experimentalFeaturesRoute = subRoute(name = Routes.EXPERIMENTAL_FEATURES)
    if (enableDebug) {
        val resolvedRoute = subRoute(name = Routes.DEBUG_MENU)
        ClickableIcon(
            imageVector = Icons.Rounded.Build,
            onClick = { navController.navigate(resolvedRoute) },
        )
    }
    val openRestoreBackup = restoreBackupOpener()
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
        DropdownMenuItem(onClick = {
            navController.navigate(experimentalFeaturesRoute)
            hideMenu()
        }) {
            Text(text = stringResource(id = R.string.experimental_features_label))
        }
        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))
        DropdownMenuItem(onClick = {
            navController.navigate("/${Routes.CREATE_BACKUP}/")
            hideMenu()
        }) {
            Text(text = stringResource(id = R.string.create_backup))
        }
        DropdownMenuItem(onClick = {
            openRestoreBackup()
            hideMenu()
        }) {
            Text(text = stringResource(id = R.string.restore_backup))
        }
    }
}

fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, LawnchairLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}
