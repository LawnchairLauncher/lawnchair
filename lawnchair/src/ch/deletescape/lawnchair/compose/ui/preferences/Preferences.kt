package ch.deletescape.lawnchair.compose.ui.preferences

import android.app.Application
import android.content.Intent
import android.content.pm.ResolveInfo
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ch.deletescape.lawnchair.sharedprefs.PrefManager
import com.android.launcher3.R

class PreferenceViewModel(application: Application) : AndroidViewModel(application), PreferenceInteractor {
    private val pm = PrefManager(application)

    override val iconPackPackage: MutableState<String> = mutableStateOf(pm.iconPackPackage)
    override val allowRotation: MutableState<Boolean> = mutableStateOf(pm.allowRotation)
    override val wrapAdaptiveIcons: MutableState<Boolean> = mutableStateOf(pm.wrapAdaptiveIcons)
    override val addIconToHome: MutableState<Boolean> = mutableStateOf(pm.addIconToHome)
    override val hotseatColumns: MutableState<Float> = mutableStateOf(pm.hotseatColumns)
    override val workspaceColumns: MutableState<Float> = mutableStateOf(pm.workspaceColumns)
    override val workspaceRows: MutableState<Float> = mutableStateOf(pm.workspaceRows)
    override val folderColumns: MutableState<Float> = mutableStateOf(pm.folderColumns)
    override val folderRows: MutableState<Float> = mutableStateOf(pm.folderRows)
    override val iconSizeFactor: MutableState<Float> = mutableStateOf(pm.iconSizeFactor)
    override val textSizeFactor: MutableState<Float> = mutableStateOf(pm.textSizeFactor)
    override val allAppsIconSizeFactor: MutableState<Float> = mutableStateOf(pm.allAppsIconSizeFactor)
    override val allAppsTextSizeFactor: MutableState<Float> = mutableStateOf(pm.allAppsTextSizeFactor)
    override val allAppsColumns: MutableState<Float> = mutableStateOf(pm.allAppsColumns)
    override val allowEmptyPages: MutableState<Boolean> = mutableStateOf(pm.allowEmptyPages)

    override fun setIconPackPackage(iconPackPackage: String) {
        pm.iconPackPackage = iconPackPackage
        this.iconPackPackage.value = iconPackPackage
    }

    override fun setAllowRotation(allowRotation: Boolean) {
        pm.allowRotation = allowRotation
        this.allowRotation.value = allowRotation
    }

    override fun setWrapAdaptiveIcons(wrapAdaptiveIcons: Boolean) {
        pm.wrapAdaptiveIcons = wrapAdaptiveIcons
        this.wrapAdaptiveIcons.value = wrapAdaptiveIcons
    }

    override fun setAddIconToHome(addIconToHome: Boolean) {
        pm.addIconToHome = addIconToHome
        this.addIconToHome.value = addIconToHome
    }

    override fun setHotseatColumns(hotseatColumns: Float) {
        pm.hotseatColumns = hotseatColumns
        this.hotseatColumns.value = hotseatColumns
    }

    override fun setWorkspaceColumns(workspaceColumns: Float) {
        pm.workspaceColumns = workspaceColumns
        this.workspaceColumns.value = workspaceColumns
    }

    override fun setWorkspaceRows(workspaceRows: Float) {
        pm.workspaceRows = workspaceRows
        this.workspaceRows.value = workspaceRows
    }

    override fun setFolderColumns(folderColumns: Float) {
        pm.folderColumns = folderColumns
        this.folderColumns.value = folderColumns
    }

    override fun setFolderRows(folderRows: Float) {
        pm.folderRows = folderRows
        this.folderRows.value = folderRows
    }

    override fun setIconSizeFactor(iconSizeFactor: Float) {
        pm.iconSizeFactor = iconSizeFactor
        this.iconSizeFactor.value = iconSizeFactor
    }

    override fun setTextSizeFactor(textSizeFactor: Float) {
        pm.textSizeFactor = textSizeFactor
        this.textSizeFactor.value = textSizeFactor
    }

    override fun setAllAppsIconSizeFactor(allAppsIconSizeFactor: Float) {
        pm.allAppsIconSizeFactor = allAppsIconSizeFactor
        this.allAppsIconSizeFactor.value = allAppsIconSizeFactor
    }

    override fun setAllAppsTextSizeFactor(allAppsTextSizeFactor: Float) {
        pm.allAppsTextSizeFactor = allAppsTextSizeFactor
        this.allAppsTextSizeFactor.value = allAppsTextSizeFactor
    }

