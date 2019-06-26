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

import android.content.Context
import android.graphics.Color
import android.graphics.PorterDuff
import android.support.v4.app.FragmentManager
import android.support.v7.preference.Preference
import android.util.AttributeSet
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.R
import me.priyesh.chroma.ColorMode


open class ColorPickerPreference(context: Context, attrs: AttributeSet?)
    : Preference(context, attrs), ColorEngine.OnColorChangeListener {

    private val engine = ColorEngine.getInstance(context)
    private val colorMode: ColorMode
    private val resolvers: Array<String>

    init {
        fragment = key
        layoutResource = R.layout.pref_with_preview_icon
        val ta = context.obtainStyledAttributes(attrs, R.styleable.ColorPickerPreference)
        colorMode = getColorMode(ta.getInt(R.styleable.ColorPickerPreference_colorMode, 0))
        resolvers = context.resources.getStringArray(ta.getResourceId(R.styleable.ColorPickerPreference_resolvers, -1))
        ta.recycle()
    }

    private fun getColorMode(mode: Int): ColorMode = when (mode) {
        0 -> ColorMode.RGB
        1 -> ColorMode.ARGB
        2 -> ColorMode.HSV
        else -> ColorMode.RGB
    }

    override fun onAttached() {
        super.onAttached()

        engine.addColorChangeListeners(this, key)
    }

    override fun onDetached() {
        super.onDetached()

        engine.removeColorChangeListeners(this, key)
    }

    override fun onColorChange(resolveInfo: ColorEngine.ResolveInfo) {
        if (resolveInfo.key == key) {
            val resolver = engine.getResolver(key)
            summary = resolver.getDisplayName()
            if (icon == null) {
                icon = context.resources.getDrawable(R.drawable.color_preview, null)
            }
            icon.setColorFilter(resolveInfo.color, PorterDuff.Mode.SRC)
        }
    }

    fun showDialog(fragmentManager: FragmentManager) {
        val resolver = engine.getResolver(key)
        ColorPickerDialog.newInstance(key, resolver.resolveColor(), colorMode, resolvers).show(fragmentManager, key)
    }
}

internal fun Array<String>.mapToResolvers(engine: ColorEngine) = map { engine.createColorResolver("PickerPreference", it) }.filter { Color.alpha(it.resolveColor()) > 0 }
