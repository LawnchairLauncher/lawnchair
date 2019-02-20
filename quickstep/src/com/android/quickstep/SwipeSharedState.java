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

import android.util.Log;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.RecentsAnimationListenerSet;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;

/**
 * Utility class used to store state information shared across multiple transitions.
 */
public class SwipeSharedState implements SwipeAnimationListener {

    private final OverviewComponentObserver mOverviewComponentObserver;

    private RecentsAnimationListenerSet mRecentsAnimationListener;
    private SwipeAnimationTargetSet mLastAnimationTarget;

    private boolean mLastAnimationCancelled = false;
    private boolean mLastAnimationRunning = false;

    public boolean canGestureBeContinued;
    public boolean goingToLauncher;

    public SwipeSharedState(OverviewComponentObserver overviewComponentObserver) {
        mOverviewComponentObserver = overviewComponentObserver;
    }

    @Override
    public final void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        mLastAnimationTarget = targetSet;

        mLastAnimationCancelled = false;
        mLastAnimationRunning = true;
    }

    @Override
    public final void onRecentsAnimationCanceled() {
        mLastAnimationTarget = null;

        mLastAnimationCancelled = true;
        mLastAnimationRunning = false;
    }

    private void clearListenerState() {
        if (mRecentsAnimationListener != null) {
            mRecentsAnimationListener.removeListener(this);
        }
        mRecentsAnimationListener = null;
        mLastAnimationTarget = null;
        mLastAnimationCancelled = false;
        mLastAnimationRunning = false;
    }

    private void onSwipeAnimationFinished(SwipeAnimationTargetSet targetSet) {
        if (mLastAnimationTarget == targetSet) {
            mLastAnimationRunning = false;
        }
    }

    public RecentsAnimationListenerSet newRecentsAnimationListenerSet() {
        Preconditions.assertUIThread();

        if (mLastAnimationRunning) {
            String msg = "New animation started before completing old animation";
            if (FeatureFlags.IS_DOGFOOD_BUILD) {
                throw new IllegalArgumentException(msg);
            } else {
                Log.e("SwipeSharedState", msg, new Exception());
            }
        }

        clearListenerState();
        mRecentsAnimationListener = new RecentsAnimationListenerSet(mOverviewComponentObserver
                .getActivityControlHelper().shouldMinimizeSplitScreen(),
                this::onSwipeAnimationFinished);
        mRecentsAnimationListener.addListener(this);
        return mRecentsAnimationListener;
    }

    public RecentsAnimationListenerSet getActiveListener() {
        return mRecentsAnimationListener;
    }

    public void applyActiveRecentsAnimationState(SwipeAnimationListener listener) {
        if (mLastAnimationTarget != null) {
            listener.onRecentsAnimationStart(mLastAnimationTarget);
        } else if (mLastAnimationCancelled) {
            listener.onRecentsAnimationCanceled();
        }
    }

    public void clearAllState() {
        clearListenerState();
        canGestureBeContinued = false;
        goingToLauncher = false;
    }
}
