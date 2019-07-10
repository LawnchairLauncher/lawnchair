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

package ch.deletescape.lawnchair.sesame

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.graphics.drawable.VectorDrawable
import android.os.Process
import android.support.v7.graphics.Palette
import ch.deletescape.lawnchair.*
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.iconpack.LawnchairIconProvider
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.compat.LauncherAppsCompat
import com.android.launcher3.graphics.FixedScaleDrawable
import com.android.launcher3.shortcuts.ShortcutInfoCompat
import com.bumptech.glide.Glide
import ninja.sesame.lib.bridge.v1.SesameFrontend
import ninja.sesame.lib.bridge.v1.SesameShortcut
import java.lang.Exception

// TODO: Half of the compat work here is probably not even needed anymore now that we treat these as legacy shortcuts
class SesameShortcutInfo(private val context: Context, private val shortcut: SesameShortcut) : ShortcutInfoCompat(null) {

    override fun makeIntent() = SesameFrontend.addPackageAuth(context, shortcut.actions[0].intent).putExtra(Sesame.EXTRA_TAG, true)
    override fun getShortcutInfo() = null
    override fun getPackage() = shortcut.packageName ?: Sesame.PACKAGE
    override fun getBadgePackage(context: Context) = `package`
    override fun getId() = "sesame_${shortcut.id}"
    override fun getShortLabel() = shortcut.plainLabel
    override fun getLongLabel() = shortLabel
    override fun getActivity() = if (shortcut.componentName != null) {
        ComponentName.unflattenFromString(shortcut.componentName!!)
    } else ComponentName(`package`, shortcut.id)

    override fun getUserHandle() = Process.myUserHandle()
    // TODO: check/query if the shortcut is currently "pinned" (see ShortcutInfo#updateFromDeepShortcutInfo)
    override fun isPinned() = false
    override fun isDeclaredInManifest() = false
    override fun isEnabled() = true
    override fun isDynamic() = true
    override fun getRank() = 0
    override fun getDisabledMessage() = null
    override fun toString() = shortcut.toString()

    private val wrapperIcon by lazy { LawnchairIconProvider.getAdaptiveIconDrawableWrapper(context) }

    fun getIcon(density: Int): Drawable {
        if (shortcut.iconUri != null) {
            try {
                val icn = Glide.with(context).load(shortcut.iconUri).submit().get().apply {
                    if (this is VectorDrawable) {
                        setTint(getAccentColor())
                    }
                }

                return if (Utilities.ATLEAST_OREO_MR1 && icn is VectorDrawable) {
                    wrapperIcon.apply {
                        mutate()
                        setBounds(0, 0, 1, 1)
                        (foreground as FixedScaleDrawable).drawable = icn
                    }
                } else icn
            } catch (ignored: Exception) {}
        }

        if (shortcut.componentName != null) {
            try {
                return context.packageManager.getActivityIcon(ComponentName.unflattenFromString(shortcut.componentName!!))
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }

        if (shortcut.packageName != null) {
            try {
                return context.packageManager.getApplicationIcon(shortcut.packageName)
            } catch (ignored: PackageManager.NameNotFoundException) {
            }
        }

        return context.resources.getDrawableForDensity(R.drawable.ic_default_shortcut, density)!!
    }

    private val lac by lazy { LauncherAppsCompat.getInstance(context) }

    private fun getAccentColor() = colors.getOrPut(shortcut.id) {
        if (shortcut.packageName != null) {
            val activities = lac.getActivityList(shortcut.packageName, Process.myUserHandle())
            if (activities.isNotEmpty()) {
                val icon = context.launcherAppState.iconCache.getFullResIcon(activities[0]).toBitmap()
                if (icon != null) {
                    val pal = Palette.from(icon).generate()
                    // Return a random color from a reduced palette
                     return@getOrPut listOfNotNull(pal.dominantSwatch,
                            pal.vibrantSwatch,
                            pal.darkVibrantSwatch,
                            pal.lightVibrantSwatch).random().rgb
                }
            }
        }
        ColorEngine.getInstance(context).accent
    }

    companion object {
        @JvmStatic
        private val colors: MutableMap<String, Int> = mutableMapOf()
    }
}