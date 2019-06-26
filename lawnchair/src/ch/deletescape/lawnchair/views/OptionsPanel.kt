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
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import com.android.launcher3.Insettable
import android.widget.FrameLayout
import ch.deletescape.lawnchair.LawnchairPreferences
import ch.deletescape.lawnchair.forEachChild
import ch.deletescape.lawnchair.isVisible
import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.Launcher
import com.android.launcher3.R
import com.android.launcher3.views.OptionsPopupView
import kotlinx.android.synthetic.main.options_view.view.*

class OptionsPanel(context: Context, attrs: AttributeSet) : LinearLayout(context, attrs),
        Insettable, View.OnClickListener, LawnchairPreferences.OnPreferenceChangeListener {

    private val launcher = Launcher.getLauncher(context)

    override fun onFinishInflate() {
        super.onFinishInflate()

        forEachChild { it.setOnClickListener(this) }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        context.lawnchairPrefs.addOnPreferenceChangeListener("pref_lockDesktop", this)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        context.lawnchairPrefs.removeOnPreferenceChangeListener("pref_lockDesktop", this)
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        widget_button.isVisible = !prefs.lockDesktop
    }

    override fun onClick(v: View) {
        when (v.id) {
            R.id.wallpaper_button -> OptionsPopupView.startWallpaperPicker(v)
            R.id.widget_button -> OptionsPopupView.onWidgetsClicked(v)
            R.id.settings_button -> OptionsPopupView.startSettings(v)
        }
    }

    override fun setInsets(insets: Rect) {
        val deviceProfile = launcher.deviceProfile

        layoutParams = (layoutParams as FrameLayout.LayoutParams).also { lp ->
            lp.width = deviceProfile.availableWidthPx
            lp.height = (deviceProfile.availableHeightPx * (1 - deviceProfile.workspaceOptionsShrinkFactor)).toInt()
            lp.bottomMargin = insets.bottom
        }
    }
}
