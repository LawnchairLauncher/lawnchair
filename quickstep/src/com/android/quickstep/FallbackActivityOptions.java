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
package com.android.quickstep;

import android.graphics.Rect;
import android.util.Log;

import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.RemoteAnimationRunnerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

/**
 * Temporary class to create activity options to emulate recents transition for fallback activtiy.
 */
public class FallbackActivityOptions implements RemoteAnimationRunnerCompat {

    private final RecentsAnimationListener mListener;

    public FallbackActivityOptions(RecentsAnimationListener listener) {
        mListener = listener;
    }

    @Override
    public void onAnimationStart(RemoteAnimationTargetCompat[] targetCompats,
            Runnable runnable) {
        DummyRecentsAnimationControllerCompat dummyRecentsAnim =
                new DummyRecentsAnimationControllerCompat(runnable);

        Rect insets = new Rect();
        WindowManagerWrapper.getInstance().getStableInsets(insets);
        mListener.onAnimationStart(dummyRecentsAnim, targetCompats, insets, null);
    }

    @Override
    public void onAnimationCancelled() {
        mListener.onAnimationCanceled();
    }

    private static class DummyRecentsAnimationControllerCompat
            extends RecentsAnimationControllerCompat {

        final Runnable mFinishCallback;

        public DummyRecentsAnimationControllerCompat(Runnable finishCallback) {
            mFinishCallback = finishCallback;
        }

        @Override
        public ThumbnailData screenshotTask(int taskId) {
            return new ThumbnailData();
        }

        @Override
        public void setInputConsumerEnabled(boolean enabled) { }

        @Override
        public void setAnimationTargetsBehindSystemBars(boolean behindSystemBars) { }

        @Override
        public void finish(boolean toHome) {
            if (toHome) {
                mFinishCallback.run();
            }
        }
    }
}
