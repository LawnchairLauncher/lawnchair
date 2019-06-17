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

package ch.deletescape.lawnchair.util

import android.content.Context
import android.view.ContextThemeWrapper
import ch.deletescape.lawnchair.theme.ThemeManager
import ch.deletescape.lawnchair.theme.ThemeOverride

class ThemedContextProvider(private val base: Context, var listener: Listener?, themeSet: ThemeOverride.ThemeSet)
    : ThemeOverride.ThemeOverrideListener {

    override val isAlive = true

    private val themeOverride = ThemeOverride(themeSet, this)

    private var currentTheme = themeOverride.getTheme(base)
        set(value) {
            if (field != value) {
                field = value
                themedContext = ContextThemeWrapper(base, value)
                listener?.onThemeChanged()
            }
        }
    private var themedContext = ContextThemeWrapper(base, currentTheme)

    fun startListening() {
        ThemeManager.getInstance(base).addOverride(themeOverride)
    }

    fun stopListening() {
        ThemeManager.getInstance(base).removeOverride(themeOverride)
    }

    override fun reloadTheme() {
        currentTheme = themeOverride.getTheme(base)
    }

    override fun applyTheme(themeRes: Int) {
        currentTheme = themeOverride.getTheme(base)
    }

    fun get() = themedContext

    interface Listener {

        fun onThemeChanged()
    }
}
