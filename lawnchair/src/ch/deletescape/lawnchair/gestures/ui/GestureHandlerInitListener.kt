/*
 *     Copyright (C) 2019 Lawnchair Team.
 *
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

package ch.deletescape.lawnchair.gestures.ui

import ch.deletescape.lawnchair.LawnchairLauncher
import ch.deletescape.lawnchair.gestures.GestureHandler
import com.android.launcher3.Launcher
import com.android.launcher3.states.InternalStateHandler

class GestureHandlerInitListener(private val handler: GestureHandler) : InternalStateHandler() {

    override fun init(launcher: Launcher, alreadyOnHome: Boolean): Boolean {
        handler.onGestureTrigger((launcher as LawnchairLauncher).gestureController)
        return true
    }
}
