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

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_POINTER_UP;
import static android.view.MotionEvent.ACTION_UP;
import static android.view.MotionEvent.INVALID_POINTER_ID;

import static com.android.launcher3.util.RaceConditionTracker.ENTER;
import static com.android.launcher3.util.RaceConditionTracker.EXIT;
import static com.android.systemui.shared.system.ActivityManagerWrapper.CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.RaceConditionTracker;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.WindowTransformSwipeHandler.GestureEndTarget;
import com.android.quickstep.util.MotionPauseDetector;
import com.android.quickstep.util.RecentsAnimationListenerSet;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.AssistDataReceiver;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.InputConsumerController;
import com.android.systemui.shared.system.NavigationBarCompat;
import com.android.systemui.shared.system.NavigationBarCompat.HitTarget;
import com.android.systemui.shared.system.WindowManagerWrapper;

import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

/**
 * Touch consumer for handling events originating from an activity other than Launcher
 */
@TargetApi(Build.VERSION_CODES.P)
public class OtherActivityTouchConsumer extends ContextWrapper implements TouchConsumer {

    public static final String DOWN_EVT = "OtherActivityTouchConsumer.DOWN";
    private static final String UP_EVT = "OtherActivityTouchConsumer.UP";

    private final RunningTaskInfo mRunningTask;
    private final RecentsModel mRecentsModel;
    private final Intent mHomeIntent;
    private final ActivityControlHelper mActivityControlHelper;
    private final OverviewCallbacks mOverviewCallbacks;
    private final TaskOverlayFactory mTaskOverlayFactory;
    private final TouchInteractionLog mTouchInteractionLog;
    private final InputConsumerController mInputConsumer;
    private final SwipeSharedState mSwipeSharedState;

    private final MotionEventQueue mEventQueue;
    private final MotionPauseDetector mMotionPauseDetector;
    private VelocityTracker mVelocityTracker;

    private final boolean mIsDeferredDownTarget;
    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;
    private boolean mPassedInitialSlop;
    // Used for non-deferred gestures to determine when to start dragging
    private int mQuickStepDragSlop;
    private float mStartDisplacement;
    private WindowTransformSwipeHandler mInteractionHandler;
    private int mDisplayRotation;
    private Rect mStableInsets = new Rect();

    public OtherActivityTouchConsumer(Context base, RunningTaskInfo runningTaskInfo,
            RecentsModel recentsModel, Intent homeIntent, ActivityControlHelper activityControl,
            @HitTarget int downHitTarget, OverviewCallbacks overviewCallbacks,
            TaskOverlayFactory taskOverlayFactory, InputConsumerController inputConsumer,
            TouchInteractionLog touchInteractionLog, MotionEventQueue eventQueue,
            SwipeSharedState swipeSharedState) {
        super(base);

        mRunningTask = runningTaskInfo;
        mRecentsModel = recentsModel;
        mHomeIntent = homeIntent;

        mMotionPauseDetector = new MotionPauseDetector(base);
        mEventQueue = eventQueue;
        mVelocityTracker = VelocityTracker.obtain();

        mActivityControlHelper = activityControl;
        mIsDeferredDownTarget = activityControl.deferStartingActivity(downHitTarget);
        mOverviewCallbacks = overviewCallbacks;
        mTaskOverlayFactory = taskOverlayFactory;
        mTouchInteractionLog = touchInteractionLog;
        mTouchInteractionLog.setTouchConsumer(this);
        mInputConsumer = inputConsumer;
        mSwipeSharedState = swipeSharedState;
    }

    @Override
    public void onShowOverviewFromAltTab() {
        startTouchTrackingForWindowAnimation(SystemClock.uptimeMillis());
    }

