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

package com.android.wm.shell.transition

import android.os.IBinder
import android.view.SurfaceControl
import android.window.TransitionInfo
import android.window.TransitionRequestInfo
import android.window.WindowContainerTransaction

/**
 * A [Transitions.TransitionHandler] that does nothing.
 *
 * Will report to handle the transition, but will not start any animations. Will immediately call
 * finish callback when animation starts.
 */
class NoOpTransitionHandler : Transitions.TransitionHandler {
    override fun handleRequest(
        transition: IBinder,
        request: TransitionRequestInfo,
    ): WindowContainerTransaction? {
        return WindowContainerTransaction()
    }

    override fun startAnimation(
        transition: IBinder,
        info: TransitionInfo,
        startTransaction: SurfaceControl.Transaction,
        finishTransaction: SurfaceControl.Transaction,
        finishCallback: Transitions.TransitionFinishCallback,
    ): Boolean {
        finishCallback.onTransitionFinished(null)
        return true
    }
}
