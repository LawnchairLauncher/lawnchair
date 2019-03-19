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

package ch.deletescape.lawnchair.views

import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.font.CustomFontManager

class ColoredButton(context: Context, attrs: AttributeSet) : Button(context, attrs) {

    private var resolved = false

    var colorResolver: ColorEngine.ColorResolver = ColorEngine.getInstance(context).accentResolver
    var color: Int = 0
        private set(value) {
            field = value
        }
        get() {
            if (!resolved) {
                field = colorResolver.resolveColor()
                resolved = true
            }
            return field
        }

    init {
        CustomFontManager.getInstance(context).loadCustomFont(this, attrs)
    }

    fun reset() {
        resolved = false
    }
}
