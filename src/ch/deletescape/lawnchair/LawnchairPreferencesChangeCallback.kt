package ch.deletescape.lawnchair

import ch.deletescape.lawnchair.iconpack.IconPackManager
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherModel
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.pageindicators.PageIndicatorLineCaret
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.android.launcher3.util.LooperExecutor
import com.google.android.apps.nexuslauncher.CustomIconUtils.reloadIcon

class LawnchairPreferencesChangeCallback(val launcher: LawnchairLauncher) {

    fun recreate() {
        if (launcher.shouldRecreate()) launcher.recreate()
    }

    fun reloadApps() {
        UserManagerCompat.getInstance(launcher).userProfiles.forEach { launcher.model.onPackagesReload(it) }
    }

    fun reloadAll() {
        launcher.model.forceReload()
    }

    fun restart() {
        launcher.scheduleRestart()
    }

    fun refreshGrid() {
        launcher.refreshGrid()
    }

    fun updateBlur() {
        launcher.blurWallpaperProvider.updateAsync()
    }

    fun resetAllApps() {
        launcher.mAllAppsController.reset()
    }

    fun updatePageIndicator() {
        val indicator = launcher.workspace.pageIndicator
        if (indicator is PageIndicatorLineCaret) {
            indicator.updateLineHeight()
        }
    }

    fun updateSmartspaceProvider() {
        launcher.lawnchairApp.smartspace.onProviderChanged()
    }

    fun updateSmartspace() {
        launcher.refreshGrid()
    }

    fun reloadIcons() {
        LooperExecutor(LauncherModel.getIconPackLooper()).execute {
            val userManagerCompat = UserManagerCompat.getInstance(launcher)
            val model = LauncherAppState.getInstance(launcher).model

            for (user in userManagerCompat.userProfiles) {
                model.onPackagesReload(user)
            }

            IconPackManager.getInstance(launcher).onPackChanged()

            val shortcutManager = DeepShortcutManager.getInstance(launcher)
            val launcherApps = LauncherAppsCompat.getInstance(launcher)
            userManagerCompat.userProfiles.forEach { user ->
                launcherApps.getActivityList(null, user).forEach { reloadIcon(shortcutManager, model, user, it.componentName.packageName) }
            }
        }
    }
}
