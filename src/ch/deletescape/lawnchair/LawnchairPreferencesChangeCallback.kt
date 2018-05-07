package ch.deletescape.lawnchair

import ch.deletescape.lawnchair.views.LawnchairAppWidgetHostView
import com.android.launcher3.Launcher
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.pageindicators.PageIndicatorLineCaret

class LawnchairPreferencesChangeCallback(private val launcher: Launcher) {

    fun recreate() {
        launcher.recreate()
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
        launcher.mAllAppsController.progress = 1f
    }

    fun updatePageIndicator() {
        val indicator = launcher.workspace.pageIndicator
        if (indicator is PageIndicatorLineCaret) {
            indicator.updateLineHeight()
        }
    }

    val smartspaceWidgets = HashSet<LawnchairAppWidgetHostView>()

    fun addSmartspaceWidget(hostView: LawnchairAppWidgetHostView) {
        smartspaceWidgets.add(hostView)
    }

    fun removeSmartspaceWidget(hostView: LawnchairAppWidgetHostView) {
        smartspaceWidgets.remove(hostView)
    }

    fun updateSmartspace() {
        launcher.refreshGrid()
        smartspaceWidgets.forEach { it -> it.updateText() }
    }
}
