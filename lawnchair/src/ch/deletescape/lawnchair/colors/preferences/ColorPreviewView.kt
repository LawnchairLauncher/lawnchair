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

package ch.deletescape.lawnchair.colors.preferences

import android.annotation.SuppressLint
import android.content.Context
import android.support.v7.widget.AppCompatTextView
import android.util.AttributeSet
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.createRipple
import ch.deletescape.lawnchair.font.CustomFontManager

@SuppressLint("ViewConstructor")
class ColorPreviewView(context: Context, attrs: AttributeSet) : AppCompatTextView(context, attrs) {

    init {
        CustomFontManager.getInstance(context).loadCustomFont(this, attrs)
    }

    var colorResolver: ColorEngine.ColorResolver? = null
        set(value) {
            if (value == null) throw IllegalArgumentException("colorResolver must not be null")
            val foregroundColor = value.computeForegroundColor()
            background = createRipple(foregroundColor, value.resolveColor())
            setTextColor(foregroundColor)
            text = value.getDisplayName()
            field = value
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }
}
