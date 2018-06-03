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

import android.annotation.TargetApi;
import android.os.Build;
import android.view.Choreographer;
import android.view.MotionEvent;
import android.view.VelocityTracker;

/**
 * A TouchConsumer which defers all events on the UIThread until the consumer is created.
 */
@TargetApi(Build.VERSION_CODES.P)
public class DeferredTouchConsumer implements TouchConsumer {

    private final VelocityTracker mVelocityTracker;
    private final DeferredTouchProvider mTouchProvider;

    private MotionEventQueue mMyQueue;
    private TouchConsumer mTarget;

    public DeferredTouchConsumer(DeferredTouchProvider touchProvider) {
        mVelocityTracker = VelocityTracker.obtain();
        mTouchProvider = touchProvider;
    }

    @Override
    public void accept(MotionEvent event) {
        mTarget.accept(event);
    }

    @Override
    public void reset() {
        mTarget.reset();
    }

    @Override
    public void updateTouchTracking(int interactionType) {
        mTarget.updateTouchTracking(interactionType);
    }

    @Override
    public void onQuickScrubEnd() {
        mTarget.onQuickScrubEnd();
    }

    @Override
    public void onQuickScrubProgress(float progress) {
        mTarget.onQuickScrubProgress(progress);
    }

    @Override
    public void onQuickStep(MotionEvent ev) {
        mTarget.onQuickStep(ev);
    }

    @Override
    public void onCommand(int command) {
        mTarget.onCommand(command);
    }

    @Override
    public void preProcessMotionEvent(MotionEvent ev) {
        mVelocityTracker.addMovement(ev);
    }

    @Override
    public Choreographer getIntrimChoreographer(MotionEventQueue queue) {
        mMyQueue = queue;
        return null;
    }

    @Override
    public void deferInit() {
        mTarget = mTouchProvider.createTouchConsumer(mVelocityTracker);
        mTarget.getIntrimChoreographer(mMyQueue);
    }

    @Override
    public boolean forceToLauncherConsumer() {
        return mTarget.forceToLauncherConsumer();
    }

    @Override
    public boolean deferNextEventToMainThread() {
        // If our target is still null, defer the next target as well
        TouchConsumer target = mTarget;
        return target == null ? true : target.deferNextEventToMainThread();
    }

    @Override
    public void onShowOverviewFromAltTab() {
        mTarget.onShowOverviewFromAltTab();
    }

    public interface DeferredTouchProvider {

        TouchConsumer createTouchConsumer(VelocityTracker tracker);
    }
}
