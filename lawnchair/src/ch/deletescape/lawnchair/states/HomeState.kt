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

import ch.deletescape.lawnchair.lawnchairPrefs
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherState
import com.android.launcher3.uioverrides.OverviewState

open class HomeState(id: Int, containerType: Int, transitionDuration: Int, flags: Int) :
        LauncherState(id, containerType, transitionDuration, flags) {

    override fun getScrimProgress(launcher: Launcher): Float {
        if (!launcher.lawnchairPrefs.dockGradientStyle) {
            // TODO: implement actual desktop scrim height calculation
            return OverviewState.getNormalVerticalProgress(launcher)
        }
        return super.getScrimProgress(launcher)
    }
}
