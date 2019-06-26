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

package ch.deletescape.lawnchair.allapps

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import ch.deletescape.lawnchair.colors.ColorEngine

abstract class PredictionsDividerLayout(context: Context, attrs: AttributeSet?)
    : LinearLayout(context, attrs), ColorEngine.OnColorChangeListener {

    private val resolverKey = ColorEngine.Resolvers.ALLAPPS_ICON_LABEL
    var allAppsLabelColor = ColorEngine.getInstance(context).getResolver(resolverKey).resolveColor()
        private set(value) {
            field = value
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ColorEngine.getInstance(context).addColorChangeListeners(this, resolverKey)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        ColorEngine.getInstance(context).removeColorChangeListeners(this, resolverKey)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == resolverKey) {
            allAppsLabelColor = resolveInfo.color
            onAllAppsLabelColorChanged()
        }
    }

    abstract fun onAllAppsLabelColorChanged()
}
