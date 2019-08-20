/*
 *     This file is part of Lawnchair Launcher.
 *
 *     Lawnchair Launcher is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Lawnchair Launcher is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with Lawnchair Launcher.  If not, see <https://www.gnu.org/licenses/>.
 */

package ch.deletescape.lawnchair

import ch.deletescape.lawnchair.blur.BlurWallpaperProvider
import com.android.launcher3.Utilities
import com.android.launcher3.compat.UserManagerCompat
import com.android.launcher3.pageindicators.PageIndicator
import com.android.launcher3.pageindicators.WorkspacePageIndicator

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

    fun reloadDrawer() {
        launcher.appsView.appsLists.forEach { it.reset() }
    }

    fun restart() {
        launcher.scheduleRestart()
    }

    fun refreshGrid() {
        launcher.refreshGrid()
    }

    fun updateBlur() {
        BlurWallpaperProvider.getInstance(launcher).updateAsync()
    }

    fun resetAllApps() {
        launcher.mAllAppsController.reset()
    }

    fun updatePageIndicator() {
        val indicator = launcher.workspace.pageIndicator
        if (indicator is WorkspacePageIndicator) {
            indicator.updateLineHeight()
        }
    }

    fun updateSmartspaceProvider() {
        launcher.lawnchairApp.smartspace.onProviderChanged()
    }

    fun updateSmartspace() {
        launcher.refreshGrid()
    }

    fun updateWeatherData() {
        launcher.lawnchairApp.smartspace.forceUpdateWeather()
    }
}
