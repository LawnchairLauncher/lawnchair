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
 * limitations under the License
 */

package com.android.systemui.shared.system;

import android.os.RemoteException;
import android.util.Log;
import android.view.RemoteAnimationTarget;
import android.view.SurfaceControl;
import android.window.PictureInPictureSurfaceTransaction;
import android.window.WindowAnimationState;

import com.android.internal.os.IResultReceiver;
import com.android.wm.shell.recents.IRecentsAnimationController;

public class RecentsAnimationControllerCompat {

    private static final String TAG = RecentsAnimationControllerCompat.class.getSimpleName();

    private IRecentsAnimationController mAnimationController;

    public RecentsAnimationControllerCompat() { }

    public RecentsAnimationControllerCompat(IRecentsAnimationController animationController) {
        mAnimationController = animationController;
    }

    public void setInputConsumerEnabled(boolean enabled) {
        try {
            mAnimationController.setInputConsumerEnabled(enabled);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set input consumer enabled state", e);
        }
    }

    /**
     * Sets the final surface transaction on a Task. This is used by Launcher to notify the system
     * that animating Activity to PiP has completed and the associated task surface should be
     * updated accordingly. This should be called before `finish`
     * @param taskId Task id of the Activity in PiP mode.
     * @param finishTransaction leash operations for the final transform.
     * @param overlay the surface control for an overlay being shown above the pip (can be null)
     */
    public void setFinishTaskTransaction(int taskId,
            PictureInPictureSurfaceTransaction finishTransaction,
            SurfaceControl overlay) {
        try {
            mAnimationController.setFinishTaskTransaction(taskId, finishTransaction, overlay);
        } catch (RemoteException e) {
            Log.d(TAG, "Failed to set finish task bounds", e);
        }
    }

    /**
     * Finish the current recents animation.
     * @param toHome Going to home or back to the previous app.
     * @param sendUserLeaveHint determines whether userLeaveHint will be set true to the previous
     *                          app.
     */
    public void finish(boolean toHome, boolean sendUserLeaveHint, IResultReceiver finishCb) {
        try {
            mAnimationController.finish(toHome, sendUserLeaveHint, finishCb);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to finish recents animation", e);
            try {
                finishCb.send(0, null);
            } catch (Exception ex) {
                // Local call, can ignore
            }
        }
    }

    /**
     * @see {{@link IRecentsAnimationController#setWillFinishToHome(boolean)}}.
     */
    public void setWillFinishToHome(boolean willFinishToHome) {
        try {
            mAnimationController.setWillFinishToHome(willFinishToHome);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set overview reached state", e);
        }
    }

    /**
     * @see IRecentsAnimationController#handOffAnimation
     */
    public void handOffAnimation(RemoteAnimationTarget[] targets, WindowAnimationState[] states) {
        try {
            mAnimationController.handOffAnimation(targets, states);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to hand off animation", e);
        }
    }

    /**
     * @see IRecentsAnimationController#detachNavigationBarFromApp
     */
    public void detachNavigationBarFromApp(boolean moveHomeToTop) {
        try {
            mAnimationController.detachNavigationBarFromApp(moveHomeToTop);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to detach the navigation bar from app", e);
        }
    }
}
