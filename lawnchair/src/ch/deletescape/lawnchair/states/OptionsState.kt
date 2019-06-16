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

import ch.deletescape.lawnchair.LawnchairLauncher
import com.android.launcher3.Launcher
import com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_TRANSITION_MS
import com.android.launcher3.LauncherState
import com.android.launcher3.states.RotationHelper
import com.android.launcher3.userevent.nano.LauncherLogProto

class OptionsState(id: Int) : LauncherState(id, LauncherLogProto.ContainerType.OVERVIEW,
        SPRING_LOADED_TRANSITION_MS, STATE_FLAGS) {

    override fun getWorkspaceScaleAndTranslation(launcher: Launcher): FloatArray {
        val grid = launcher.deviceProfile
        val ws = launcher.workspace

        if (grid.isVerticalBarLayout) {
            val scale = grid.workspaceOptionsShrinkFactor
            val optionsView = LawnchairLauncher.getLauncher(launcher).optionsView

            val wsHeightWithoutInsets = ws.height - grid.insets.top - grid.insets.bottom
            val desiredCenter = wsHeightWithoutInsets * 0.5f * scale
            val actualCenter = wsHeightWithoutInsets * 0.5f

            val desiredBottom = grid.heightPx - optionsView.height
            val actualBottom = ws.height * 0.5f + (ws.height * 0.5f * scale)

            return floatArrayOf(scale, 0f, Math.max(desiredCenter - actualCenter, desiredBottom - actualBottom))
        }

        val scale = grid.workspaceOptionsShrinkFactor
        val insets = launcher.dragLayer.insets

        val scaledHeight = scale * ws.normalChildHeight
        val shrunkTop = (insets.top + grid.dropTargetBarSizePx).toFloat()
        val shrunkBottom = (ws.measuredHeight - insets.bottom
                - grid.workspacePadding.bottom
                - grid.workspaceSpringLoadedBottomSpace).toFloat()
        val totalShrunkSpace = shrunkBottom - shrunkTop

        val desiredCellTop = shrunkTop + (totalShrunkSpace - scaledHeight) / 2

        val halfHeight = (ws.height / 2).toFloat()
        val myCenter = ws.top + halfHeight
        val cellTopFromCenter = halfHeight - ws.getChildAt(0).top
        val actualCellTop = myCenter - cellTopFromCenter * scale
        return floatArrayOf(scale, 0f, (desiredCellTop - actualCellTop) / scale)
    }

    override fun getWorkspaceScrimAlpha(launcher: Launcher?): Float {
        return 0.6f
    }

    override fun getWorkspaceBlurAlpha(launcher: Launcher?): Float {
        return 1f
    }

    override fun getVisibleElements(launcher: Launcher): Int {
        return OPTIONS_VIEW
    }

    override fun onStateEnabled(launcher: Launcher) {
        super.onStateEnabled(launcher)
        launcher.rotationHelper.setCurrentStateRequest(RotationHelper.REQUEST_LOCK)
    }

    companion object {
        private const val STATE_FLAGS = FLAG_MULTI_PAGE or FLAG_DISABLE_RESTORE or
                FLAG_DISABLE_PAGE_CLIPPING or FLAG_PAGE_BACKGROUNDS
    }
}
