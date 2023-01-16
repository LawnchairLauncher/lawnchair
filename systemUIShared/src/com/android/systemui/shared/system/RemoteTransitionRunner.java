/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.systemui.shared.system;

import android.os.IBinder;
import android.view.SurfaceControl;
import android.window.TransitionInfo;

/** Interface for something that runs a remote transition animation. */
public interface RemoteTransitionRunner {
    /**
     * Starts a transition animation. Once complete, the implementation should call
     * `finishCallback`.
     */
    void startAnimation(IBinder transition, TransitionInfo info, SurfaceControl.Transaction t,
            Runnable finishCallback);

    /**
     * Attempts to merge a transition into the currently-running animation. If merge is not
     * possible/supported, this should do nothing. Otherwise, the implementation should call
     * `finishCallback` immediately to indicate that it merged the transition.
     *
     * @param transition The transition that wants to be merged into the running animation.
     * @param mergeTarget The transition to merge into (that this runner is currently animating).
     */
    default void mergeAnimation(IBinder transition, TransitionInfo info,
            SurfaceControl.Transaction t, IBinder mergeTarget, Runnable finishCallback) { }
}
