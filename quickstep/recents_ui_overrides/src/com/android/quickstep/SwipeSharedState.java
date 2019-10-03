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
package com.android.quickstep;

import static com.android.launcher3.util.Executors.MAIN_EXECUTOR;

import android.util.Log;

import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.RecentsAnimationCallbacks.RecentsAnimationListener;

import com.android.systemui.shared.recents.model.ThumbnailData;

import java.io.PrintWriter;

/**
 * Utility class used to store state information shared across multiple transitions.
 */
public class SwipeSharedState implements RecentsAnimationListener {

    private OverviewComponentObserver mOverviewComponentObserver;

    private RecentsAnimationCallbacks mRecentsAnimationListener;
    private RecentsAnimationController mLastRecentsAnimationController;
    private RecentsAnimationTargets mLastAnimationTarget;

    private boolean mLastAnimationCancelled = false;
    private boolean mLastAnimationRunning = false;

    public boolean canGestureBeContinued;
    public boolean goingToLauncher;
    public boolean recentsAnimationFinishInterrupted;
    public int nextRunningTaskId = -1;
    private int mLogId;

    public void setOverviewComponentObserver(OverviewComponentObserver observer) {
        mOverviewComponentObserver = observer;
    }

    @Override
    public final void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        mLastRecentsAnimationController = controller;
        mLastAnimationTarget = targets;

        mLastAnimationCancelled = false;
        mLastAnimationRunning = true;
    }

    @Override
    public final void onRecentsAnimationCanceled(ThumbnailData thumbnailData) {
        if (thumbnailData != null) {
            mOverviewComponentObserver.getActivityControlHelper().switchToScreenshot(thumbnailData,
                    () -> {
                        mLastRecentsAnimationController.cleanupScreenshot();
                        clearAnimationState();
                    });
        } else {
            clearAnimationState();
        }
    }

    @Override
    public final void onRecentsAnimationFinished(RecentsAnimationController controller) {
        if (mLastRecentsAnimationController == controller) {
            mLastAnimationRunning = false;
        }
    }

    private void clearAnimationTarget() {
        if (mLastAnimationTarget != null) {
            mLastAnimationTarget.release();
            mLastAnimationTarget = null;
        }
    }

    private void clearAnimationState() {
        clearAnimationTarget();

        mLastAnimationCancelled = true;
        mLastAnimationRunning = false;
    }

    private void clearListenerState(boolean finishAnimation) {
        if (mRecentsAnimationListener != null) {
            mRecentsAnimationListener.removeListener(this);
            mRecentsAnimationListener.notifyAnimationCanceled();
            if (mLastAnimationRunning && mLastRecentsAnimationController != null) {
                Utilities.postAsyncCallback(MAIN_EXECUTOR.getHandler(),
                        finishAnimation
                                ? mLastRecentsAnimationController::finishAnimationToHome
                                : mLastRecentsAnimationController::finishAnimationToApp);
                mLastRecentsAnimationController = null;
                mLastAnimationTarget = null;
            }
        }
        mRecentsAnimationListener = null;
        clearAnimationTarget();
        mLastAnimationCancelled = false;
        mLastAnimationRunning = false;
    }

    public RecentsAnimationCallbacks newRecentsAnimationCallbacks() {
        Preconditions.assertUIThread();

        if (mLastAnimationRunning) {
            String msg = "New animation started before completing old animation";
            if (FeatureFlags.IS_DOGFOOD_BUILD) {
                throw new IllegalArgumentException(msg);
            } else {
                Log.e("SwipeSharedState", msg, new Exception());
            }
        }

        clearListenerState(false /* finishAnimation */);
        boolean shouldMinimiseSplitScreen = mOverviewComponentObserver == null ? false
                : mOverviewComponentObserver.getActivityControlHelper().shouldMinimizeSplitScreen();
        mRecentsAnimationListener = new RecentsAnimationCallbacks(shouldMinimiseSplitScreen);
        mRecentsAnimationListener.addListener(this);
        return mRecentsAnimationListener;
    }

    public RecentsAnimationCallbacks getActiveListener() {
        return mRecentsAnimationListener;
    }

    public void applyActiveRecentsAnimationState(RecentsAnimationListener listener) {
        if (mLastRecentsAnimationController != null) {
            listener.onRecentsAnimationStart(mLastRecentsAnimationController,
                    mLastAnimationTarget);
        } else if (mLastAnimationCancelled) {
            listener.onRecentsAnimationCanceled(null);
        }
    }

    /**
     * Called when a recents animation has finished, but was interrupted before the next task was
     * launched. The given {@param runningTaskId} should be used as the running task for the
     * continuing input consumer.
     */
    public void setRecentsAnimationFinishInterrupted(int runningTaskId) {
        recentsAnimationFinishInterrupted = true;
        nextRunningTaskId = runningTaskId;
        mLastAnimationTarget = mLastAnimationTarget.cloneWithoutTargets();
    }

    public void clearAllState(boolean finishAnimation) {
        clearListenerState(finishAnimation);
        canGestureBeContinued = false;
        recentsAnimationFinishInterrupted = false;
        nextRunningTaskId = -1;
        goingToLauncher = false;
    }

    public void dump(String prefix, PrintWriter pw) {
        pw.println(prefix + "goingToLauncher=" + goingToLauncher);
        pw.println(prefix + "canGestureBeContinued=" + canGestureBeContinued);
        pw.println(prefix + "recentsAnimationFinishInterrupted=" + recentsAnimationFinishInterrupted);
        pw.println(prefix + "nextRunningTaskId=" + nextRunningTaskId);
        pw.println(prefix + "lastAnimationCancelled=" + mLastAnimationCancelled);
        pw.println(prefix + "lastAnimationRunning=" + mLastAnimationRunning);
        pw.println(prefix + "logTraceId=" + mLogId);
    }

    public void setLogTraceId(int logId) {
        this.mLogId = logId;
    }
}
