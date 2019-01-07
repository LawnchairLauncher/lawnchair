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

package ch.deletescape.lawnchair.gestures.gestures

import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.gestures.handlers.*
import com.android.launcher3.LauncherState
import com.android.launcher3.LauncherState.ALL_APPS

class VerticalSwipeGesture(controller: GestureController) : Gesture(controller) {

    override val isEnabled = true

    private val swipeUpHandler by controller.createHandlerPref("pref_gesture_swipe_up",
            OpenDrawerGestureHandler(controller.launcher, null))
    private val dockSwipeUpHandler by controller.createHandlerPref("pref_gesture_dock_swipe_up",
            OpenDrawerGestureHandler(controller.launcher, null))
    private val swipeDownHandler by controller.createHandlerPref("pref_gesture_swipe_down",
            NotificationsOpenGestureHandler(controller.launcher, null))

    val customSwipeUp get() = swipeUpHandler !is VerticalSwipeGestureHandler
    val customDockSwipeUp get() = dockSwipeUpHandler !is VerticalSwipeGestureHandler
    val customSwipeDown get() = swipeDownHandler !is NotificationsOpenGestureHandler

    val swipeUpAppsSearch get() = swipeUpHandler is StartAppSearchGestureHandler
    val dockSwipeUpAppsSearch get() = dockSwipeUpHandler is StartAppSearchGestureHandler

    fun onSwipeUp() {
        swipeUpHandler.onGestureTrigger(controller)
    }

    fun onDockSwipeUp() {
        dockSwipeUpHandler.onGestureTrigger(controller)
    }

    fun onSwipeDown() {
        swipeDownHandler.onGestureTrigger(controller)
    }

    fun onSwipeUpAllAppsComplete(fromDock: Boolean) {
        if (if (fromDock) dockSwipeUpAppsSearch else swipeUpAppsSearch) {
            controller.launcher.appsView.searchUiManager.startSearch()
        }
    }

    fun getTargetState(fromDock: Boolean): LauncherState {
        return if (fromDock) {
            (dockSwipeUpHandler as? StateChangeGestureHandler)?.getTargetState() ?: ALL_APPS
        } else {
            (swipeUpHandler as? StateChangeGestureHandler)?.getTargetState() ?: ALL_APPS
        }
    }
}
