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

import com.android.launcher3.util.Preconditions;
import com.android.quickstep.util.RecentsAnimationListenerSet;
import com.android.quickstep.util.SwipeAnimationTargetSet;
import com.android.quickstep.util.SwipeAnimationTargetSet.SwipeAnimationListener;

/**
 * Utility class used to store state information shared across multiple transitions.
 */
public class SwipeSharedState implements SwipeAnimationListener {

    private RecentsAnimationListenerSet mRecentsAnimationListener;
    private SwipeAnimationTargetSet mLastAnimationTarget;
    private boolean mLastAnimationCancelled = false;

    public boolean canGestureBeContinued;
    public boolean goingToLauncher;

    @Override
    public final void onRecentsAnimationStart(SwipeAnimationTargetSet targetSet) {
        mLastAnimationTarget = targetSet;
        mLastAnimationCancelled = false;
    }

    @Override
    public final void onRecentsAnimationCanceled() {
        mLastAnimationTarget = null;
        mLastAnimationCancelled = true;
    }

    private void clearListenerState() {
        if (mRecentsAnimationListener != null) {
            mRecentsAnimationListener.removeListener(this);
        }
        mRecentsAnimationListener = null;
        mLastAnimationTarget = null;
        mLastAnimationCancelled = false;
    }

    public RecentsAnimationListenerSet newRecentsAnimationListenerSet() {
        Preconditions.assertUIThread();
        clearListenerState();
        mRecentsAnimationListener = new RecentsAnimationListenerSet();
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
