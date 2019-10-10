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

package ch.deletescape.lawnchair.wallpaper

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import ch.deletescape.lawnchair.theme.ThemeOverride
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import com.android.launcher3.util.Themes
import java.lang.ref.WeakReference

class WallpaperPreviewProvider(private val context: Context) : BroadcastReceiver() {

    private val wallpaperManager = WallpaperManager.getInstance(context)
    private var loadedWallpaper: WeakReference<Drawable>? = null
        set(value) {
            field = value
            registered = field != null
        }
    private var registered = false
        set(value) {
            if (field != value) {
                field = value
                if (value) {
                    context.registerReceiver(this, IntentFilter(Intent.ACTION_WALLPAPER_CHANGED))
                } else {
                    context.unregisterReceiver(this)
                }
            }
        }

    val wallpaper: Drawable
        get() = loadWallpaper()

    private fun loadWallpaper(): Drawable {
        val loaded = loadedWallpaper?.get()
        if (loaded != null) {
            return loaded
        }

        val info = wallpaperManager.wallpaperInfo
        val drawable = if (info != null) {
            info.loadThumbnail(context.packageManager)
        } else {
            wallpaperManager.drawable
        }
        loadedWallpaper = WeakReference(drawable)
        return drawable ?: loadEmptyBackground()
    }

    private fun loadEmptyBackground(): Drawable {
        val themedContext = ContextThemeWrapper(context, ThemeOverride.Launcher().getTheme(context))
        return Themes.getAttrDrawable(themedContext, android.R.attr.windowBackground)
    }

    override fun onReceive(context: Context, intent: Intent?) {
        loadedWallpaper = null
    }

    companion object : LawnchairSingletonHolder<WallpaperPreviewProvider>(::WallpaperPreviewProvider)
}