    override fun setAllAppsColumns(allAppsColumns: Float) {
        pm.allAppsColumns = allAppsColumns
        this.allAppsColumns.value = allAppsColumns
    }

    override fun setAllowEmptyPages(allowEmptyPages: Boolean) {
        pm.allowEmptyPages = allowEmptyPages
        this.allowEmptyPages.value = allowEmptyPages
    }

    override fun getIconPacks(): MutableMap<String, IconPackInfo> {
        val pm = getApplication<Application>().packageManager
        val iconPacks: MutableMap<String, IconPackInfo> = HashMap()
        val list: MutableList<ResolveInfo> = pm.queryIntentActivities(Intent("com.novalauncher.THEME"), 0)

        list.addAll(pm.queryIntentActivities(Intent("org.adw.launcher.icons.ACTION_PICK_ICON"), 0))
        list.addAll(pm.queryIntentActivities(Intent("com.dlto.atom.launcher.THEME"), 0))
        list.addAll(
            pm.queryIntentActivities(Intent("android.intent.action.MAIN").addCategory("com.anddoes.launcher.THEME"), 0)
        )

        iconPacks["system"] =
            IconPackInfo("System Icons", "", AppCompatResources.getDrawable(getApplication(), R.drawable.ic_launcher_home)!!)

        for (info in list) {
            iconPacks[info.activityInfo.packageName] = IconPackInfo(
                info.loadLabel(pm).toString(),
                info.activityInfo.packageName,
                info.loadIcon(pm)
            )
        }

        return iconPacks
    }
}

sealed class Screen(
    val route: String,
    @StringRes val titleResId: Int,
    @StringRes val subtitleResId: Int? = null,
    @DrawableRes val iconResId: Int? = null
) {
    object Top : Screen(route = "top", titleResId = R.string.settings)

    object GeneralSettings : Screen(
        route = "generalSettings",
        titleResId = R.string.general_label,
        subtitleResId = R.string.general_description,
        iconResId = R.drawable.ic_general
    )

    object HomeScreenSettings : Screen(
        route = "homeScreenSettings",
        titleResId = R.string.home_screen_label,
        subtitleResId = R.string.home_screen_description,
        iconResId = R.drawable.ic_home_screen
    )

    object IconPackSettings : Screen(
        route = "iconPackSettings",
        titleResId = R.string.icon_pack
    )

    object DockSettings : Screen(
        route = "dockSettings",
        titleResId = R.string.dock_label,
        subtitleResId = R.string.dock_description,
        iconResId = R.drawable.ic_dock
    )

    object AppDrawerSettings : Screen(
        route = "appDrawerSettings",
        titleResId = R.string.app_drawer_label,
        subtitleResId = R.string.app_drawer_description,
        iconResId = R.drawable.ic_app_drawer
    )

    object FolderSettings : Screen(
        route = "folderSettings",
        titleResId = R.string.folders_label,
        subtitleResId = R.string.folders_description,
        iconResId = R.drawable.ic_folder
    )
}

val screens = listOf(
    Screen.GeneralSettings,
    Screen.HomeScreenSettings,
    Screen.DockSettings,
    Screen.AppDrawerSettings,
    Screen.FolderSettings
)

@ExperimentalAnimationApi
@Composable
fun Settings(interactor: PreferenceInteractor = viewModel<PreferenceViewModel>()) {
    val navController = rememberNavController()

    Column(
        modifier = Modifier
            .background(MaterialTheme.colors.background)
            .fillMaxWidth()
    ) {
        TopBar(navController = navController)
        NavHost(navController = navController, startDestination = Screen.Top.route) {
            composable(route = Screen.Top.route) { PreferenceCategoryList(navController) }
            composable(route = Screen.HomeScreenSettings.route) { HomeScreenPreferences(interactor = interactor) }
            composable(route = Screen.IconPackSettings.route) { IconPackPreferences(interactor = interactor) }
            composable(route = Screen.DockSettings.route) { DockPreferences(interactor = interactor) }
            composable(route = Screen.AppDrawerSettings.route) { AppDrawerPreferences(interactor = interactor) }
            composable(route = Screen.FolderSettings.route) { FolderPreferences(interactor = interactor) }
            composable(route = Screen.GeneralSettings.route) {
                GeneralPreferences(
                    navController = navController,
                    interactor = interactor
                )
            }
        }
    }
}