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

import static com.android.systemui.shared.system.ActivityManagerWrapper
        .CLOSE_SYSTEM_WINDOWS_REASON_RECENTS;
import static com.android.systemui.shared.system.RemoteAnimationTargetCompat.MODE_CLOSING;

import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.os.SystemClock;
import android.util.SparseArray;
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.util.TraceHelper;
import com.android.quickstep.util.RemoteAnimationTargetSet;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.AssistDataReceiver;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.NavigationBarCompat;
import com.android.systemui.shared.system.NavigationBarCompat.HitTarget;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Touch consumer for handling events originating from an activity other than Launcher
 */
@TargetApi(Build.VERSION_CODES.P)
public class OtherActivityTouchConsumer extends ContextWrapper implements TouchConsumer {

    private static final long LAUNCHER_DRAW_TIMEOUT_MS = 150;

    private final SparseArray<RecentsAnimationState> mAnimationStates = new SparseArray<>();
    private final RunningTaskInfo mRunningTask;
    private final RecentsModel mRecentsModel;
    private final Intent mHomeIntent;
    private final ActivityControlHelper mActivityControlHelper;
    private final MainThreadExecutor mMainThreadExecutor;
    private final Choreographer mBackgroundThreadChoreographer;
    private final OverviewCallbacks mOverviewCallbacks;
    private final TaskOverlayFactory mTaskOverlayFactory;

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

    private VelocityTracker mVelocityTracker;
    private MotionEventQueue mEventQueue;
    private boolean mIsGoingToHome;

    public OtherActivityTouchConsumer(Context base, RunningTaskInfo runningTaskInfo,
            RecentsModel recentsModel, Intent homeIntent, ActivityControlHelper activityControl,
            MainThreadExecutor mainThreadExecutor, Choreographer backgroundThreadChoreographer,
            @HitTarget int downHitTarget, OverviewCallbacks overviewCallbacks,
            TaskOverlayFactory taskOverlayFactory, VelocityTracker velocityTracker) {
        super(base);

        mRunningTask = runningTaskInfo;
        mRecentsModel = recentsModel;
        mHomeIntent = homeIntent;
        mVelocityTracker = velocityTracker;
        mActivityControlHelper = activityControl;
        mMainThreadExecutor = mainThreadExecutor;
        mBackgroundThreadChoreographer = backgroundThreadChoreographer;
        mIsDeferredDownTarget = activityControl.deferStartingActivity(downHitTarget);
        mOverviewCallbacks = overviewCallbacks;
        mTaskOverlayFactory = taskOverlayFactory;
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
        switch (ev.getActionMasked()) {
            case ACTION_DOWN: {
                TraceHelper.beginSection("TouchInt");
                mActivePointerId = ev.getPointerId(0);
                mDownPos.set(ev.getX(), ev.getY());
                mLastPos.set(mDownPos);
                mPassedInitialSlop = false;
                mQuickStepDragSlop = NavigationBarCompat.getQuickStepDragSlopPx();

                // Start the window animation on down to give more time for launcher to draw if the
                // user didn't start the gesture over the back button
                if (!mIsDeferredDownTarget) {
                    startTouchTrackingForWindowAnimation(ev.getEventTime());
                }

                Display display = getSystemService(WindowManager.class).getDefaultDisplay();
                mDisplayRotation = display.getRotation();
                WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);
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
                    mInteractionHandler.updateDisplacement(displacement - mStartDisplacement);
                }
                break;
            }
            case ACTION_CANCEL:
                // TODO: Should be different than ACTION_UP
            case ACTION_UP: {
                TraceHelper.endSection("TouchInt");

                finishTouchTracking(ev);
                break;
            }
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
        // Create the shared handler
        RecentsAnimationState animationState = new RecentsAnimationState();
        final WindowTransformSwipeHandler handler = new WindowTransformSwipeHandler(
                animationState.id, mRunningTask, this, touchTimeMs, mActivityControlHelper);

        // Preload the plan
        mRecentsModel.loadTasks(mRunningTask.id, null);
        mInteractionHandler = handler;
        handler.setGestureEndCallback(mEventQueue::reset);

        CountDownLatch drawWaitLock = new CountDownLatch(1);
        handler.setLauncherOnDrawCallback(() -> {
            drawWaitLock.countDown();
            if (handler == mInteractionHandler) {
                switchToMainChoreographer();
            }
        });
        handler.initWhenReady();

        TraceHelper.beginSection("RecentsController");

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

        Runnable startActivity = () -> ActivityManagerWrapper.getInstance().startRecentsActivity(
                mHomeIntent, assistDataReceiver, animationState, null, null);

        if (Looper.myLooper() != Looper.getMainLooper()) {
            startActivity.run();
            try {
                drawWaitLock.await(LAUNCHER_DRAW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                // We have waited long enough for launcher to draw
            }
        } else {
            // We should almost always get touch-town on background thread. This is an edge case
            // when the background Choreographer has not yet initialized.
            BackgroundExecutor.get().submit(startActivity);
        }
    }

    @Override
    public void onCommand(int command) {
        RecentsAnimationState state = mAnimationStates.get(command);
        if (state != null) {
            state.execute();
        }
    }

