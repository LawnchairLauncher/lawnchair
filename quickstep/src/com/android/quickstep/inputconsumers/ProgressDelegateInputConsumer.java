/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.quickstep.inputconsumers;

import static com.android.app.animation.Interpolators.scrollInterpolatorForVelocity;
import static com.android.launcher3.touch.BaseSwipeDetector.calculateDuration;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_POSITIVE;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;
import static com.android.quickstep.util.ActiveGestureLog.INTENT_EXTRA_LOG_TRACE_ID;

import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.view.MotionEvent;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.touch.SingleAxisSwipeDetector;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.MultiStateCallback;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.TaskAnimationManager;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputMonitorCompat;

import java.util.HashMap;

/**
 * Input consumer which delegates the swipe-progress handling
 */
public class ProgressDelegateInputConsumer implements InputConsumer,
        RecentsAnimationCallbacks.RecentsAnimationListener,
        SingleAxisSwipeDetector.Listener {

    private static final float SWIPE_DISTANCE_THRESHOLD = 0.2f;

    private static final String[] STATE_NAMES = DEBUG_STATES ? new String[3] : null;
    private static int getFlagForIndex(int index, String name) {
        if (DEBUG_STATES) {
            STATE_NAMES[index] = name;
        }
        return 1 << index;
    }

    private static final int STATE_TARGET_RECEIVED =
            getFlagForIndex(0, "STATE_TARGET_RECEIVED");
    private static final int STATE_HANDLER_INVALIDATED =
            getFlagForIndex(1, "STATE_HANDLER_INVALIDATED");
    private static final int STATE_FLING_FINISHED =
            getFlagForIndex(2, "STATE_FLING_FINISHED");

    private final Context mContext;
    private final TaskAnimationManager mTaskAnimationManager;
    private final GestureState mGestureState;
    private final InputMonitorCompat mInputMonitorCompat;
    private final MultiStateCallback mStateCallback;

    private final Point mDisplaySize;
    private final SingleAxisSwipeDetector mSwipeDetector;

    private final AnimatedFloat mProgress;

    private boolean mDragStarted = false;

    private RecentsAnimationController mRecentsAnimationController;
    private Boolean mFlingEndsOnHome;

    public ProgressDelegateInputConsumer(Context context,
            TaskAnimationManager taskAnimationManager, GestureState gestureState,
            InputMonitorCompat inputMonitorCompat, AnimatedFloat progress) {
        mContext = context;
        mTaskAnimationManager = taskAnimationManager;
        mGestureState = gestureState;
        mInputMonitorCompat = inputMonitorCompat;
        mProgress = progress;

        // Do not use DeviceProfile as the user data might be locked
        mDisplaySize = DisplayController.INSTANCE.get(context).getInfo().currentSize;

        // Init states
        mStateCallback = new MultiStateCallback(STATE_NAMES);
        mStateCallback.runOnceAtState(STATE_TARGET_RECEIVED | STATE_HANDLER_INVALIDATED,
                this::endRemoteAnimation);
        mStateCallback.runOnceAtState(STATE_TARGET_RECEIVED | STATE_FLING_FINISHED,
                this::onFlingFinished);

        mSwipeDetector = new SingleAxisSwipeDetector(mContext, this, VERTICAL);
        mSwipeDetector.setDetectableScrollConditions(DIRECTION_POSITIVE, false);
    }

    @Override
    public int getType() {
        return TYPE_PROGRESS_DELEGATE;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mFlingEndsOnHome == null) {
            mSwipeDetector.onTouchEvent(ev);
        }
    }

    @Override
    public void onDragStart(boolean start, float startDisplacement) {
        mDragStarted = true;
        TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
        mInputMonitorCompat.pilferPointers();
        Intent intent = mGestureState.getHomeIntent()
                .putExtra(INTENT_EXTRA_LOG_TRACE_ID, mGestureState.getGestureId());
        mTaskAnimationManager.startRecentsAnimation(mGestureState, intent, this);
    }

    @Override
    public boolean onDrag(float displacement) {
        if (mDisplaySize.y > 0) {
            mProgress.updateValue(displacement / -mDisplaySize.y);
        }
        return true;
    }

    @Override
    public void onDragEnd(float velocity) {
        final boolean willExit;
        if (mSwipeDetector.isFling(velocity)) {
            willExit = velocity < 0;
        } else {
            willExit = mProgress.value > SWIPE_DISTANCE_THRESHOLD;
        }
        float endValue = willExit ? 1 : 0;
        long duration = calculateDuration(velocity, endValue - mProgress.value);
        mFlingEndsOnHome = willExit;

        ObjectAnimator anim = mProgress.animateToValue(endValue);
        anim.setDuration(duration).setInterpolator(scrollInterpolatorForVelocity(velocity));
        anim.addListener(AnimatorListeners.forSuccessCallback(
                () -> mStateCallback.setState(STATE_FLING_FINISHED)));
        anim.start();
    }

    private void onFlingFinished() {
        boolean endToRecents = mFlingEndsOnHome == null ? true : mFlingEndsOnHome;
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.finishController(endToRecents /* toRecents */,
                    null /* callback */, false /* sendUserLeaveHint */);
        } else if (endToRecents) {
            startHomeIntentSafely(mContext, null);
        }
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        mRecentsAnimationController = controller;
        mStateCallback.setState(STATE_TARGET_RECEIVED);
    }

    @Override
    public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
        mRecentsAnimationController = null;
    }

    private void endRemoteAnimation() {
        onDragEnd(Float.MIN_VALUE);
    }

    @Override
    public void onConsumerAboutToBeSwitched() {
        mStateCallback.setState(STATE_HANDLER_INVALIDATED);
    }

    @Override
    public boolean allowInterceptByParent() {
        return !mDragStarted;
    }
}
