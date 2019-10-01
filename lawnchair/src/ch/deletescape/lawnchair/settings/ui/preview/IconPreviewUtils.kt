/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.settings.ui.preview

import android.content.Context
import android.content.res.Resources
import android.os.Process
import com.android.launcher3.AppInfo
import com.android.launcher3.R
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.util.PackageManagerHelper
import com.google.android.apps.nexuslauncher.CustomAppFilter

object IconPreviewUtils {

    private fun getPreviewPackages(resources: Resources): Array<String> {
        return resources.getStringArray(R.array.icon_shape_preview_packages)
    }

    fun getPreviewAppInfos(context: Context): List<AppInfo> {
        val launcherApps = LauncherAppsCompat.getInstance(context)
        val user = Process.myUserHandle()
        val appFilter = CustomAppFilter(context)
        val predefined = getPreviewPackages(context.resources)
                .filter { PackageManagerHelper.isAppInstalled(context.packageManager, it, 0) }
                .mapNotNull { launcherApps.getActivityList(it, user).firstOrNull() }
                .asSequence()
        val randomized = launcherApps.getActivityList(null, Process.myUserHandle())
                .shuffled()
                .asSequence()
        return (predefined + randomized)
                .filter { appFilter.shouldShowApp(it.componentName, it.user) }
                .take(20)
                .map { AppInfo(it, it.user, false) }
                .toList()
    }
}
