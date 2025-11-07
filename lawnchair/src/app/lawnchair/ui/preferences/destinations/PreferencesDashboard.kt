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
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SettingsBackupRestore
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.lawnchair.LawnchairApp
import app.lawnchair.LawnchairLauncher
import app.lawnchair.backup.ui.restoreBackupOpener
import app.lawnchair.preferences.getAdapter
import app.lawnchair.preferences.observeAsState
import app.lawnchair.preferences.preferenceManager
import app.lawnchair.preferences2.preferenceManager2
import app.lawnchair.ui.OverflowMenu
import app.lawnchair.ui.preferences.LocalNavController
import app.lawnchair.ui.preferences.components.AnnouncementPreference
import app.lawnchair.ui.preferences.components.controls.PreferenceCategory
import app.lawnchair.ui.preferences.components.controls.WarningPreference
import app.lawnchair.ui.preferences.components.layout.ClickableIcon
import app.lawnchair.ui.preferences.components.layout.DividerColumn
import app.lawnchair.ui.preferences.components.layout.PreferenceDivider
import app.lawnchair.ui.preferences.components.layout.PreferenceGroup
import app.lawnchair.ui.preferences.components.layout.PreferenceLayout
import app.lawnchair.ui.preferences.components.layout.PreferenceTemplate
import app.lawnchair.ui.preferences.data.liveinfo.SyncLiveInformation
import app.lawnchair.ui.preferences.navigation.About
import app.lawnchair.ui.preferences.navigation.AppDrawer
import app.lawnchair.ui.preferences.navigation.CreateBackup
import app.lawnchair.ui.preferences.navigation.DebugMenu
import app.lawnchair.ui.preferences.navigation.Dock
import app.lawnchair.ui.preferences.navigation.ExperimentalFeatures
import app.lawnchair.ui.preferences.navigation.Folders
import app.lawnchair.ui.preferences.navigation.General
import app.lawnchair.ui.preferences.navigation.Gestures
import app.lawnchair.ui.preferences.navigation.HomeScreen
import app.lawnchair.ui.preferences.navigation.PreferenceRootRoute
import app.lawnchair.ui.preferences.navigation.Quickstep
import app.lawnchair.ui.preferences.navigation.Search
import app.lawnchair.ui.preferences.navigation.Smartspace
import app.lawnchair.ui.theme.isSelectedThemeDark
import app.lawnchair.ui.theme.preferenceGroupColor
import app.lawnchair.ui.util.addIf
import app.lawnchair.util.isDefaultLauncher
import app.lawnchair.util.restartLauncher
import com.android.launcher3.BuildConfig
import com.android.launcher3.R

