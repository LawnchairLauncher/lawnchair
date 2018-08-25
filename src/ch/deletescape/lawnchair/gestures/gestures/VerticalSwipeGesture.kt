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
import ch.deletescape.lawnchair.gestures.handlers.NotificationsOpenGestureHandler
import ch.deletescape.lawnchair.gestures.handlers.OpenDrawerGestureHandler
import ch.deletescape.lawnchair.gestures.handlers.StartAppSearchGestureHandler

class VerticalSwipeGesture(controller: GestureController) : Gesture(controller) {

    override val isEnabled = true

    private val swipeUpHandler by controller.createHandlerPref("pref_gesture_swipe_up",
            OpenDrawerGestureHandler(controller.launcher, null))
    private val swipeDownHandler by controller.createHandlerPref("pref_gesture_swipe_down",
            NotificationsOpenGestureHandler(controller.launcher, null))

    val customSwipeUp get() = swipeUpHandler !is OpenDrawerGestureHandler
    val customSwipeDown get() = swipeDownHandler !is NotificationsOpenGestureHandler

    val swipeUpAppsSearch get() = swipeUpHandler is StartAppSearchGestureHandler

    fun onSwipeUp() {
        swipeUpHandler.onGestureTrigger(controller)
    }

    fun onSwipeDown() {
        swipeDownHandler.onGestureTrigger(controller)
    }

    fun onSwipeUpAllAppsComplete() {
        if (swipeUpAppsSearch) {
            controller.launcher.appsView.searchUiManager.startSearch()
        }
    }
}
