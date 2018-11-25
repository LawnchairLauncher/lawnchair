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
import android.content.res.ColorStateList
import android.support.v14.preference.SwitchPreference
import android.support.v4.graphics.ColorUtils
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.preference.AndroidResources
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Switch
import ch.deletescape.lawnchair.colors.ColorEngine
import ch.deletescape.lawnchair.settings.ui.ControlledPreference
import ch.deletescape.lawnchair.settings.ui.PreferenceController
import com.android.launcher3.util.Themes

open class StyledSwitchPreferenceCompat(context: Context, attrs: AttributeSet?) :
        SwitchPreference(context, attrs), ColorEngine.OnAccentChangeListener, ControlledPreference {

    private val normalLight = android.support.v7.preference.R.color.switch_thumb_normal_material_light
    private val disabledLight = android.support.v7.appcompat.R.color.switch_thumb_disabled_material_light
    private var checkableView: View? = null

    private val delegate = ControlledPreference.Delegate(context)

    override val controller get() = delegate.controller

    init {
        delegate.parseAttributes(attrs)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder?) {
        super.onBindViewHolder(holder)
        checkableView = holder?.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET)
        ColorEngine.getInstance(context).addAccentChangeListener(this)
    }

    override fun onAccentChange(color: Int, foregroundColor: Int) {
        if (checkableView is Switch) {
            val colorForeground = Themes.getAttrColor(context, android.R.attr.colorForeground)
            val alphaDisabled = Themes.getAlpha(context, android.R.attr.disabledAlpha)
            val switchThumbNormal = context.resources.getColor(normalLight)
            val switchThumbDisabled = context.resources.getColor(disabledLight)
            val thstateList = ColorStateList(arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()),
                    intArrayOf(
                            switchThumbDisabled,
                            color,
                            switchThumbNormal))
            val trstateList = ColorStateList(arrayOf(
                    intArrayOf(-android.R.attr.state_enabled),
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()),
                    intArrayOf(
                            ColorUtils.setAlphaComponent(colorForeground, alphaDisabled),
                            color,
                            colorForeground))
            DrawableCompat.setTintList((checkableView as Switch).thumbDrawable, thstateList)
            DrawableCompat.setTintList((checkableView as Switch).trackDrawable, trstateList)
        }
    }

    override fun onDetached() {
        super.onDetached()
        ColorEngine.getInstance(context).removeAccentChangeListener(this)
    }
}
