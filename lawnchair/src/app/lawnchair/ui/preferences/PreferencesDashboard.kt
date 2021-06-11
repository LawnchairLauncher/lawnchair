package app.lawnchair.ui.preferences

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.lawnchair.LawnchairApp
import app.lawnchair.preferences.getMajorVersion
import app.lawnchair.ui.preferences.components.PreferenceCategory
import com.android.launcher3.R

@Composable
fun PreferencesDashboard() {
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

    if (LawnchairApp.isRecentsEnabled) {
        PreferenceCategory(
            label = stringResource(R.string.quickstep_label),
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

