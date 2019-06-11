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

package ch.deletescape.lawnchair.preferences

import android.content.Context
import android.support.v7.preference.Preference
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.ImageView
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.colors.ColorPalette
import ch.deletescape.lawnchair.forEachIndexed
import com.android.launcher3.R

open class StyledIconPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs), ColorEngine.OnColorChangeListener {

    var count = 1
    var index = 0

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.findViewById(android.support.v7.preference.R.id.icon)?.let { it as? ImageView }?.apply {
            val size = resources.getDimensionPixelSize(R.dimen.dashboard_tile_image_size)
            layoutParams = ViewGroup.LayoutParams(size, size)
        }
    }

    override fun onAttached() {
        super.onAttached()
        parent?.forEachIndexed { i, pref ->
            if (pref.key == key) index = i
            if (pref is StyledIconPreference) count++
        }
        ColorEngine.getInstance(context).addColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == ColorEngine.Resolvers.ACCENT) {
            val palette = ColorPalette.getPalette(resolveInfo.color, count)
            icon = icon?.mutate()?.apply {
                setTint(try {
                    palette[index, true]
                } catch (ignored: Exception) {
                    resolveInfo.color
                })
            }
        }
    }

    override fun onDetached() {
        super.onDetached()
        ColorEngine.getInstance(context).removeColorChangeListeners(this, ColorEngine.Resolvers.ACCENT)
    }
}