@Composable
fun PreferencesDashboard(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    SyncLiveInformation()
    val prefs = preferenceManager()
    val prefs2 = preferenceManager2()

    val aboutDescrption = if (prefs.hideVersionInfo.get()) {
        prefs.pseudonymVersion.get()
    } else {
        "${context.getString(R.string.derived_app_name)} ${BuildConfig.MAJOR_VERSION}"
    }

    PreferenceLayout(
        label = stringResource(id = R.string.settings),
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        backArrowVisible = false,
        actions = { PreferencesOverflowMenu(currentRoute = currentRoute, onNavigate = onNavigate) },
    ) {
        AnnouncementPreference()

        if (BuildConfig.APPLICATION_ID.contains("nightly") || BuildConfig.DEBUG) {
            PreferencesDebugWarning()
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (!context.isDefaultLauncher()) {
            PreferencesSetDefaultLauncherWarning()
            Spacer(modifier = Modifier.height(8.dp))
        }

        PreferenceGroup {
            PreferenceCategory(
                label = stringResource(R.string.general_label),
                description = stringResource(R.string.general_description),
                iconResource = R.drawable.ic_general,
                onNavigate = { onNavigate(General) },
                isSelected = currentRoute is General,
            )

            PreferenceCategory(
                label = stringResource(R.string.home_screen_label),
                description = stringResource(R.string.home_screen_description),
                iconResource = R.drawable.ic_home_screen,
                onNavigate = { onNavigate(HomeScreen) },
                isSelected = currentRoute is HomeScreen,
            )

            PreferenceCategory(
                label = stringResource(id = R.string.smartspace_widget),
                description = stringResource(R.string.smartspace_widget_description),
                iconResource = R.drawable.ic_smartspace,
                onNavigate = { onNavigate(Smartspace) },
                isSelected = currentRoute is Smartspace,
            )

            PreferenceCategory(
                label = stringResource(R.string.dock_label),
                description = stringResource(R.string.dock_description),
                iconResource = R.drawable.ic_dock,
                onNavigate = { onNavigate(Dock) },
                isSelected = currentRoute is Dock,
            )

            val deckLayout = prefs2.deckLayout.getAdapter()
            if (!deckLayout.state.value) {
                PreferenceCategory(
                    label = stringResource(R.string.app_drawer_label),
                    description = stringResource(R.string.app_drawer_description),
                    iconResource = R.drawable.ic_app_drawer,
                    onNavigate = { onNavigate(AppDrawer) },
                    isSelected = currentRoute is AppDrawer,
                )
            }

            PreferenceCategory(
                label = stringResource(R.string.search_bar_label),
                description = stringResource(R.string.drawer_search_description),
                iconResource = R.drawable.ic_search,
                onNavigate = { onNavigate(Search()) },
                isSelected = currentRoute is Search,
            )

            PreferenceCategory(
                label = stringResource(R.string.folders_label),
                description = stringResource(R.string.folders_description),
                iconResource = R.drawable.ic_folder,
                onNavigate = { onNavigate(Folders) },
                isSelected = currentRoute is Folders,
            )

            PreferenceCategory(
                label = stringResource(id = R.string.gestures_label),
                description = stringResource(R.string.gestures_description),
                iconResource = R.drawable.ic_gestures,
                onNavigate = { onNavigate(Gestures) },
                isSelected = currentRoute is Gestures,
            )

            if (LawnchairApp.isRecentsEnabled || BuildConfig.DEBUG) {
                PreferenceCategory(
                    label = stringResource(id = R.string.quickstep_label),
                    description = stringResource(id = R.string.quickstep_description),
                    iconResource = R.drawable.ic_quickstep,
                    onNavigate = { onNavigate(Quickstep) },
                    isSelected = currentRoute is Quickstep,
                )
            }

            PreferenceCategory(
                label = stringResource(R.string.about_label),
                description = aboutDescrption,
                iconResource = R.drawable.ic_about,
                onNavigate = { onNavigate(About) },
                isSelected = currentRoute is About,
            )
        }
    }
}

@Composable
fun RowScope.PreferencesOverflowMenu(
    currentRoute: PreferenceRootRoute,
    onNavigate: (PreferenceRootRoute) -> Unit,
    modifier: Modifier = Modifier,
) {
    val enableDebug by preferenceManager().enableDebugMenu.observeAsState()
    val highlightColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
    val highlightShape = MaterialTheme.shapes.large

    if (enableDebug) {
        ClickableIcon(
            imageVector = Icons.Rounded.Build,
            onClick = { onNavigate(DebugMenu) },
            modifier = Modifier.addIf(currentRoute == DebugMenu) {
                Modifier
                    .clip(highlightShape)
                    .background(highlightColor)
            },
        )
    }
    val navController = LocalNavController.current
    val openCreateBackup = { navController.navigate(CreateBackup) }
    val openRestoreBackup = restoreBackupOpener()
    OverflowMenu(
        modifier = modifier.addIf(
            listOf(ExperimentalFeatures).any {
                currentRoute == it
            },
        ) {
            Modifier
                .clip(highlightShape)
                .background(highlightColor)
        },
    ) {
        val context = LocalContext.current
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    painter = painterResource(R.drawable.ic_about),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                openAppInfo(context)
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.app_info_drop_target_label))
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                restartLauncher(context)
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.debug_restart_launcher))
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                onNavigate(ExperimentalFeatures)
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.experimental_features_label))
            },
        )
        PreferenceDivider(modifier = Modifier.padding(vertical = 8.dp))
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Backup,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                openCreateBackup()
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.create_backup))
            },
        )
        DropdownMenuItem(
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.SettingsBackupRestore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = {
                openRestoreBackup()
                hideMenu()
            },
            text = {
                Text(text = stringResource(id = R.string.restore_backup))
            },
        )
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
