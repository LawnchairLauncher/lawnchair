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

package ch.deletescape.lawnchair.colors.resolvers

import android.support.annotation.Keep
import android.support.v4.graphics.ColorUtils
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.colors.ThemeAttributeColorResolver
import ch.deletescape.lawnchair.colors.WallpaperColorResolver
import ch.deletescape.lawnchair.theme.ThemeManager
import com.android.launcher3.R
import com.android.launcher3.util.Themes

@Keep
class DrawerQsbAutoResolver(config: Config) : ColorEngine.ColorResolver(config),  LawnchairPreferences.OnPreferenceChangeListener {

    private val isDark get() =  ThemeManager.getInstance(engine.context).isDark
    private val lightResolver = DrawerQsbLightResolver(Config("DrawerQsbAutoResolver@Light", engine, {
        _, _ -> if (!isDark) notifyChanged()
    }))
    private val darkResolver = DrawerQsbDarkResolver(Config("DrawerQsbAutoResolver@Dark", engine, {
        _, _ -> if (isDark) notifyChanged()
    }))

    override fun startListening() {
        super.startListening()
        LawnchairPreferences.getInstanceNoCreate().addOnPreferenceChangeListener(this, "pref_launcherTheme")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        notifyChanged()
    }

    override fun stopListening() {
        super.stopListening()
        LawnchairPreferences.getInstanceNoCreate().removeOnPreferenceChangeListener(this, "pref_launcherTheme")
    }

    override fun resolveColor() = if (isDark) darkResolver.resolveColor() else lightResolver.resolveColor()

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_based)
}

@Keep
class DrawerQsbLightResolver(config: Config) : WallpaperColorResolver(config), LawnchairPreferences.OnPreferenceChangeListener {

    override val themeAware = true
    private val isDark get() = ThemeManager.getInstance(engine.context).isDark

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        notifyChanged()
    }

    override fun resolveColor() = engine.context.resources.getColor(
            if (isDark)
                R.color.qsb_background_drawer_dark
            else
                R.color.qsb_background_drawer_default
    ).let {
        ColorUtils.compositeColors(ColorUtils
                .compositeColors(it, Themes.getAttrColor(launcherThemeContext, R.attr.allAppsScrimColor)),
                colorInfo.mainColor)
    }

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_light)
}

@Keep
class DrawerQsbDarkResolver(config: Config) : WallpaperColorResolver(config) {

    override val themeAware = true
    val color = engine.context.resources.getColor(R.color.qsb_background_drawer_dark_bar)

    override fun resolveColor() = ColorUtils.compositeColors(ColorUtils
            .compositeColors(color, Themes.getAttrColor(launcherThemeContext, R.attr.allAppsScrimColor)),
            colorInfo.mainColor)

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_dark)
}

@Keep
class ShelfBackgroundAutoResolver(config: Config) : ThemeAttributeColorResolver(config) {

    override val colorAttr = R.attr.allAppsScrimColor
}
