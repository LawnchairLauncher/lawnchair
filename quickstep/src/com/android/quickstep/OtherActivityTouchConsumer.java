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
import static com.android.systemui.shared.system.NavigationBarCompat.HIT_TARGET_BACK;
import static com.android.systemui.shared.system.NavigationBarCompat.HIT_TARGET_OVERVIEW;
import static com.android.systemui.shared.system.NavigationBarCompat.QUICK_STEP_DRAG_SLOP_PX;

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
import android.view.Choreographer;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.AssistDataReceiver;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.NavigationBarCompat.HitTarget;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Touch consumer for handling events originating from an activity other than Launcher
 */
@TargetApi(Build.VERSION_CODES.P)
public class OtherActivityTouchConsumer extends ContextWrapper implements TouchConsumer {

    private static final long LAUNCHER_DRAW_TIMEOUT_MS = 150;
    private static final int[] DEFERRED_HIT_TARGETS = false
            ? new int[] {HIT_TARGET_BACK, HIT_TARGET_OVERVIEW} : new int[] {HIT_TARGET_BACK};

    private final RunningTaskInfo mRunningTask;
    private final RecentsModel mRecentsModel;
    private final Intent mHomeIntent;
    private final ActivityControlHelper mActivityControlHelper;
    private final MainThreadExecutor mMainThreadExecutor;
    private final Choreographer mBackgroundThreadChoreographer;

    private final boolean mIsDeferredDownTarget;
    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;
    private boolean mPassedInitialSlop;
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
            @HitTarget int downHitTarget, VelocityTracker velocityTracker) {
        super(base);
        mRunningTask = runningTaskInfo;
        mRecentsModel = recentsModel;
        mHomeIntent = homeIntent;
        mVelocityTracker = velocityTracker;
        mActivityControlHelper = activityControl;
        mMainThreadExecutor = mainThreadExecutor;
        mBackgroundThreadChoreographer = backgroundThreadChoreographer;
        mIsDeferredDownTarget = Arrays.binarySearch(DEFERRED_HIT_TARGETS, downHitTarget) >= 0;
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
                if (!mPassedInitialSlop && Math.abs(displacement) > QUICK_STEP_DRAG_SLOP_PX) {
                    mPassedInitialSlop = true;
                    mStartDisplacement = displacement;

                    // If we deferred starting the window animation on touch down, then
                    // start tracking now
                    if (mIsDeferredDownTarget) {
                        startTouchTrackingForWindowAnimation(ev.getEventTime());
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

                finishTouchTracking();
                break;
            }
        }
    }

    private void notifyGestureStarted() {
        if (mInteractionHandler == null) {
            return;
        }
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
        final WindowTransformSwipeHandler handler = new WindowTransformSwipeHandler(
                mRunningTask, this, touchTimeMs, mActivityControlHelper);

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
        Runnable startActivity = () -> mActivityControlHelper.startRecents(this, mHomeIntent,
                new AssistDataReceiver() {
                    @Override
                    public void onHandleAssistData(Bundle bundle) {
                        mRecentsModel.preloadAssistData(mRunningTask.id, bundle);
                    }
                },
                new RecentsAnimationListener() {
                    public void onAnimationStart(
                            RecentsAnimationControllerCompat controller,
                            RemoteAnimationTargetCompat[] apps, Rect homeContentInsets,
                            Rect minimizedHomeBounds) {
                        if (mInteractionHandler == handler) {
                            TraceHelper.partitionSection("RecentsController", "Received");
                            handler.onRecentsAnimationStart(controller, apps, homeContentInsets,
                                    minimizedHomeBounds);
                        } else {
                            TraceHelper.endSection("RecentsController", "Finishing no handler");
                            controller.finish(false /* toHome */);
                        }
                    }

                    public void onAnimationCanceled() {
                        TraceHelper.endSection("RecentsController",
                                "Cancelled: " + mInteractionHandler);
                        if (mInteractionHandler == handler) {
                            handler.onRecentsAnimationCanceled();
                        }
                    }
                });

        if (Looper.myLooper() != Looper.getMainLooper()) {
            startActivity.run();
            if (!mIsDeferredDownTarget) {
                try {
                    drawWaitLock.await(LAUNCHER_DRAW_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // We have waited long enough for launcher to draw
                }
            }
        } else {
            // We should almost always get touch-town on background thread. This is an edge case
            // when the background Choreographer has not yet initialized.
            BackgroundExecutor.get().submit(startActivity);
        }
    }

    /**
     * Called when the gesture has ended. Does not correlate to the completion of the interaction as
     * the animation can still be running.
     */
    private void finishTouchTracking() {
        if (mPassedInitialSlop && mInteractionHandler != null) {
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
        notifyGestureStarted();
        if (mInteractionHandler != null) {
            mInteractionHandler.updateInteractionType(interactionType);
        }
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
    public void onQuickStep(float eventX, float eventY, long eventTime) {
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
}
