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

package ch.deletescape.lawnchair.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.TextUtils
import com.android.launcher3.R
import com.android.launcher3.Utilities.EXTRA_WALLPAPER_FLAVOR
import com.android.launcher3.util.PackageManagerHelper

data class WallpaperActivityInfo(
        private val packageName: String,
        private val activityName: String?,
        val isStylesWallpapers: Boolean) {

    fun addToIntent(intent: Intent) {
        if (isStylesWallpapers) {
            intent.putExtra(EXTRA_WALLPAPER_FLAVOR, "focus_wallpaper")
        } else {
            intent.putExtra(EXTRA_WALLPAPER_FLAVOR, "wallpaper_only")
        }
        intent.`package` = packageName
        if (activityName != null) {
            intent.component = ComponentName(packageName, activityName)
        }
    }

    companion object {

        private const val STYLES_WALLPAPERS_NAME = "com.android.customization.picker.CustomizationPickerActivity"

        @JvmStatic
        fun resolve(context: Context): WallpaperActivityInfo? {
            val results = context.packageManager.queryIntentActivities(
                    Intent(Intent.ACTION_SET_WALLPAPER), PackageManager.MATCH_SYSTEM_ONLY)
            results.forEach { info ->
                val activityInfo = info.activityInfo
                if (activityInfo.name == STYLES_WALLPAPERS_NAME) {
                    return WallpaperActivityInfo(activityInfo.packageName, activityInfo.name, true)
                }
            }
            val pickerPackage = context.getString(R.string.wallpaper_picker_package)
            if (!TextUtils.isEmpty(pickerPackage) && PackageManagerHelper
                            .isAppEnabled(context.packageManager, pickerPackage, 0)) {
                return WallpaperActivityInfo(pickerPackage, null, false)
            }
            return null
        }
    }
}
