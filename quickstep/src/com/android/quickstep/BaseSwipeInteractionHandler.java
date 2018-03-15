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

import android.support.annotation.WorkerThread;

import com.android.launcher3.states.InternalStateHandler;
import com.android.quickstep.TouchConsumer.InteractionType;

public abstract class BaseSwipeInteractionHandler extends InternalStateHandler {

    protected Runnable mGestureEndCallback;
    protected boolean mIsGoingToHome;

    public void setGestureEndCallback(Runnable gestureEndCallback) {
        mGestureEndCallback = gestureEndCallback;
    }

    public void reset() {}

    @WorkerThread
    public abstract void onGestureStarted();

    @WorkerThread
    public abstract void onGestureEnded(float endVelocity);

    public abstract void updateInteractionType(@InteractionType int interactionType);

    @WorkerThread
    public abstract void onQuickScrubEnd();

    @WorkerThread
    public abstract void onQuickScrubProgress(float progress);

    @WorkerThread
    public abstract void updateDisplacement(float displacement);
}
