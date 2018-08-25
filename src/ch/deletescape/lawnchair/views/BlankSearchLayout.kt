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
import android.support.animation.FloatValueHolder
import android.support.animation.SpringAnimation
import android.support.animation.SpringForce
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.android.launcher3.Launcher
import com.android.launcher3.allapps.AllAppsContainerView
import com.android.launcher3.allapps.AllAppsRecyclerView
import com.android.launcher3.allapps.AlphabeticalAppsList
import com.android.launcher3.allapps.SearchUiManager

class BlankSearchLayout(context: Context, attrs: AttributeSet?) : View(context, attrs), SearchUiManager {

    var topMargin = 0
        set(value) {
            if (value != field) {
                field = value
                layoutParams.height = value
            }
        }

    override fun initialize(containerView: AllAppsContainerView) {
        containerView.isVerticalFadingEdgeEnabled = false
    }

    override fun resetSearch() {

    }

    override fun preDispatchKeyEvent(keyEvent: KeyEvent?) {

    }

    override fun startSearch() {

    }
}
