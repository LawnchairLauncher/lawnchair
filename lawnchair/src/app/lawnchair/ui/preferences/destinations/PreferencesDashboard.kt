package app.lawnchair.ui.preferences.destinations

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.TipsAndUpdates
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.components.AnnouncementPreference
import app.lawnchair.ui.preferences.components.controls.PreferenceCategory
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.data.liveinfo.SyncLiveInformation
import app.lawnchair.ui.preferences.navigation.Routes
import app.lawnchair.ui.util.addIf
import app.lawnchair.util.isDefaultLauncher
import app.lawnchair.util.restartLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@Composable
fun PreferencesDashboard(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SyncLiveInformation()

    PreferenceLayout(
        label = stringResource(id = R.string.settings),
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        backArrowVisible = false,
        actions = { PreferencesOverflowMenu(currentRoute = currentRoute, onNavigate = onNavigate) },
    ) {
        AnnouncementPreference()

        if (BuildConfig.DEBUG) {
            PreferencesDebugWarning()
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!context.isDefaultLauncher()) {
            PreferencesSetDefaultLauncherWarning()
            Spacer(modifier = Modifier.height(16.dp))
        }

        PreferenceCategory(
            label = stringResource(R.string.general_label),
            description = stringResource(R.string.general_description),
            iconResource = R.drawable.ic_general,
            onNavigate = { onNavigate(Routes.GENERAL) },
            isSelected = currentRoute.contains(Routes.GENERAL),
        )

        PreferenceCategory(
            label = stringResource(R.string.home_screen_label),
            description = stringResource(R.string.home_screen_description),
            iconResource = R.drawable.ic_home_screen,
            onNavigate = { onNavigate(Routes.HOME_SCREEN) },
            isSelected = currentRoute.contains(Routes.HOME_SCREEN),
        )

        PreferenceCategory(
            label = stringResource(id = R.string.smartspace_widget),
            description = stringResource(R.string.smartspace_widget_description),
            iconResource = R.drawable.ic_smartspace,
            onNavigate = { onNavigate(Routes.SMARTSPACE) },
            isSelected = currentRoute.contains(Routes.SMARTSPACE),
        )

        PreferenceCategory(
            label = stringResource(R.string.dock_label),
            description = stringResource(R.string.dock_description),
            iconResource = R.drawable.ic_dock,
            onNavigate = { onNavigate(Routes.DOCK) },
            isSelected = currentRoute.contains(Routes.DOCK),
        )

        PreferenceCategory(
            label = stringResource(R.string.app_drawer_label),
            description = stringResource(R.string.app_drawer_description),
            iconResource = R.drawable.ic_app_drawer,
            onNavigate = { onNavigate(Routes.APP_DRAWER) },
            isSelected = currentRoute.contains(Routes.APP_DRAWER),
        )

        PreferenceCategory(
            label = stringResource(R.string.drawer_search_label),
            description = stringResource(R.string.drawer_search_description),
            iconResource = R.drawable.ic_search,
            onNavigate = { onNavigate(Routes.SEARCH) },
            isSelected = currentRoute.contains(Routes.SEARCH),
        )

        PreferenceCategory(
            label = stringResource(R.string.folders_label),
            description = stringResource(R.string.folders_description),
            iconResource = R.drawable.ic_folder,
            onNavigate = { onNavigate(Routes.FOLDERS) },
            isSelected = currentRoute.contains(Routes.FOLDERS),
        )

        PreferenceCategory(
            label = stringResource(id = R.string.gestures_label),
            description = stringResource(R.string.gestures_description),
            iconResource = R.drawable.ic_gestures,
            onNavigate = { onNavigate(Routes.GESTURES) },
            isSelected = currentRoute.contains(Routes.GESTURES),
        )

        if (LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
            PreferenceCategory(
                label = stringResource(id = R.string.quickstep_label),
                description = stringResource(id = R.string.quickstep_description),
                iconResource = R.drawable.ic_quickstep,
                onNavigate = { onNavigate(Routes.QUICKSTEP) },
                isSelected = currentRoute.contains(Routes.QUICKSTEP),
            )
        }

        PreferenceCategory(
            label = stringResource(R.string.about_label),
            description = "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}",
            iconResource = R.drawable.ic_about,
            onNavigate = { onNavigate(Routes.ABOUT) },
            isSelected = currentRoute.contains(Routes.ABOUT),
        )
    }
}

@Composable
fun RowScope.PreferencesOverflowMenu(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enableDebug by preferenceManager().enableDebugMenu.observeAsState()
    val highlightColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    val highlightShape = MaterialTheme.shapes.large

    val experimentalFeaturesRoute = Routes.EXPERIMENTAL_FEATURES
    if (enableDebug) {
        val resolvedRoute = Routes.DEBUG_MENU
        ClickableIcon(
            imageVector = Icons.Rounded.Build,
            onClick = { onNavigate(resolvedRoute) },
            modifier = Modifier.addIf(currentRoute.contains(resolvedRoute)) {
                Modifier
                    .clip(highlightShape)
                    .background(highlightColor)
            },
        )
    }
    val openRestoreBackup = restoreBackupOpener()
    OverflowMenu(
        modifier = modifier.addIf(
            listOf(Routes.CREATE_BACKUP, Routes.RESTORE_BACKUP, experimentalFeaturesRoute).any {
                currentRoute.contains(it)
            },
        ) {
            Modifier
                .clip(highlightShape)
                .background(highlightColor)
        },
    ) {
        val context = LocalContext.current
        DropdownMenuItem(onClick = {
            openAppInfo(context)
            hideMenu()
        }, text = {
            Text(text = stringResource(id = R.string.app_info_drop_target_label))
        })
        DropdownMenuItem(onClick = {
            restartLauncher(context)
            hideMenu()
        }, text = {
            Text(text = stringResource(id = R.string.debug_restart_launcher))
        })
        DropdownMenuItem(onClick = {
            onNavigate(experimentalFeaturesRoute)
            hideMenu()
        }, text = {
            Text(text = stringResource(id = R.string.experimental_features_label))
        })
        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))
        DropdownMenuItem(onClick = {
            onNavigate(Routes.CREATE_BACKUP)
            hideMenu()
        }, text = {
            Text(text = stringResource(id = R.string.create_backup))
        })
        DropdownMenuItem(onClick = {
            openRestoreBackup()
            hideMenu()
        }, text = {
            Text(text = stringResource(id = R.string.restore_backup))
        })
    }
}

@Composable
fun PreferencesDebugWarning(
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        WarningPreference(
            // Don't move to strings.xml, no need to translate this warning
            text = "You are using a development build, which may contain bugs and broken features. Use at your own risk!",
        )
    }
}

@Composable
fun PreferencesSetDefaultLauncherWarning(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.padding(horizontal = 16.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        PreferenceTemplate(
            modifier = Modifier.clickable {
                Intent(Settings.ACTION_HOME_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .let { context.startActivity(it) }
                (context as? Activity)?.finish()
            },
            title = {},
            description = {
                Text(
                    text = stringResource(id = R.string.set_default_launcher_tip),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            startWidget = {
                Icon(
                    imageVector = Icons.Rounded.TipsAndUpdates,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    contentDescription = null,
                )
            },
        )
    }
}

fun openAppInfo(context: Context) {
    val launcherApps = context.getSystemService<LauncherApps>()
    val componentName = ComponentName(context, LawnchairLauncher::class.java)
    launcherApps?.startAppDetailsActivity(componentName, Process.myUserHandle(), null, null)
}
