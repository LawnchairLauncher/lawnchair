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

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;
import static com.android.launcher3.util.Executors.UI_HELPER_EXECUTOR;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;

import com.android.launcher3.util.Preconditions;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import java.util.function.Consumer;

/**
 * Wrapper around RecentsAnimationControllerCompat to help with some synchronization
 */
public class RecentsAnimationController {

    private final RecentsAnimationControllerCompat mController;
    private final Consumer<RecentsAnimationController> mOnFinishedListener;
    private final boolean mAllowMinimizeSplitScreen;

    private boolean mUseLauncherSysBarFlags = false;
    private boolean mSplitScreenMinimized = false;

    public RecentsAnimationController(RecentsAnimationControllerCompat controller,
            boolean allowMinimizeSplitScreen,
            Consumer<RecentsAnimationController> onFinishedListener) {
        mController = controller;
        mOnFinishedListener = onFinishedListener;
        mAllowMinimizeSplitScreen = allowMinimizeSplitScreen;
    }

    /**
     * Synchronously takes a screenshot of the task with the given {@param taskId} if the task is
     * currently being animated.
     */
    public ThumbnailData screenshotTask(int taskId) {
        return mController.screenshotTask(taskId);
    }

    /**
     * Indicates that the gesture has crossed the window boundary threshold and system UI can be
     * update the system bar flags accordingly.
     */
    public void setUseLauncherSystemBarFlags(boolean useLauncherSysBarFlags) {
        if (mUseLauncherSysBarFlags != useLauncherSysBarFlags) {
            mUseLauncherSysBarFlags = useLauncherSysBarFlags;
            UI_HELPER_EXECUTOR.execute(() -> {
                mController.setAnimationTargetsBehindSystemBars(!useLauncherSysBarFlags);
            });
        }
    }

    /**
     * Indicates that the gesture has crossed the window boundary threshold and we should minimize
     * if we are in splitscreen.
     */
    public void setSplitScreenMinimized(boolean splitScreenMinimized) {
        if (!mAllowMinimizeSplitScreen) {
            return;
        }
        if (mSplitScreenMinimized != splitScreenMinimized) {
            mSplitScreenMinimized = splitScreenMinimized;
            UI_HELPER_EXECUTOR.execute(() -> {
                SystemUiProxy p = SystemUiProxy.INSTANCE.getNoCreate();
                if (p != null) {
                    p.setSplitScreenMinimized(splitScreenMinimized);
                }
            });
        }
    }

    /**
     * Notifies the controller that we want to defer cancel until the next app transition starts.
     * If {@param screenshot} is set, then we will receive a screenshot on the next
     * {@link RecentsAnimationCallbacks#onAnimationCanceled(ThumbnailData)} and we must also call
     * {@link #cleanupScreenshot()} when that screenshot is no longer used.
     */
    public void setDeferCancelUntilNextTransition(boolean defer, boolean screenshot) {
        mController.setDeferCancelUntilNextTransition(defer, screenshot);
    }

    /**
     * Cleans up the screenshot previously returned from
     * {@link RecentsAnimationCallbacks#onAnimationCanceled(ThumbnailData)}.
     */
    public void cleanupScreenshot() {
        UI_HELPER_EXECUTOR.execute(() -> mController.cleanupScreenshot());
    }

    /**
     * Remove task remote animation target from
     * {@link RecentsAnimationCallbacks#onTaskAppeared(RemoteAnimationTargetCompat)}}.
     */
    @UiThread
    public boolean removeTaskTarget(@NonNull RemoteAnimationTargetCompat target) {
        return mController.removeTask(target.taskId);
    }

    @UiThread
    public void finishAnimationToHome() {
        finishController(true /* toRecents */, null, false /* sendUserLeaveHint */);
    }

    @UiThread
    public void finishAnimationToApp() {
        finishController(false /* toRecents */, null, false /* sendUserLeaveHint */);
    }

    /** See {@link #finish(boolean, Runnable, boolean)} */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete) {
        finish(toRecents, onFinishComplete, false /* sendUserLeaveHint */);
    }

    /**
     * @param onFinishComplete A callback that runs on the main thread after the animation
     *                         controller has finished on the background thread.
     * @param sendUserLeaveHint Determines whether userLeaveHint flag will be set on the pausing
     *                          activity. If userLeaveHint is true, the activity will enter into
     *                          picture-in-picture mode upon being paused.
     */
    @UiThread
    public void finish(boolean toRecents, Runnable onFinishComplete, boolean sendUserLeaveHint) {
        Preconditions.assertUIThread();
        finishController(toRecents, onFinishComplete, sendUserLeaveHint);
    }

    @UiThread
    public void finishController(boolean toRecents, Runnable callback, boolean sendUserLeaveHint) {
        mOnFinishedListener.accept(this);
        UI_HELPER_EXECUTOR.execute(() -> {
            mController.finish(toRecents, sendUserLeaveHint);
            if (callback != null) {
                MAIN_EXECUTOR.execute(callback);
            }
        });
    }

    /**
     * Enables the input consumer to start intercepting touches in the app window.
     */
    public void enableInputConsumer() {
        UI_HELPER_EXECUTOR.submit(() -> {
            mController.hideCurrentInputMethod();
            mController.setInputConsumerEnabled(true);
        });
    }

    /** @return wrapper controller. */
    public RecentsAnimationControllerCompat getController() {
        return mController;
    }
}
