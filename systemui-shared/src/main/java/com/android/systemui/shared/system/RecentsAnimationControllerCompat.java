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

import android.app.ActivityManager.TaskSnapshot;
import android.os.RemoteException;
import android.util.Log;
import android.view.IRecentsAnimationController;

import com.android.systemui.shared.recents.model.ThumbnailData;

public class RecentsAnimationControllerCompat {

    private static final String TAG = RecentsAnimationControllerCompat.class.getSimpleName();

    private IRecentsAnimationController mAnimationController;

    public RecentsAnimationControllerCompat() { }

    public RecentsAnimationControllerCompat(IRecentsAnimationController animationController) {
        mAnimationController = animationController;
    }

    public ThumbnailData screenshotTask(int taskId) {
        try {
            TaskSnapshot snapshot = mAnimationController.screenshotTask(taskId);
            return snapshot != null ? new ThumbnailData(snapshot) : new ThumbnailData();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to screenshot task", e);
            return new ThumbnailData();
        }
    }

    public void setInputConsumerEnabled(boolean enabled) {
        try {
            mAnimationController.setInputConsumerEnabled(enabled);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set input consumer enabled state", e);
        }
    }

    public void setAnimationTargetsBehindSystemBars(boolean behindSystemBars) {
        try {
            mAnimationController.setAnimationTargetsBehindSystemBars(behindSystemBars);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set whether animation targets are behind system bars", e);
        }
    }

    public void setSplitScreenMinimized(boolean minimized) {
        try {
            mAnimationController.setSplitScreenMinimized(minimized);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set minimize dock", e);
        }
    }

    public void hideCurrentInputMethod() {
        try {
            mAnimationController.hideCurrentInputMethod();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to set hide input method", e);
        }
    }

    public void finish(boolean toHome) {
        try {
            mAnimationController.finish(toHome);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to finish recents animation", e);
        }
    }
}