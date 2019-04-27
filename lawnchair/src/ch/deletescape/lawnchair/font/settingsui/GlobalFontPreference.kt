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

package ch.deletescape.lawnchair.font.settingsui

import android.content.Context
import android.os.Build
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.widget.Button
import android.widget.ImageView
import android.widget.Switch
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.createColoredButtonBackground
import ch.deletescape.lawnchair.font.CustomFontManager
import ch.deletescape.lawnchair.tintDrawable
import com.android.launcher3.R

class GlobalFontPreference(context: Context, attrs: AttributeSet) : FontPreference(context, attrs) {

    private val fontManager = CustomFontManager.getInstance(context)
    private var button: Button? = null
    private var switch: Switch? = null

    init {
        layoutResource = R.layout.preference_global_font
        widgetLayoutResource = R.layout.preference_switch_widget
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        holder.itemView.isClickable = false
        holder.findViewById(R.id.clickableRow).setOnClickListener { onClick() }

        val accent = ColorEngine.getInstance(context).accent

        (holder.findViewById(android.R.id.icon) as ImageView).tintDrawable(accent)

        button = holder.findViewById(R.id.changeButton) as Button?
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M) {
            button!!.setTextColor(context.getColorStateList(R.color.btn_colored_text_material))
        }
        button!!.background = context.createColoredButtonBackground(accent)
        button!!.setOnClickListener { super.onClick() }

        switch = holder.findViewById(R.id.switchWidget) as Switch
        switch!!.applyColor(accent)
        updateUi()
    }

    override fun onClick() {
        fontManager.enableGlobalFont = !fontManager.enableGlobalFont
        updateUi()
    }

    private fun updateUi() {
        button?.isEnabled = fontManager.enableGlobalFont
        switch?.isChecked = fontManager.enableGlobalFont
        notifyDependencyChange(fontManager.enableGlobalFont)
    }

    override fun shouldDisableDependents(): Boolean {
        return fontManager.enableGlobalFont
    }
}
