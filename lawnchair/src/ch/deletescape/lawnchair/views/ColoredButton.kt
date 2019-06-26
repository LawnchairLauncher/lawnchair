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
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.widget.Button
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.colors.resolvers.DrawerLabelAutoResolver
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.getColorAttr
import ch.deletescape.lawnchair.getTabRipple

class ColoredButton(context: Context, attrs: AttributeSet) : Button(context, attrs),
        ColorEngine.OnColorChangeListener {

    var colorResolver: ColorEngine.ColorResolver = ColorEngine.getInstance(context).accentResolver
        set(value) {
            if (field != value) {
                field = value
                reset()
            }
        }
    var color: Int = 0

    private var defaultColor = currentTextColor

    init {
        CustomFontManager.getInstance(context).loadCustomFont(this, attrs)
    }

    fun reset() {
        color = colorResolver.resolveColor()
        setTextColor()
        setRippleColor()
    }

    private fun setTextColor() {
        val stateList = ColorStateList(arrayOf(
                intArrayOf(android.R.attr.state_selected),
                intArrayOf()),
                intArrayOf(
                        color,
                        defaultColor))
        setTextColor(stateList)
    }

    private fun setRippleColor() {
        background = RippleDrawable(getTabRipple(context, color), null, null)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ColorEngine.getInstance(context).addColorChangeListeners(this, ColorEngine.Resolvers.ALLAPPS_ICON_LABEL)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ColorEngine.getInstance(context).removeColorChangeListeners(this, ColorEngine.Resolvers.ALLAPPS_ICON_LABEL)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        when (resolveInfo.key) {
            ColorEngine.Resolvers.ALLAPPS_ICON_LABEL -> {
                defaultColor = if (DrawerLabelAutoResolver::class.java.isAssignableFrom(resolveInfo.resolverClass)) {
                    context.getColorAttr(android.R.attr.textColorTertiary)
                } else {
                    resolveInfo.color
                }
                setTextColor()
            }
        }
    }
}