    @Override
    public void accept(MotionEvent ev) {
        if (mVelocityTracker == null) {
            return;
        }
        mVelocityTracker.addMovement(ev);
        if (ev.getActionMasked() == ACTION_POINTER_UP) {
            mVelocityTracker.clear();
            mMotionPauseDetector.clear();
        }

        mTouchInteractionLog.addMotionEvent(ev);
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                RaceConditionTracker.onEvent(DOWN_EVT, ENTER);
                TraceHelper.beginSection("TouchInt");
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                // If active listener isn't null, we are continuing the previous gesture.
                mPassedInitialSlop = mSwipeSharedState.getActiveListener() != null;
                mQuickStepDragSlop = NavigationBarCompat.getQuickStepDragSlopPx();

                // Start the window animation on down to give more time for launcher to draw if the
                // user didn't start the gesture over the back button
                if (!mIsDeferredDownTarget) {
                    startTouchTrackingForWindowAnimation(ev.getEventTime());
                }

                Display display = getSystemService(WindowManager.class).getDefaultDisplay();
                mDisplayRotation = display.getRotation();
                WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);
                RaceConditionTracker.onEvent(DOWN_EVT, EXIT);
                break;
            }
            case ACTION_POINTER_UP: {
                int ptrIdx = ev.getActionIndex();
                int ptrId = ev.getPointerId(ptrIdx);
                if (ptrId == mActivePointerId) {
                    final int newPointerIdx = ptrIdx == 0 ? 1 : 0;
                    mDownPos.set(
                            ev.getX(newPointerIdx) - (mLastPos.x - mDownPos.x),
                            ev.getY(newPointerIdx) - (mLastPos.y - mDownPos.y));
                    mLastPos.set(ev.getX(newPointerIdx), ev.getY(newPointerIdx));
                    mActivePointerId = ev.getPointerId(newPointerIdx);
                }
                break;
            }
            case ACTION_MOVE: {
                int pointerIndex = ev.findPointerIndex(mActivePointerId);
                if (pointerIndex == INVALID_POINTER_ID) {
                    break;
                }
                mLastPos.set(ev.getX(pointerIndex), ev.getY(pointerIndex));
                float displacement = getDisplacement(ev);
                if (!mPassedInitialSlop) {
                    if (!mIsDeferredDownTarget) {
                        // Normal gesture, ensure we pass the drag slop before we start tracking
                        // the gesture
                        if (Math.abs(displacement) > mQuickStepDragSlop) {
                            mPassedInitialSlop = true;
                            mStartDisplacement = displacement;
                        }
                    }
                }

                if (mPassedInitialSlop && mInteractionHandler != null) {
                    // Move
                    dispatchMotion(ev, displacement - mStartDisplacement, null);

                    if (FeatureFlags.SWIPE_HOME.get()) {
                        boolean isLandscape = isNavBarOnLeft() || isNavBarOnRight();
                        float orthogonalDisplacement = !isLandscape
                                ? ev.getX() - mDownPos.x
                                : ev.getY() - mDownPos.y;
                        mMotionPauseDetector.addPosition(displacement, orthogonalDisplacement);
                    }
                }
                break;
            }
            case ACTION_CANCEL:
                // TODO: Should be different than ACTION_UP
            case ACTION_UP: {
                RaceConditionTracker.onEvent(UP_EVT, ENTER);
                TraceHelper.endSection("TouchInt");

                finishTouchTracking(ev);
                RaceConditionTracker.onEvent(UP_EVT, EXIT);
                break;
            }
        }
    }

    private void dispatchMotion(MotionEvent ev, float displacement, @Nullable Float velocityX) {
        mInteractionHandler.updateDisplacement(displacement);
        boolean isLandscape = isNavBarOnLeft() || isNavBarOnRight();
        if (!isLandscape) {
            mInteractionHandler.dispatchMotionEventToRecentsView(ev, velocityX);
        }
    }

    private void notifyGestureStarted() {
        if (mInteractionHandler == null) {
            return;
        }

        mOverviewCallbacks.closeAllWindows();
        ActivityManagerWrapper.getInstance().closeSystemWindows(
                CLOSE_SYSTEM_WINDOWS_REASON_RECENTS);

        // Notify the handler that the gesture has actually started
        mInteractionHandler.onGestureStarted();
    }

    private boolean isNavBarOnRight() {
        return mDisplayRotation == Surface.ROTATION_90 && mStableInsets.right > 0;
    }

    private boolean isNavBarOnLeft() {
        return mDisplayRotation == Surface.ROTATION_270 && mStableInsets.left > 0;
    }

    private void startTouchTrackingForWindowAnimation(long touchTimeMs) {
        mTouchInteractionLog.startRecentsAnimation();

        RecentsAnimationListenerSet listenerSet = mSwipeSharedState.getActiveListener();
        final WindowTransformSwipeHandler handler = new WindowTransformSwipeHandler(
                mRunningTask, this, touchTimeMs, mActivityControlHelper,
                listenerSet != null, mInputConsumer, mTouchInteractionLog);

        // Preload the plan
        mRecentsModel.getTasks(null);
        mInteractionHandler = handler;
        handler.setGestureEndCallback(this::onInteractionGestureFinished);
        mMotionPauseDetector.setOnMotionPauseListener(handler::onMotionPauseChanged);
        handler.initWhenReady();

        TraceHelper.beginSection("RecentsController");

        if (listenerSet != null) {
            listenerSet.addListener(handler);
            mSwipeSharedState.applyActiveRecentsAnimationState(handler);
        } else {
            AssistDataReceiver assistDataReceiver = !mTaskOverlayFactory.needAssist() ? null :
                    new AssistDataReceiver() {
                        @Override
                        public void onHandleAssistData(Bundle bundle) {
                            if (mInteractionHandler == null) {
                                // Interaction is probably complete
                                mRecentsModel.preloadAssistData(mRunningTask.id, bundle);
                            } else if (handler == mInteractionHandler) {
                                handler.onAssistDataReceived(bundle);
                            }
                        }
                    };

            RecentsAnimationListenerSet newListenerSet =
                    mSwipeSharedState.newRecentsAnimationListenerSet();
            newListenerSet.addListener(handler);
            BackgroundExecutor.get().submit(
                    () -> ActivityManagerWrapper.getInstance().startRecentsActivity(
                            mHomeIntent, assistDataReceiver, newListenerSet,
                            null, null));
        }
    }

    /**
     * Called when the gesture has ended. Does not correlate to the completion of the interaction as
     * the animation can still be running.
     */
    private void finishTouchTracking(MotionEvent ev) {
        if (mPassedInitialSlop && mInteractionHandler != null) {

            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(this).getScaledMaximumFlingVelocity());
            float velocityX = mVelocityTracker.getXVelocity(mActivePointerId);
            float velocity = isNavBarOnRight() ? velocityX
                    : isNavBarOnLeft() ? -velocityX
                            : mVelocityTracker.getYVelocity(mActivePointerId);

            dispatchMotion(ev, getDisplacement(ev) - mStartDisplacement, velocityX);

            mInteractionHandler.onGestureEnded(velocity, velocityX);
        } else {
            // Since we start touch tracking on DOWN, we may reach this state without actually
            // starting the gesture. In that case, just cleanup immediately.
            onConsumerAboutToBeSwitched();
            onInteractionGestureFinished();

            // Also clean up in case the system has handled the UP and canceled the animation before
            // we had a chance to start the recents animation. In such a case, we will not receive
            ActivityManagerWrapper.getInstance().cancelRecentsAnimation(
                    true /* restoreHomeStackPosition */);
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    @Override
    public void onConsumerAboutToBeSwitched() {
        Preconditions.assertUIThread();
        if (mInteractionHandler != null) {
            // The consumer is being switched while we are active. Set up the shared state to be
            // used by the next animation
            removeListener();
            GestureEndTarget endTarget = mInteractionHandler.mGestureEndTarget;
            mSwipeSharedState.canGestureBeContinued = endTarget != null && endTarget.canBeContinued;
            mSwipeSharedState.goingToLauncher = endTarget != null && endTarget.isLauncher;
            if (mSwipeSharedState.canGestureBeContinued) {
                mInteractionHandler.cancel();
            } else {
                mInteractionHandler.reset();
            }
        }
    }

    @UiThread
    private void onInteractionGestureFinished() {
        Preconditions.assertUIThread();
        removeListener();
        mInteractionHandler = null;
        mEventQueue.onConsumerInactive(this);
    }

    private void removeListener() {
        RecentsAnimationListenerSet listenerSet = mSwipeSharedState.getActiveListener();
        if (listenerSet != null) {
            listenerSet.removeListener(mInteractionHandler);
        }
    }

    @Override
    public void onQuickScrubStart() {
        if (!mPassedInitialSlop && mIsDeferredDownTarget && mInteractionHandler == null) {
            // If we deferred starting the window animation on touch down, then
            // start tracking now
            startTouchTrackingForWindowAnimation(SystemClock.uptimeMillis());
            mPassedInitialSlop = true;
        }

        mTouchInteractionLog.startQuickScrub();
        if (mInteractionHandler != null) {
            mInteractionHandler.onQuickScrubStart();
        }
        notifyGestureStarted();
    }

    @Override
    public void onQuickScrubEnd() {
        mTouchInteractionLog.endQuickScrub("onQuickScrubEnd");
        if (mInteractionHandler != null) {
            mInteractionHandler.onQuickScrubEnd();
        }
    }

    @Override
    public void onQuickScrubProgress(float progress) {
        mTouchInteractionLog.setQuickScrubProgress(progress);
        if (mInteractionHandler != null) {
            mInteractionHandler.onQuickScrubProgress(progress);
        }
    }

    @Override
    public void onQuickStep(MotionEvent ev) {
        mTouchInteractionLog.startQuickStep();
        if (mIsDeferredDownTarget) {
            // Deferred gesture, start the animation and gesture tracking once we pass the actual
            // touch slop
            startTouchTrackingForWindowAnimation(ev.getEventTime());
        }
        if (!mPassedInitialSlop) {
            mPassedInitialSlop = true;
            mStartDisplacement = getDisplacement(ev);
        }
        notifyGestureStarted();
    }

    private float getDisplacement(MotionEvent ev) {
        float eventX = ev.getX();
        float eventY = ev.getY();
        float displacement = eventY - mDownPos.y;
        if (isNavBarOnRight()) {
            displacement = eventX - mDownPos.x;
        } else if (isNavBarOnLeft()) {
            displacement = mDownPos.x - eventX;
        }
        return displacement;
    }

    @Override
    public boolean isActive() {
        return mInteractionHandler != null;
    }
}
