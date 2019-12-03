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

package ch.deletescape.lawnchair.sesame

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Process
import androidx.palette.graphics.Palette
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.iconpack.LawnchairIconProvider
import ch.deletescape.lawnchair.launcherAppState
import ch.deletescape.lawnchair.shortcuts.LawnchairShortcutManager
import ch.deletescape.lawnchair.toBitmap
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.icons.FixedScaleDrawable
import com.android.launcher3.shortcuts.DeepShortcutManager
import com.bumptech.glide.Glide
import ninja.sesame.lib.bridge.v1.SesameFrontend
import ninja.sesame.lib.bridge.v1.SesameShortcut
import java.lang.Exception

fun SesameShortcut.getId() = "${LawnchairShortcutManager.QUINOA_PREFIX}$id"

fun SesameShortcut.getActivity() = if (componentName != null) {
    ComponentName.unflattenFromString(componentName!!)!!
} else ComponentName(packageName ?: Sesame.PACKAGE, id)

fun SesameShortcut.getIcon(context: Context, density: Int): Drawable {
    val shortcutInfo = SesameFrontend.getShortcutInfo(context, this)
    if (shortcutInfo != null) {
        return DeepShortcutManager.getInstance(context).getShortcutIconDrawable(shortcutInfo, density)
    }
    if (iconUri != null) {
        try {
            val icn = Glide.with(context).load(iconUri).submit().get().apply {
                if (this is VectorDrawable) {
                    setTint(getAccentColor(context))
                }
            }

            return if (Utilities.ATLEAST_OREO_MR1 && icn is VectorDrawable) {
                LawnchairIconProvider.getAdaptiveIconDrawableWrapper(context).apply {
                    mutate()
                    setBounds(0, 0, 1, 1)
                    (foreground as FixedScaleDrawable).drawable = icn
                }
            } else icn
        } catch (ignored: Exception) {}
    }

    if (componentName != null) {
        try {
            return context.packageManager.getActivityIcon(ComponentName.unflattenFromString(componentName!!))
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
    }

    if (packageName != null) {
        try {
            return context.packageManager.getApplicationIcon(packageName)
        } catch (ignored: PackageManager.NameNotFoundException) {
        }
    }

    return context.resources.getDrawableForDensity(R.drawable.ic_default_shortcut, density)!!
}

private fun SesameShortcut.getAccentColor(context: Context) = colors.getOrPut(id) {
    if (packageName != null) {
        val activities = LauncherAppsCompat.getInstance(context).getActivityList(packageName, Process.myUserHandle())
        if (activities.isNotEmpty()) {
            val icon = context.launcherAppState.iconCache.getFullResIcon(activities[0]).toBitmap()
            if (icon != null) {
                val pal = Palette.from(icon).generate()
                // Return a random color from a reduced palette
                val reduced = listOfNotNull(pal.dominantSwatch,
                                            pal.vibrantSwatch,
                                            pal.darkVibrantSwatch,
                                            pal.lightVibrantSwatch)
                return@getOrPut reduced[id.hashCode() % reduced.size].rgb
            }
        }
    }
    ColorEngine.getInstance(context).accent
}

private val colors: MutableMap<String, Int> = mutableMapOf()