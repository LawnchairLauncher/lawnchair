/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.wm.shell.desktopmode.desktopfirst

import android.util.ArrayMap
import android.util.ArraySet
import android.window.DisplayAreaInfo
import com.android.wm.shell.RootTaskDisplayAreaOrganizer
import com.android.wm.shell.common.DisplayController
import com.android.wm.shell.shared.desktopmode.DesktopFirstListener
import com.android.wm.shell.sysui.ShellInit

/** Manages the desktop-first listeners */
class DesktopFirstListenerManager(
    shellInit: ShellInit,
    private val rootTaskDisplayAreaOrganizer: RootTaskDisplayAreaOrganizer,
    private val displayController: DisplayController,
) : DisplayController.OnDisplaysChangedListener {

    private val notifiedIsDesktopFirstByDisplayId = ArrayMap<Int, Boolean>()
    private val listeners = ArraySet<DesktopFirstListener>()

    private val rootTaskDisplayAreaListener =
        object : RootTaskDisplayAreaOrganizer.RootTaskDisplayAreaListener {
            override fun onDisplayAreaAppeared(displayAreaInfo: DisplayAreaInfo) {
                val displayId = displayAreaInfo.displayId
                val isDesktopFirst = rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)
                notifiedIsDesktopFirstByDisplayId[displayId] = isDesktopFirst
                for (listener in listeners) {
                    listener.onStateChanged(displayId, isDesktopFirst)
                }
            }

            override fun onDisplayAreaVanished(displayAreaInfo: DisplayAreaInfo) {
                val displayId = displayAreaInfo.displayId
                notifiedIsDesktopFirstByDisplayId.remove(displayId)
            }

            override fun onDisplayAreaInfoChanged(displayAreaInfo: DisplayAreaInfo) {
                val displayId = displayAreaInfo.displayId
                val isDesktopFirst = rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)

                if (notifiedIsDesktopFirstByDisplayId[displayId] == isDesktopFirst) {
                    // No change.
                    return
                }

                for (listener in listeners) {
                    listener.onStateChanged(displayId, isDesktopFirst)
                }
                notifiedIsDesktopFirstByDisplayId[displayId] = isDesktopFirst
            }
        }

    init {
        shellInit.addInitCallback({ onInit() }, this)
    }

    private fun onInit() {
        displayController.addDisplayWindowListener(this)
    }

    /**
     * Register a listener that will receive callbacks about desktop-first state. Once it's
     * registered, the listener immediately receives the current state.
     */
    fun registerListener(listener: DesktopFirstListener) {
        if (!listeners.add(listener)) {
            // The listener is already registered.
            return
        }

        // Notifies the current state on registered.
        for (displayId in rootTaskDisplayAreaOrganizer.displayIds) {
            val isDesktopFirst = rootTaskDisplayAreaOrganizer.isDisplayDesktopFirst(displayId)
            listener.onStateChanged(displayId, isDesktopFirst)
        }
    }

    /** Unregister a registered desktop-first listener */
    fun unregisterListener(listener: DesktopFirstListener) {
        listeners.remove(listener)
    }

    override fun onDisplayAdded(displayId: Int) {
        rootTaskDisplayAreaOrganizer.registerListener(displayId, rootTaskDisplayAreaListener)
    }

    override fun onDisplayRemoved(displayId: Int) {
        rootTaskDisplayAreaOrganizer.unregisterListener(displayId, rootTaskDisplayAreaListener)
    }
}
