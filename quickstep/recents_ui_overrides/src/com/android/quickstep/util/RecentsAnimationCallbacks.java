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

import androidx.annotation.BinderThread;
import androidx.annotation.UiThread;

import com.android.launcher3.Utilities;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.TouchInteractionService;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Wrapper around {@link com.android.systemui.shared.system.RecentsAnimationListener} which delegates callbacks to multiple listeners
 * on the main thread
 */
public class RecentsAnimationCallbacks implements
        com.android.systemui.shared.system.RecentsAnimationListener {

    private final Set<RecentsAnimationListener> mListeners = new ArraySet<>();
    private final boolean mShouldMinimizeSplitScreen;
    private final Consumer<RecentsAnimationTargets> mOnFinishListener;
    private RecentsAnimationControllerCompat mController;

    private boolean mCancelled;

    public RecentsAnimationCallbacks(boolean shouldMinimizeSplitScreen,
            Consumer<RecentsAnimationTargets> onFinishListener) {
        mShouldMinimizeSplitScreen = shouldMinimizeSplitScreen;
        mOnFinishListener = onFinishListener;
        TouchInteractionService.getSwipeSharedState().setRecentsAnimationCanceledCallback(
                () -> mController.cleanupScreenshot());
    }

    @UiThread
    public void addListener(RecentsAnimationListener listener) {
        Preconditions.assertUIThread();
        mListeners.add(listener);
    }

    @UiThread
    public void removeListener(RecentsAnimationListener listener) {
        Preconditions.assertUIThread();
        mListeners.remove(listener);
    }

    // Called only in R+ platform
    @BinderThread
    public final void onAnimationStart(RecentsAnimationControllerCompat controller,
            RemoteAnimationTargetCompat[] appTargets,
            RemoteAnimationTargetCompat[] wallpaperTargets,
            Rect homeContentInsets, Rect minimizedHomeBounds) {
        mController = controller;
        RecentsAnimationTargets targetSet = new RecentsAnimationTargets(controller, appTargets,
                wallpaperTargets, homeContentInsets, minimizedHomeBounds,
                mShouldMinimizeSplitScreen, mOnFinishListener);

        if (mCancelled) {
            targetSet.cancelAnimation();
        } else {
            Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
                for (RecentsAnimationListener listener : getListeners()) {
                    listener.onRecentsAnimationStart(targetSet);
                }
            });
        }
    }

    // Called only in Q platform
    @BinderThread
    @Deprecated
    public final void onAnimationStart(RecentsAnimationControllerCompat controller,
            RemoteAnimationTargetCompat[] appTargets, Rect homeContentInsets,
            Rect minimizedHomeBounds) {
        onAnimationStart(controller, appTargets, new RemoteAnimationTargetCompat[0],
                homeContentInsets, minimizedHomeBounds);
    }

    @BinderThread
    @Override
    public final void onAnimationCanceled(ThumbnailData thumbnailData) {
        Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(), () -> {
            for (RecentsAnimationListener listener : getListeners()) {
                listener.onRecentsAnimationCanceled(thumbnailData);
            }
        });
    }

    private RecentsAnimationListener[] getListeners() {
        return mListeners.toArray(new RecentsAnimationListener[mListeners.size()]);
    }

    public void cancelListener() {
        mCancelled = true;
        onAnimationCanceled(null);
    }

    /**
     * Listener for the recents animation callbacks.
     */
    public interface RecentsAnimationListener {
        void onRecentsAnimationStart(RecentsAnimationTargets targetSet);

        /**
         * Callback from the system when the recents animation is canceled. {@param thumbnailData}
         * is passed back for rendering screenshot to replace live tile.
         */
        void onRecentsAnimationCanceled(ThumbnailData thumbnailData);
    }
}
