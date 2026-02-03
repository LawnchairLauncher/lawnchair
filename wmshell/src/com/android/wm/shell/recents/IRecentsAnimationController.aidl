/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.wm.shell.recents;

import android.graphics.GraphicBuffer;
import android.view.IRemoteAnimationFinishedCallback;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.PictureInPictureSurfaceTransaction;
import android.window.TaskSnapshot;
import android.window.WindowAnimationState;

import com.android.internal.os.IResultReceiver;

/**
 * Passed to the {@link IRecentsAnimationRunner} in order for the runner to control to let the
 * runner control certain aspects of the recents animation, and to notify window manager when the
 * animation has completed.
 *
 * {@hide}
 */
interface IRecentsAnimationController {

    /**
     * Sets the final surface transaction on a Task. This is used by Launcher to notify the system
     * that animating Activity to PiP has completed and the associated task surface should be
     * updated accordingly. This should be called before `finish`
     * @param taskId for which the leash should be updated
     * @param finishTransaction leash operations for the final transform.
     * @param overlay the surface control for an overlay being shown above the pip (can be null)
     */
     void setFinishTaskTransaction(int taskId,
             in PictureInPictureSurfaceTransaction finishTransaction, in SurfaceControl overlay);

    /**
     * Notifies to the system that the animation into Recents should end, and all leashes associated
     * with remote animation targets should be relinquished. If {@param moveHomeToTop} is true, then
     * the home activity should be moved to the top. Otherwise, the home activity is hidden and the
     * user is returned to the app.
     * @param sendUserLeaveHint If set to true, {@link Activity#onUserLeaving} will be sent to the
     *                          top resumed app, false otherwise.
     */
    void finish(boolean moveHomeToTop, boolean sendUserLeaveHint, in IResultReceiver finishCb);

    /**
     * Called by the handler to indicate that the recents animation input consumer should be
     * enabled. This is currently used to work around an issue where registering an input consumer
     * mid-animation causes the existing motion event chain to be canceled. Instead, the caller
     * may register the recents animation input consumer prior to starting the recents animation
     * and then enable it mid-animation to start receiving touch events.
     */
    void setInputConsumerEnabled(boolean enabled);

    /**
     * Sets a state for controller to decide which surface is the destination when the recents
     * animation is cancelled through fail safe mechanism.
     */
    void setWillFinishToHome(boolean willFinishToHome);

    /**
     * Detach navigation bar from app.
     *
     * The system reparents the leash of navigation bar to the app when the recents animation starts
     * and Launcher should call this method to let system restore the navigation bar to its
     * original position when the quick switch gesture is finished and will run the fade-in
     * animation If {@param moveHomeToTop} is {@code true}. Otherwise, restore the navigtation bar
     * without animation.
     *
     * @param moveHomeToTop if {@code true}, the home activity should be moved to the top.
     *                      Otherwise, the home activity is hidden and the user is returned to the
     *                      app.
     */
    void detachNavigationBarFromApp(boolean moveHomeToTop);

    /**
     * Hand off the ongoing animation of a set of remote targets, to be run by another handler using
     * the given starting parameters.
     *
     * Once the handoff is complete, operations on the old leashes for the given targets as well as
     * callbacks will become no-ops.
     *
     * The number of targets MUST match the number of states, and each state MUST match the target
     * at the same index.
     */
    oneway void handOffAnimation(in RemoteAnimationTarget[] targets,
                    in WindowAnimationState[] states);
}
