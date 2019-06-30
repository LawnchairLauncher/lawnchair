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

package ch.deletescape.lawnchair.animations

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import ch.deletescape.lawnchair.getColorAttr
import ch.deletescape.lawnchair.getDrawableAttrNullable
import ch.deletescape.lawnchair.getIntAttr
import ch.deletescape.lawnchair.settings.ui.SettingsActivity
import ch.deletescape.lawnchair.theme.ThemeOverride
import ch.deletescape.lawnchair.util.LawnchairSingletonHolder
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.BuildConfig
import com.android.launcher3.Utilities

class SplashResolver(private val context: Context) {

    fun loadSplash(intent: Intent): SplashData {
        val activityInfo = intent.resolveActivityInfo(context.packageManager, 0)
        val themedContext: Context
        themedContext = if (activityInfo == null
                            || (activityInfo.packageName == BuildConfig.APPLICATION_ID
                            && activityInfo.name == SettingsActivity::class.java.name)) {
            ContextThemeWrapper(context, ThemeOverride.Settings().getTheme(context))
        } else {
            val theme = activityInfo.themeResource
            val packageContext = context.createPackageContext(activityInfo.packageName, 0)
            ContextThemeWrapper(packageContext, theme)
        }
        val layoutInDisplayCutoutMode = if (Utilities.ATLEAST_P)
            themedContext.getIntAttr(android.R.attr.windowLayoutInDisplayCutoutMode) else 0
        return SplashData(
                themedContext.getDrawableAttrNullable(android.R.attr.windowBackground),
                themedContext.getColorAttr(android.R.attr.statusBarColor),
                themedContext.getColorAttr(android.R.attr.navigationBarColor),
                layoutInDisplayCutoutMode)
    }

    data class SplashData(val background: Drawable?, val statusColor: Int, val navColor: Int,
                          val layoutInDisplayCutoutMode: Int) {

        init {
            dump()
        }

        fun dump() {
            d("statusColor: ${String.format("%08x", statusColor)}")
            d("navColor: ${String.format("%08x", navColor)}")
            d("layoutInDisplayCutoutMode: $layoutInDisplayCutoutMode")
        }
    }

    companion object : LawnchairSingletonHolder<SplashResolver>(::SplashResolver)
}
