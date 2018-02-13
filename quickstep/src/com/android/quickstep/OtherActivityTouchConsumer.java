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

import static com.android.quickstep.RemoteRunnable.executeSafely;

import android.app.ActivityManager.RunningTaskInfo;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.WindowManager;

import com.android.launcher3.MainThreadExecutor;
import com.android.launcher3.Utilities;
import com.android.launcher3.util.TraceHelper;
import com.android.systemui.shared.recents.ISystemUiProxy;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.AssistDataReceiver;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.RecentsAnimationControllerCompat;
import com.android.systemui.shared.system.RecentsAnimationListener;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Touch consumer for handling events originating from an activity other than Launcher
 */
public class OtherActivityTouchConsumer extends ContextWrapper implements TouchConsumer {
    private static final String TAG = "ActivityTouchConsumer";

    private static final long LAUNCHER_DRAW_TIMEOUT_MS = 150;

    private final RunningTaskInfo mRunningTask;
    private final RecentsModel mRecentsModel;
    private final Intent mHomeIntent;
    private final ISystemUiProxy mISystemUiProxy;
    private final MainThreadExecutor mMainThreadExecutor;

    private final PointF mDownPos = new PointF();
    private final PointF mLastPos = new PointF();
    private int mActivePointerId = INVALID_POINTER_ID;
    private boolean mTouchThresholdCrossed;
    private int mTouchSlop;
    private float mStartDisplacement;
    private BaseSwipeInteractionHandler mInteractionHandler;
    private int mDisplayRotation;
    private Rect mStableInsets = new Rect();

    private VelocityTracker mVelocityTracker;

