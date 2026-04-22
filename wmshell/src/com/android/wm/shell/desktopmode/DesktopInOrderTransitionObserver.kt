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
import android.view.SurfaceControl
import android.window.DesktopModeFlags
import android.window.TransitionInfo
import com.android.internal.protolog.ProtoLog
import com.android.wm.shell.desktopmode.multidesks.DesksTransitionObserver
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_DESKTOP_MODE
import com.android.wm.shell.transition.FocusTransitionObserver
import com.android.wm.shell.transition.Transitions
import java.util.Optional

/** Keeps the Desktop related [TransitionObserver] callbacks in sync. */
class DesktopInOrderTransitionObserver(
    private val desktopImmersiveController: Optional<DesktopImmersiveController>,
    private val focusTransitionObserver: FocusTransitionObserver,
    private val desksTransitionObserver: Optional<DesksTransitionObserver>,
    private val desktopImeHandler: Optional<DesktopImeHandler>,
    private val desktopBackNavTransitionObserver: Optional<DesktopBackNavTransitionObserver>,
) : Transitions.TransitionObserver {

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startT: SurfaceControl.Transaction,
        finishT: SurfaceControl.Transaction,
    ) {
        // Update desk state first, otherwise [TaskChangeListener] may update desktop task state
        // under an outdated active desk if a desk switch and a task update happen in the same
        // transition, such as when unminimizing a task from an inactive desk.
        desksTransitionObserver.ifPresent { it.onTransitionReady(transition, info) }

        if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) {
            // TODO(b/367268953): Remove when DesktopTaskListener is introduced and the repository
            //  is updated from there **before** the |mWindowDecorViewModel| methods are invoked.
            //  Otherwise window decoration relayout won't run with the immersive state up to date.
            desktopImmersiveController.ifPresent {
                it.onTransitionReady(transition, info, startT, finishT)
            }
        }
        // Update focus state first to ensure the correct state can be queried from listeners.
        // TODO(371503964): Remove this once the unified task repository is ready.
        focusTransitionObserver.updateFocusState(info)

        // Call after the focus state update to have the correct focused window.
        desktopImeHandler.ifPresent { it.onTransitionReady(transition, info) }
        desktopBackNavTransitionObserver.ifPresent { it.onTransitionReady(transition, info) }
    }

    override fun onTransitionStarting(transition: IBinder) {
        if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) {
            // TODO(b/367268953): Remove when DesktopTaskListener is introduced.
            desktopImmersiveController.ifPresent { it.onTransitionStarting(transition) }
        }
    }

    override fun onTransitionMerged(merged: IBinder, playing: IBinder) {
        desksTransitionObserver.ifPresent { it.onTransitionMerged(merged, playing) }
        if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) {
            // TODO(b/367268953): Remove when DesktopTaskListener is introduced.
            desktopImmersiveController.ifPresent { it.onTransitionMerged(merged, playing) }
        }
    }

    override fun onTransitionFinished(transition: IBinder, aborted: Boolean) {
        desksTransitionObserver.ifPresent { it.onTransitionFinished(transition) }
        if (DesktopModeFlags.ENABLE_FULLY_IMMERSIVE_IN_DESKTOP.isTrue) {
            // TODO(b/367268953): Remove when DesktopTaskListener is introduced.
            desktopImmersiveController.ifPresent { h ->
                h.onTransitionFinished(transition, aborted)
            }
        }
    }

    private fun logD(msg: String, vararg arguments: Any?) {
        ProtoLog.d(WM_SHELL_DESKTOP_MODE, "%s: $msg", TAG, *arguments)
    }

    companion object {
        private const val TAG = "DesktopInOrderTransitionObserver"
    }
}
