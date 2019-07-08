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

package ch.deletescape.lawnchair.touch

import android.content.Context
import android.graphics.PointF
import android.view.MotionEvent
import android.view.View
import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.util.extensions.d
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.views.OptionsPopupView

open class GestureTouchListener(context: Context) : View.OnTouchListener {

    private val launcher = LawnchairLauncher.getLauncher(context)
    private val gestureController = launcher.gestureController

    private var touchDownX = 0f
    private var touchDownY = 0f
    private var downInOptions = false
    private var clickPossible = true

    override fun onTouch(view: View?, ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_DOWN -> {
                touchDownX = ev.x
                touchDownY = ev.y
                downInOptions = launcher.isInState(LauncherState.OPTIONS)
                clickPossible = downInOptions &&
                        launcher.workspace.isScrollerFinished
            }
            MotionEvent.ACTION_MOVE -> {
                checkClickPossible(ev.x, ev.y)
            }
            MotionEvent.ACTION_UP -> {
                checkClickPossible(ev.x, ev.y)
                if (clickPossible && launcher.isInState(LauncherState.OPTIONS)) {
                    launcher.stateManager.goToState(LauncherState.NORMAL)
                }
            }
        }
        return if (!downInOptions) {
            gestureController.onBlankAreaTouch(ev)
        } else false
    }

    private fun checkClickPossible(x: Float, y: Float) {
        if (!clickPossible) return
        clickPossible = downInOptions && distanceSquared(touchDownX, touchDownY, x, y) < 400f
    }

    private fun distanceSquared(x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val disX = x2 - x1
        val disY = y2 - y1
        return (disX * disX) + (disY * disY)
    }

    fun onLongPress() {
        if (launcher.isInState(LauncherState.NORMAL)) {
            gestureController.onLongPress()
        } else if (launcher.isInState(LauncherState.OPTIONS)) {
            // Don't go to normal on up
            clickPossible = false
            OptionsPopupView.show(launcher, touchDownX, touchDownY, listOf(
                    OptionsPopupView.OptionItem(
                            R.string.remove_drop_target_label,
                            R.drawable.ic_remove_no_shadow,
                            -1) {
                        launcher.workspace.removeScreen(launcher.currentWorkspaceScreen, true)
                        true
                    }))
        }
    }

    fun setTouchDownPoint(touchDownPoint: PointF) {
        gestureController.touchDownPoint = touchDownPoint
    }
}
