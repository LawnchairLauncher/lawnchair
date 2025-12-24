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

package com.android.systemui.animation

import android.os.IBinder
import android.view.SurfaceControl
import android.view.WindowManager.TRANSIT_CLOSE
import android.view.WindowManager.TRANSIT_OPEN
import android.view.WindowManager.TRANSIT_TO_BACK
import android.view.WindowManager.TRANSIT_TO_FRONT
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo

/**
 * Delegate for Animation Library remote transitions that takes care of initializing the changes and
 * cleaning up once the transition is done.
 */
interface RemoteTransitionHelper {
    companion object {
        val CLOSING_MODES = setOf(TRANSIT_CLOSE, TRANSIT_TO_BACK)
        val OPENING_MODES = setOf(TRANSIT_OPEN, TRANSIT_TO_FRONT)
    }

    /**
     * Applies transactions common to all remote transitions, such as setting up the alpha and
     * rotation of various changes. Invoked before the animation starts.
     */
    fun setUpAnimation(
        token: IBinder,
        info: TransitionInfo,
        transaction: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback?,
    )

    /**
     * Cleans up any state leftover after [setUpAnimation] is called. Invoked right before the
     * transition's finish callback.
     */
    fun cleanUpAnimation(token: IBinder, transaction: SurfaceControl.Transaction)
}
