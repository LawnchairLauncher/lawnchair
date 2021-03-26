package app.lawnchair.ui.preferences

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.pm.ResolveInfo
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import app.lawnchair.util.preferences.PrefManager
import com.android.launcher3.R
import com.android.launcher3.notification.NotificationListener

class PreferenceViewModel(application: Application) : AndroidViewModel(application), PreferenceInteractor {
    private val pm = PrefManager(application)
    private val lawnchairNotificationListener = ComponentName(application, NotificationListener::class.java)
    private val enabledNotificationListeners: String? by lazy {
        Settings.Secure.getString(
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
    override val drawerOpacity: MutableState<Float> = mutableStateOf(pm.drawerOpacity)

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

    override fun setDrawerOpacity(drawerOpacity: Float) {
        pm.drawerOpacity = drawerOpacity
        this.drawerOpacity.value = drawerOpacity
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
            route = GENERAL_PREFERENCES_ROUTE,
            labelResId = R.string.general_label,
            description = description,
            iconResId = R.drawable.ic_general
        )

        class HomeScreenPreferences(description: String = "") : Screen(
            route = HOME_SCREEN_PREFERENCES_ROUTE,
            labelResId = R.string.home_screen_label,
            description = description,
            iconResId = R.drawable.ic_home_screen
        )

        object IconPackPreferences : Screen(
            route = ICON_PACK_PREFERENCES_ROUTE,
            labelResId = R.string.icon_pack
        )

        class DockPreferences(description: String = "") : Screen(
            route = DOCK_PREFERENCES_ROUTE,
            labelResId = R.string.dock_label,
            description = description,
            iconResId = R.drawable.ic_dock
        )

        class AppDrawerPreferences(description: String = "") : Screen(
            route = APP_DRAWER_PREFERENCES_ROUTE,
            labelResId = R.string.app_drawer_label,
            description = description,
            iconResId = R.drawable.ic_app_drawer
        )

        class FolderPreferences(description: String = "") : Screen(
            route = FOLDER_PREFERENCES_ROUTE,
            labelResId = R.string.folders_label,
            description = description,
            iconResId = R.drawable.ic_folder
        )

        class About(description: String = "") : Screen(
            route = ABOUT_ROUTE,
            labelResId = R.string.about_label,
            description = description,
            iconResId = R.drawable.ic_about
        )
    }
}