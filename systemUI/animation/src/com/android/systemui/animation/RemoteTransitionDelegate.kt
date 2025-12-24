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
import android.window.IRemoteTransition
import android.window.IRemoteTransitionFinishedCallback
import android.window.TransitionInfo
import android.window.WindowAnimationState

/**
 * Delegates transition handling to the remote transition returned by [remoteTransitionPicker].
 *
 * The remote transition is updated in [#startAnimation] - after that the same remote transition is
 * used throughout the transition lifecycle.
 */
class RemoteTransitionDelegate(
    private val remoteTransitionPicker: Function1<TransitionInfo, IRemoteTransition>
) : IRemoteTransition.Stub() {
    private var currentRemoteTransition: IRemoteTransition? = null

    override fun startAnimation(
        token: IBinder,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback,
    ) {
        currentRemoteTransition = remoteTransitionPicker.invoke(info)
        currentRemoteTransition?.startAnimation(token, info, t, finishCallback)
    }

    override fun mergeAnimation(
        token: IBinder,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        mergeTarget: IBinder,
        finishCallback: IRemoteTransitionFinishedCallback,
    ) {
        currentRemoteTransition?.mergeAnimation(token, info, t, mergeTarget, finishCallback)
    }

    override fun takeOverAnimation(
        token: IBinder,
        info: TransitionInfo,
        t: SurfaceControl.Transaction,
        finishCallback: IRemoteTransitionFinishedCallback,
        states: Array<WindowAnimationState>,
    ) {
        currentRemoteTransition?.takeOverAnimation(token, info, t, finishCallback, states)
    }

    override fun onTransitionConsumed(token: IBinder, aborted: Boolean) {
        currentRemoteTransition?.onTransitionConsumed(token, aborted)
    }
}
