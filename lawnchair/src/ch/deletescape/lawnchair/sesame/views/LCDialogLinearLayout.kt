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

package ch.deletescape.lawnchair.sesame.views

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.widget.LinearLayout
import ch.deletescape.lawnchair.theme.ThemeOverride
import com.android.launcher3.R
import com.android.launcher3.util.Themes

/**
 * Simple dialog linear layout class to use in externally displayed layouts where we can't programmatically change the design otherwise
 */
class LCDialogLinearLayout(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs) {

    private val themeSet: ThemeOverride.ThemeSet get() = ThemeOverride.SettingsTransparent()

    init {
        ThemeOverride(themeSet, context).applyTheme(context)
        backgroundTintList = ColorStateList.valueOf(Themes.getAttrColor(context, R.attr.settingsBackground))
    }
}