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
import android.support.v4.content.ContextCompat
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.theme.ThemeManager
import com.android.launcher3.R

@Keep
class SuperGAutoResolver(config: Config) : ColorEngine.ColorResolver(config) {

    override val themeAware = true
    private val isDark get() = ThemeManager.getInstance(engine.context).isDark

    override fun resolveColor(): Int {
        return ContextCompat.getColor(engine.context,
                if (isDark) R.color.qsb_background_dark else R.color.qsb_background)
    }

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_based)
}

@Keep
class SuperGLightResolver(config: Config) : ColorEngine.ColorResolver(config) {

    override fun resolveColor() = ContextCompat.getColor(engine.context, R.color.qsb_background)

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_light)
}

@Keep
class SuperGDarkResolver(config: Config) : ColorEngine.ColorResolver(config) {

    override fun resolveColor() = ContextCompat.getColor(engine.context, R.color.qsb_background_dark)

    override fun getDisplayName() = engine.context.resources.getString(R.string.theme_dark)
}
