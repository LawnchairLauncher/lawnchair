/*
 * Copyright 2025 The Android Open Source Project
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

package com.android.wm.shell.compatui.letterbox

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionInfo.Change
import com.android.internal.protolog.ProtoLog
import com.android.window.flags2.Flags
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleController
import com.android.wm.shell.compatui.letterbox.lifecycle.LetterboxLifecycleEventFactory
import com.android.wm.shell.compatui.letterbox.lifecycle.isChangeForALeafTask
import com.android.wm.shell.protolog.ShellProtoLogGroup.WM_SHELL_APP_COMPAT
import com.android.wm.shell.sysui.ShellInit
import com.android.wm.shell.transition.Transitions
import com.android.wm.shell.transition.Transitions.TRANSIT_MOVE_LETTERBOX_REACHABILITY

/**
 * The [TransitionObserver] to handle Letterboxing events in Shell delegating to a
 * [LetterboxLifecycleController].
 */
class DelegateLetterboxTransitionObserver(
    shellInit: ShellInit,
    private val transitions: Transitions,
    private val letterboxLifecycleController: LetterboxLifecycleController,
    private val letterboxLifecycleEventFactory: LetterboxLifecycleEventFactory,
) : Transitions.TransitionObserver {

    companion object {
        @JvmStatic private val TAG = "DelegateLetterboxTransitionObserver"
    }

    init {
        if (Flags.appCompatRefactoring()) {
            logV("Initializing LetterboxTransitionObserver")
            shellInit.addInitCallback({ transitions.registerObserver(this) }, this)
        }
    }

    override fun onTransitionReady(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
    ) {
        if (info.type == TRANSIT_MOVE_LETTERBOX_REACHABILITY) {
            // Reachability transitions are handled in LetterboxAnimationHandler
            return
        }
        info.changes.forEach { change ->
            if (taskAllowed(change) && letterboxLifecycleEventFactory.canHandle(change)) {
                letterboxLifecycleEventFactory.createLifecycleEvent(change)?.let { event ->
                    letterboxLifecycleController.onLetterboxLifecycleEvent(
                        event,
                        startTransaction,
                        finishTransaction,
                    )
                }
            }
        }
    }

    private fun logV(msg: String) {
        ProtoLog.v(WM_SHELL_APP_COMPAT, "%s: %s", TAG, msg)
    }

    // When the flag is disabled all the changes related to leaf Tasks are skipped. This is because
    // a leaf task surfaces should not be the parent of letterbox surfaces.
    // When the flag is enabled, leaf Tasks are handled to cover the case of split screen when
    // Task in the Change is not a leaf Task but it's still useful to find the actual leaf Task used
    // to identify the right letterbox surfaces. Check [TaskIdResolver] for additional information.
    private fun taskAllowed(change: Change): Boolean =
        Flags.appCompatRefactoringFixMultiwindowTaskHierarchy() || change.isChangeForALeafTask()
}