    public OtherActivityTouchConsumer(Context base, RunningTaskInfo runningTaskInfo,
            RecentsModel recentsModel, Intent homeIntent, ISystemUiProxy systemUiProxy,
            MainThreadExecutor mainThreadExecutor) {
        super(base);
        mRunningTask = runningTaskInfo;
        mRecentsModel = recentsModel;
        mHomeIntent = homeIntent;
        mVelocityTracker = VelocityTracker.obtain();
        mISystemUiProxy = systemUiProxy;
        mMainThreadExecutor = mainThreadExecutor;
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
                mTouchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
                mTouchThresholdCrossed = false;

                // Start the window animation on down to give more time for launcher to draw
                if (!isUsingScreenShot()) {
                    startTouchTrackingForWindowAnimation();
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

                float displacement = ev.getY(pointerIndex) - mDownPos.y;
                if (isNavBarOnRight()) {
                    displacement = ev.getX(pointerIndex) - mDownPos.x;
                } else if (isNavBarOnLeft()) {
                    displacement = mDownPos.x - ev.getX(pointerIndex);
                }
                if (!mTouchThresholdCrossed) {
                    mTouchThresholdCrossed = Math.abs(displacement) >= mTouchSlop;
                    if (mTouchThresholdCrossed) {
                        mStartDisplacement = Math.signum(displacement) * mTouchSlop;

                        if (isUsingScreenShot()) {
                            startTouchTrackingForScreenshotAnimation();
                        }

                        notifyGestureStarted();
                    }
                } else {
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
        // Notify the handler that the gesture has actually started
        mInteractionHandler.onGestureStarted();

        // Notify the system that we have started tracking the event
        if (mISystemUiProxy != null) {
            executeSafely(mISystemUiProxy::onRecentsAnimationStarted);
        }
    }

    private boolean isNavBarOnRight() {
        return mDisplayRotation == Surface.ROTATION_90 && mStableInsets.right > 0;
    }

    private boolean isNavBarOnLeft() {
        return mDisplayRotation == Surface.ROTATION_270 && mStableInsets.left > 0;
    }

    private boolean isUsingScreenShot() {
        return Utilities.getPrefs(this).getBoolean("pref_use_screenshot_animation", true);
    }

    /**
     * Called when the gesture has started.
     */
    private void startTouchTrackingForScreenshotAnimation() {
        // Create the shared handler
        final NavBarSwipeInteractionHandler handler =
                new NavBarSwipeInteractionHandler(mRunningTask, this, INTERACTION_NORMAL);

        TraceHelper.partitionSection("TouchInt", "Thershold crossed ");

        // Start the recents activity on a background thread
        BackgroundExecutor.get().submit(() -> {
            // Get the snap shot before
            handler.setTaskSnapshot(getCurrentTaskSnapshot());

            // Start the launcher activity with our custom handler
            Intent homeIntent = handler.addToIntent(new Intent(mHomeIntent));
            startActivity(homeIntent, ActivityOptions.makeCustomAnimation(this, 0, 0).toBundle());
            TraceHelper.partitionSection("TouchInt", "Home started");
        });

        // Preload the plan
        mRecentsModel.loadTasks(mRunningTask.id, null);
        mInteractionHandler = handler;
        mInteractionHandler.setGestureEndCallback(this::onFinish);
    }

    private Bitmap getCurrentTaskSnapshot() {
        TraceHelper.beginSection("TaskSnapshot");
        // TODO: We are using some hardcoded layers for now, to best approximate the activity layers
        Point displaySize = new Point();
        Display display = getSystemService(WindowManager.class).getDefaultDisplay();
        display.getRealSize(displaySize);
        int rotation = display.getRotation();
        // The rotation is backwards in landscape, so flip it.
        if (rotation == Surface.ROTATION_270) {
            rotation = Surface.ROTATION_90;
        } else if (rotation == Surface.ROTATION_90) {
            rotation = Surface.ROTATION_270;
        }
        try {
            return mISystemUiProxy.screenshot(new Rect(), displaySize.x, displaySize.y, 0, 100000,
                    false, rotation).toBitmap();
        } catch (Exception e) {
            Log.e(TAG, "Error capturing snapshot", e);

            // Return a dummy bitmap
            Bitmap bitmap = Bitmap.createBitmap(displaySize.x, displaySize.y, Config.RGB_565);
            bitmap.eraseColor(Color.WHITE);
            return bitmap;
        } finally {
            TraceHelper.endSection("TaskSnapshot");
        }
    }

    private void startTouchTrackingForWindowAnimation() {
        // Create the shared handler
        final WindowTransformSwipeHandler handler =
                new WindowTransformSwipeHandler(mRunningTask, this);

        // Preload the plan
        mRecentsModel.loadTasks(mRunningTask.id, null);
        mInteractionHandler = handler;
        handler.setGestureEndCallback(this::onFinish);

        CountDownLatch drawWaitLock = new CountDownLatch(1);
        handler.setLauncherOnDrawCallback(() -> {
            drawWaitLock.countDown();
            if (handler == mInteractionHandler) {
                switchToMainChoreographer();
            }
        });
        handler.initWhenReady(mMainThreadExecutor);

        Runnable startActivity = () -> ActivityManagerWrapper.getInstance()
                .startRecentsActivity(mHomeIntent,
                new AssistDataReceiver() {
                    @Override
                    public void onHandleAssistData(Bundle bundle) {
                        // Pass to AIAI
                    }
                },
                new RecentsAnimationListener() {
                    public void onAnimationStart(
                            RecentsAnimationControllerCompat controller,
                            RemoteAnimationTargetCompat[] apps, Rect homeContentInsets,
                            Rect minimizedHomeBounds) {
                        if (mInteractionHandler == handler) {
                            handler.setRecentsAnimation(controller, apps, homeContentInsets,
                                    minimizedHomeBounds);
                        } else {
                            controller.finish(false /* toHome */);
                        }
                    }

                    public void onAnimationCanceled() {
                        if (mInteractionHandler == handler) {
                            handler.setRecentsAnimation(null, null, null, null);
                        }
                    }
                }, null, null);

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

    /**
     * Called when the gesture has ended. Does not correlate to the completion of the interaction as
     * the animation can still be running.
     */
    private void finishTouchTracking() {
        if (mTouchThresholdCrossed) {
            mVelocityTracker.computeCurrentVelocity(1000,
                    ViewConfiguration.get(this).getScaledMaximumFlingVelocity());

            float velocity = isNavBarOnRight() ? mVelocityTracker.getXVelocity(mActivePointerId)
                    : isNavBarOnLeft() ? -mVelocityTracker.getXVelocity(mActivePointerId)
                            : mVelocityTracker.getYVelocity(mActivePointerId);
            mInteractionHandler.onGestureEnded(velocity);
        } else if (!isUsingScreenShot()) {
            // Since we start touch tracking on DOWN, we may reach this state without actually
            // starting the gesture. In that case, just cleanup immediately.
            reset();

            // Also clean up in case the system has handled the UP and canceled the animation before
            // we had a chance to start the recents animation. In such a case, we will not receive
            ActivityManagerWrapper.getInstance().cancelRecentsAnimation();
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;

        onTouchTrackingComplete();
    }

    @Override
    public void reset() {
        // Clean up the old interaction handler
        if (mInteractionHandler != null) {
            final BaseSwipeInteractionHandler handler = mInteractionHandler;
            mMainThreadExecutor.execute(handler::reset);
            mInteractionHandler = null;
        }
    }

    @Override
    public void updateTouchTracking(int interactionType) {
        notifyGestureStarted();

        mMainThreadExecutor.execute(() -> {
            if (mInteractionHandler != null) {
                mInteractionHandler.updateInteractionType(interactionType);
            }
        });
    }

    @Override
    public boolean shouldUseBackgroundConsumer() {
        return !isUsingScreenShot();
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

    private void onFinish() {
        mInteractionHandler = null;
    }

    public void onTouchTrackingComplete() { }

    public void switchToMainChoreographer() { }

    @Override
    public void preProcessMotionEvent(MotionEvent ev) {
        if (mVelocityTracker != null) {
           mVelocityTracker.addMovement(ev);
           if (ev.getActionMasked() == ACTION_POINTER_UP) {
               mVelocityTracker.clear();
           }
        }
    }
}
