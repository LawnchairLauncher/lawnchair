/*
 * Copyright (C) 2020 The Android Open Source Project
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
 * limitations under the License
 */

package android.window;

import android.view.SurfaceControl;
import android.window.WindowContainerTransaction;

/**
 * Interface to be invoked by the controlling process when a remote transition has finished.
 *
 * @see IRemoteTransition
 * @param wct An optional WindowContainerTransaction to apply before the transition finished.
 * @param sct An optional Surface Transaction that is added to the end of the finish/cleanup
 *            transaction. This is applied by shell.Transitions (before submitting the wct).
 * {@hide}
 */
interface IRemoteTransitionFinishedCallback {
    void onTransitionFinished(in WindowContainerTransaction wct, in SurfaceControl.Transaction sct);
}
