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

package com.android.wm.shell.desktopmode

import android.os.IBinder
import android.os.RemoteException
import android.util.Slog
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions

/** Callback to Launcher overview to notify that add to desktop from overview menu is completed. */
class OverviewToDesktopTransitionObserver(
    private val transitions: Transitions,
    shellInit: ShellInit,
) : Transitions.TransitionObserver {

    private val transitionToCallback = mutableMapOf<IBinder?, IMoveToDesktopCallback?>()

    init {
        shellInit.addInitCallback(::onInit, this)
    }

    fun onInit() {
        transitions.registerObserver(this)
    }

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
        try {
            transitionToCallback[transition]?.onTaskMovedToDesktop()
            transitionToCallback.clear()
        } catch (e: RemoteException) {
            Slog.e(TAG, "onTransitionFinished: Error calling onTaskMovedToDesktop", e)
        }
    }

    fun addPendingOverviewTransition(transition: IBinder?, callback: IMoveToDesktopCallback?) {
        transitionToCallback += transition to callback
    }

    companion object {
        const val TAG = "OverviewToDesktopTransitionObserver"
    }
}
