package app.lawnchair.ui.preferences

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.provider.Settings.Secure.getString
import android.view.Window
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
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
import app.lawnchair.ui.preferences.PreferenceViewModel.Screen
import app.lawnchair.util.preferences.PrefManager
import app.lawnchair.util.preferences.getMajorVersion
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener

const val topRoute = "top"
const val generalPreferencesRoute = "generalPreferences"
const val homeScreenPreferencesRoute = "homeScreenPreferences"
const val iconPackPreferencesRoute = "iconPackPreferences"
const val dockPreferencesRoute = "dockPreferences"
const val appDrawerPreferencesRoute = "appDrawerPreferences"
const val folderPreferencesRoute = "folderPreferences"
const val aboutRoute = "about"

class PreferenceViewModel(application: Application) : AndroidViewModel(application), PreferenceInteractor {
    private val pm = PrefManager(application)
    private val lawnchairNotificationListener = ComponentName(application, NotificationListener::class.java)
    private val enabledNotificationListeners: String? by lazy {
        getString(
            application.contentResolver,
            "enabled_notification_listeners"
        )
    }

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
    override val makeColoredBackgrounds: MutableState<Boolean> = mutableStateOf(pm.makeColoredBackgrounds)
    override val notificationDotsEnabled: MutableState<Boolean> =
        mutableStateOf(enabledNotificationListeners?.contains(lawnchairNotificationListener.flattenToString()) == true)

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

    override fun setMakeColoredBackgrounds(makeColoredBackgrounds: Boolean) {
        pm.makeColoredBackgrounds = makeColoredBackgrounds
        this.makeColoredBackgrounds.value = makeColoredBackgrounds
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

    sealed class Screen(
        val route: String,
        @StringRes val labelResId: Int,
        val description: String? = null,
        @DrawableRes val iconResId: Int? = null
    ) {
        object Top : Screen(route = "top", labelResId = R.string.settings)

        class GeneralPreferences(description: String = "") : Screen(
            route = generalPreferencesRoute,
            labelResId = R.string.general_label,
            description = description,
            iconResId = R.drawable.ic_general
        )

        class HomeScreenPreferences(description: String = "") : Screen(
            route = homeScreenPreferencesRoute,
            labelResId = R.string.home_screen_label,
            description = description,
            iconResId = R.drawable.ic_home_screen
        )

        object IconPackPreferences : Screen(
            route = iconPackPreferencesRoute,
            labelResId = R.string.icon_pack
        )

        class DockPreferences(description: String = "") : Screen(
            route = dockPreferencesRoute,
            labelResId = R.string.dock_label,
            description = description,
            iconResId = R.drawable.ic_dock
        )

        class AppDrawerPreferences(description: String = "") : Screen(
            route = appDrawerPreferencesRoute,
            labelResId = R.string.app_drawer_label,
            description = description,
            iconResId = R.drawable.ic_app_drawer
        )

        class FolderPreferences(description: String = "") : Screen(
            route = folderPreferencesRoute,
            labelResId = R.string.folders_label,
            description = description,
            iconResId = R.drawable.ic_folder
        )

        class About(description: String = "") : Screen(
            route = aboutRoute,
            labelResId = R.string.about_label,
            description = description,
            iconResId = R.drawable.ic_about
        )
    }
}

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
            .background(MaterialTheme.colors.background)
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        TopBar(navController = navController)
        NavHost(navController = navController, startDestination = topRoute) {
            composable(route = topRoute) { PreferenceCategoryList(navController) }
            composable(route = homeScreenPreferencesRoute) { HomeScreenPreferences(interactor = interactor) }
            composable(route = iconPackPreferencesRoute) { IconPackPreference(interactor = interactor) }
            composable(route = dockPreferencesRoute) { DockPreferences(interactor = interactor) }
            composable(route = appDrawerPreferencesRoute) { AppDrawerPreferences(interactor = interactor) }
            composable(route = folderPreferencesRoute) { FolderPreferences(interactor = interactor) }
            composable(route = aboutRoute) { About() }
            composable(route = generalPreferencesRoute) {
                GeneralPreferences(
                    navController = navController,
                    interactor = interactor
                )
            }
        }
    }
}