/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.graphics.Rect;
import android.util.ArraySet;

import androidx.annotation.UiThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Wrapper around {@link RecentsAnimationListener} which delegates callbacks to multiple listeners
 * on the main thread
 */
public class RecentsAnimationListenerSet implements RecentsAnimationListener {

    // The actual app surface is replaced by a screenshot upon recents animation cancelation when
    // the thumbnailData exists. Launcher takes the responsibility to clean up this screenshot
    // after app transition is finished. This delay is introduced to cover the app transition
    // period of time.
    private final int TRANSITION_DELAY = 100;

    private final Set<SwipeAnimationListener> mListeners = new ArraySet<>();
    private final boolean mShouldMinimizeSplitScreen;
    private final Consumer<SwipeAnimationTargetSet> mOnFinishListener;
    private RecentsAnimationControllerCompat mController;

    private boolean mCancelled;

    public RecentsAnimationListenerSet(boolean shouldMinimizeSplitScreen,
            Consumer<SwipeAnimationTargetSet> onFinishListener) {
        mShouldMinimizeSplitScreen = shouldMinimizeSplitScreen;
        mOnFinishListener = onFinishListener;
    }

    @UiThread
    public void addListener(SwipeAnimationListener listener) {
        Preconditions.assertUIThread();
        mListeners.add(listener);
    }

    @UiThread
    public void removeListener(SwipeAnimationListener listener) {
        Preconditions.assertUIThread();
        mListeners.remove(listener);
    }

    @Override
    public final void onAnimationStart(RecentsAnimationControllerCompat controller,
            RemoteAnimationTargetCompat[] targets, Rect homeContentInsets,
            Rect minimizedHomeBounds) {
        mController = controller;
        SwipeAnimationTargetSet targetSet = new SwipeAnimationTargetSet(controller, targets,
                homeContentInsets, minimizedHomeBounds, mShouldMinimizeSplitScreen,
                mOnFinishListener);

        if (mCancelled) {
            targetSet.cancelAnimation();
        } else {
            Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
                for (SwipeAnimationListener listener : getListeners()) {
                    listener.onRecentsAnimationStart(targetSet);
                }
            });
        }
    }

    @Override
    public final void onAnimationCanceled(ThumbnailData thumbnailData) {
        Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
            for (SwipeAnimationListener listener : getListeners()) {
                listener.onRecentsAnimationCanceled();
            }
        });
        // TODO: handle the transition better instead of simply using a transition delay.
        if (thumbnailData != null) {
            MAIN_EXECUTOR.getHandler().postDelayed(() -> mController.cleanupScreenshot(),
                    TRANSITION_DELAY);
        }
    }

    private SwipeAnimationListener[] getListeners() {
        return mListeners.toArray(new SwipeAnimationListener[mListeners.size()]);
    }

    public void cancelListener() {
        mCancelled = true;
        onAnimationCanceled(null);
    }
}
