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

package ch.deletescape.lawnchair.states

import android.content.Context
import android.view.ViewGroup
import ch.deletescape.lawnchair.lawnchairPrefs
import ch.deletescape.lawnchair.util.SingletonHolder
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import kotlin.math.min

open class HomeState(id: Int, containerType: Int, transitionDuration: Int, flags: Int) :
        LauncherState(id, containerType, transitionDuration, flags) {

    override fun getScrimProgress(launcher: Launcher): Float {
        if (!launcher.lawnchairPrefs.dockGradientStyle) {
            return getNormalProgress(launcher)
        }
        return super.getScrimProgress(launcher)
    }

    companion object {

        private val shelfOffset = SingletonHolder<Int, Context> { it.resources.getDimensionPixelSize(R.dimen.shelf_surface_offset) }

        fun getNormalProgress(launcher: Launcher): Float {
            return 1 - (getScrimHeight(launcher) / launcher.allAppsController.shiftRange)
        }

        private fun getScrimHeight(launcher: Launcher): Float {
            val dp = launcher.deviceProfile
            val prefs = Utilities.getLawnchairPrefs(launcher)

            return if (prefs.dockHide) {
                dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx
            } else {
                val rangeDelta = dp.heightPx - launcher.allAppsController.shiftRange
                val lp = launcher.hotseat.layoutParams
                -rangeDelta + lp.height + dp.insets.top - shelfOffset.getInstance(launcher)
            }
        }
    }
}
