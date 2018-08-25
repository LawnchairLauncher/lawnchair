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

import android.view.MotionEvent
import ch.deletescape.lawnchair.gestures.Gesture
import ch.deletescape.lawnchair.gestures.GestureController
import ch.deletescape.lawnchair.lawnchairPrefs

class DoubleTapGesture(controller: GestureController) : Gesture(controller) {

    override val isEnabled = true
    private val prefs = controller.launcher.lawnchairPrefs
    private val delay get() = prefs.doubleTapDelay
    private val handler by controller.createHandlerPref("pref_gesture_double_tap")

    private var lastDown = 0L

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (ev.eventTime - lastDown <= delay) {
                    handler.onGestureTrigger(controller)
                    lastDown = 0L
                    return true
                } else {
                    lastDown = ev.downTime
                }
            }
        }
        return false
    }
}