    /**
     * Called when the gesture has ended. Does not correlate to the completion of the interaction as
     * the animation can still be running.
     */
    private void finishTouchTracking(MotionEvent ev) {
        if (mPassedInitialSlop && mInteractionHandler != null) {
            mInteractionHandler.updateDisplacement(getDisplacement(ev) - mStartDisplacement);

            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(this).getScaledMaximumFlingVelocity());

            float velocity = isNavBarOnRight() ? mVelocityTracker.getXVelocity(mActivePointerId)
                    : isNavBarOnLeft() ? -mVelocityTracker.getXVelocity(mActivePointerId)
                            : mVelocityTracker.getYVelocity(mActivePointerId);
            mInteractionHandler.onGestureEnded(velocity);
        } else {
            // Since we start touch tracking on DOWN, we may reach this state without actually
            // starting the gesture. In that case, just cleanup immediately.
            reset();

            // Also clean up in case the system has handled the UP and canceled the animation before
            // we had a chance to start the recents animation. In such a case, we will not receive
            ActivityManagerWrapper.getInstance().cancelRecentsAnimation(
                    true /* restoreHomeStackPosition */);
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    @Override
    public void reset() {
        // Clean up the old interaction handler
        if (mInteractionHandler != null) {
            final WindowTransformSwipeHandler handler = mInteractionHandler;
            mInteractionHandler = null;
            mIsGoingToHome = handler.mIsGoingToHome;
            mMainThreadExecutor.execute(handler::reset);
        }
    }

    @Override
    public void updateTouchTracking(int interactionType) {
        if (!mPassedInitialSlop && mIsDeferredDownTarget && mInteractionHandler == null) {
            // If we deferred starting the window animation on touch down, then
            // start tracking now
            startTouchTrackingForWindowAnimation(SystemClock.uptimeMillis());
            mPassedInitialSlop = true;
        }

        if (mInteractionHandler != null) {
            mInteractionHandler.updateInteractionType(interactionType);
        }
        notifyGestureStarted();
    }

    @Override
    public Choreographer getIntrimChoreographer(MotionEventQueue queue) {
        mEventQueue = queue;
        return mBackgroundThreadChoreographer;
    }

    @Override
    public void onQuickScrubEnd() {
        if (mInteractionHandler != null) {
            mInteractionHandler.onQuickScrubEnd();
        }
    }

    @Override
    public void onQuickScrubProgress(float progress) {
        if (mInteractionHandler != null) {
            mInteractionHandler.onQuickScrubProgress(progress);
        }
    }

    @Override
    public void onQuickStep(MotionEvent ev) {
        if (mIsDeferredDownTarget) {
            // Deferred gesture, start the animation and gesture tracking once we pass the actual
            // touch slop
            startTouchTrackingForWindowAnimation(ev.getEventTime());
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

    public void switchToMainChoreographer() {
        mEventQueue.setInterimChoreographer(null);
    }

    @Override
    public void preProcessMotionEvent(MotionEvent ev) {
        if (mVelocityTracker != null) {
           mVelocityTracker.addMovement(ev);
           if (ev.getActionMasked() == ACTION_POINTER_UP) {
               mVelocityTracker.clear();
           }
        }
    }

    @Override
    public boolean forceToLauncherConsumer() {
        return mIsGoingToHome;
    }

    @Override
    public boolean deferNextEventToMainThread() {
        // TODO: Consider also check if the eventQueue is using mainThread of not.
        return mInteractionHandler != null;
    }

    private class RecentsAnimationState implements RecentsAnimationListener {

        private final int id;

        private RecentsAnimationControllerCompat mController;
        private RemoteAnimationTargetSet mTargets;
        private Rect mHomeContentInsets;
        private Rect mMinimizedHomeBounds;
        private boolean mCancelled;

        public RecentsAnimationState() {
            id = mAnimationStates.size();
            mAnimationStates.put(id, this);
        }

        @Override
        public void onAnimationStart(
                RecentsAnimationControllerCompat controller,
                RemoteAnimationTargetCompat[] apps, Rect homeContentInsets,
                Rect minimizedHomeBounds) {
            mController = controller;
            mTargets = new RemoteAnimationTargetSet(apps, MODE_CLOSING);
            mHomeContentInsets = homeContentInsets;
            mMinimizedHomeBounds = minimizedHomeBounds;
            mEventQueue.onCommand(id);
        }

        @Override
        public void onAnimationCanceled() {
            mCancelled = true;
            mEventQueue.onCommand(id);
        }

        public void execute() {
            if (mInteractionHandler == null || mInteractionHandler.id != id) {
                if (!mCancelled && mController != null) {
                    TraceHelper.endSection("RecentsController", "Finishing no handler");
                    mController.finish(false /* toHome */);
                }
            } else if (mCancelled) {
                TraceHelper.endSection("RecentsController",
                        "Cancelled: " + mInteractionHandler);
                mInteractionHandler.onRecentsAnimationCanceled();
            } else {
                TraceHelper.partitionSection("RecentsController", "Received");
                mInteractionHandler.onRecentsAnimationStart(mController, mTargets,
                        mHomeContentInsets, mMinimizedHomeBounds);
            }
        }
    }
}
