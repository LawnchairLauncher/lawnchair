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
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import ch.deletescape.lawnchair.LawnchairPreferences
import com.android.launcher3.R
import com.android.launcher3.Utilities

class HomeWidgetSwitchLayout(context: Context, attrs: AttributeSet?) :
        FrameLayout(context, attrs), LawnchairPreferences.OnPreferenceChangeListener {

    private val prefs = Utilities.getLawnchairPrefs(context)
    private var currentLayout = 0
        set(value) {
            if (field != value) {
                field = value
                removeAllViews()
                LayoutInflater.from(context).inflate(value, this, false).let {
                    it.layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
                    addView(it)
                }
            }
        }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()

        prefs.addOnPreferenceChangeListener(this, "pref_use_pill_qsb")
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()

        prefs.removeOnPreferenceChangeListener(this, "pref_use_pill_qsb")
    }

    override fun onValueChanged(key: String, prefs: LawnchairPreferences, force: Boolean) {
        currentLayout = if (prefs.usePillQsb) R.layout.qsb_container else R.layout.search_container_workspace
    }
}
