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
import android.preference.SwitchPreference
//import android.support.v14.preference.SwitchPreference
import android.support.v4.graphics.ColorUtils
import android.support.v4.graphics.drawable.DrawableCompat
import android.support.v7.preference.AndroidResources
import android.support.v7.preference.PreferenceViewHolder
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.Switch
import ch.deletescape.lawnchair.applyColor
import ch.deletescape.lawnchair.colors.ColorEngine
import com.android.launcher3.util.Themes


open class StyledSwitchPreference(context: Context, attrs: AttributeSet?) : SwitchPreference(context, attrs), ColorEngine.OnAccentChangeListener {

    private var checkableView: View? = null

    override fun onBindView(view: View?) {
        super.onBindView(view)
        checkableView = view?.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET)
        ColorEngine.getInstance(context).addAccentChangeListener(this)
    }

    override fun onAccentChange(color: Int, foregroundColor: Int) {
        if (checkableView is Switch) {
            (checkableView as Switch).applyColor(color)
        }
    }

    override fun onPrepareForRemoval() {
        super.onPrepareForRemoval()
        ColorEngine.getInstance(context).removeAccentChangeListener(this)
    }
}
