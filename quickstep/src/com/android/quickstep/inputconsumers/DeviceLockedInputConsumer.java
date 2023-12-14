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
package com.android.quickstep.inputconsumers;

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_POINTER_DOWN;
import static android.view.MotionEvent.ACTION_UP;

import static com.android.launcher3.Utilities.squaredHypot;
import static com.android.launcher3.util.VelocityUtils.PX_PER_MS;
import static com.android.quickstep.AbsSwipeUpHandler.MIN_PROGRESS_FOR_OVERVIEW;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;
import static com.android.quickstep.TaskAnimationManager.ENABLE_SHELL_TRANSITIONS;
import static com.android.quickstep.util.ActiveGestureLog.INTENT_EXTRA_LOG_TRACE_ID;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.PointF;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.view.VelocityTracker;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.testing.TestLogging;
import com.android.launcher3.testing.shared.TestProtocol;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.GestureState;
import com.android.quickstep.InputConsumer;
import com.android.quickstep.MultiStateCallback;
import com.android.quickstep.RecentsAnimationCallbacks;
import com.android.quickstep.RecentsAnimationController;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RecentsAnimationTargets;
import com.android.quickstep.RemoteAnimationTargets;
import com.android.quickstep.TaskAnimationManager;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.quickstep.util.TransformParams;
import com.android.quickstep.util.TransformParams.BuilderProxy;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.InputMonitorCompat;

import java.util.HashMap;

/**
 * A placeholder input consumer used when the device is still locked, e.g. from secure camera.
 */
public class DeviceLockedInputConsumer implements InputConsumer,
        RecentsAnimationCallbacks.RecentsAnimationListener, BuilderProxy {

    private static final String[] STATE_NAMES = DEBUG_STATES ? new String[2] : null;
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

    private final Context mContext;
    private final RecentsAnimationDeviceState mDeviceState;
    private final TaskAnimationManager mTaskAnimationManager;
    private final GestureState mGestureState;
    private final float mTouchSlopSquared;
    private final InputMonitorCompat mInputMonitorCompat;

    private final PointF mTouchDown = new PointF();
    private final TransformParams mTransformParams;
    private final MultiStateCallback mStateCallback;

    private final Point mDisplaySize;
    private final Matrix mMatrix = new Matrix();
    private final float mMaxTranslationY;

    private VelocityTracker mVelocityTracker;
    private final AnimatedFloat mProgress = new AnimatedFloat(this::applyTransform);

    private boolean mThresholdCrossed = false;
    private boolean mHomeLaunched = false;
    private boolean mCancelWhenRecentsStart = false;
    private boolean mDismissTask = false;

    private RecentsAnimationController mRecentsAnimationController;

    public DeviceLockedInputConsumer(Context context, RecentsAnimationDeviceState deviceState,
            TaskAnimationManager taskAnimationManager, GestureState gestureState,
            InputMonitorCompat inputMonitorCompat) {
        mContext = context;
        mDeviceState = deviceState;
        mTaskAnimationManager = taskAnimationManager;
        mGestureState = gestureState;
        mTouchSlopSquared = mDeviceState.getSquaredTouchSlop();
        mTransformParams = new TransformParams();
        mInputMonitorCompat = inputMonitorCompat;
        mMaxTranslationY = context.getResources().getDimensionPixelSize(
                R.dimen.device_locked_y_offset);

        // Do not use DeviceProfile as the user data might be locked
        mDisplaySize = DisplayController.INSTANCE.get(context).getInfo().currentSize;

        // Init states
        mStateCallback = new MultiStateCallback(STATE_NAMES);
        mStateCallback.runOnceAtState(STATE_TARGET_RECEIVED | STATE_HANDLER_INVALIDATED,
                this::endRemoteAnimation);

        mVelocityTracker = VelocityTracker.obtain();
    }

    @Override
    public int getType() {
        return TYPE_DEVICE_LOCKED;
    }

    @Override
    public void onMotionEvent(MotionEvent ev) {
        if (mVelocityTracker == null) {
            return;
        }
        mVelocityTracker.addMovement(ev);

        float x = ev.getX();
        float y = ev.getY();
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTouchDown.set(x, y);
                break;
            case ACTION_POINTER_DOWN: {
                if (!mThresholdCrossed) {
                    // Cancel interaction in case of multi-touch interaction
                    int ptrIdx = ev.getActionIndex();
                    if (!mDeviceState.getRotationTouchHelper().isInSwipeUpTouchRegion(ev, ptrIdx)) {
                        int action = ev.getAction();
                        ev.setAction(ACTION_CANCEL);
                        finishTouchTracking(ev);
                        ev.setAction(action);
                    }
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                if (!mThresholdCrossed) {
                    if (squaredHypot(x - mTouchDown.x, y - mTouchDown.y) > mTouchSlopSquared) {
                        startRecentsTransition();
                    }
                } else {
                    float dy = Math.max(mTouchDown.y - y, 0);
                    mProgress.updateValue(dy / mDisplaySize.y);
                }
                break;
            }
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                finishTouchTracking(ev);
                break;
        }
    }

    /**
     * Called when the gesture has ended. Does not correlate to the completion of the interaction as
     * the animation can still be running.
     */
    private void finishTouchTracking(MotionEvent ev) {
        if (mThresholdCrossed && ev.getAction() == ACTION_UP) {
            mVelocityTracker.computeCurrentVelocity(PX_PER_MS);

            float velocityY = mVelocityTracker.getYVelocity();
            float flingThreshold = mContext.getResources()
                    .getDimension(R.dimen.quickstep_fling_threshold_speed);

            boolean dismissTask;
            if (Math.abs(velocityY) > flingThreshold) {
                // Is fling
                dismissTask = velocityY < 0;
            } else {
                dismissTask = mProgress.value >= (1 - MIN_PROGRESS_FOR_OVERVIEW);
            }

            // Animate back to fullscreen before finishing
            ObjectAnimator animator = mProgress.animateToValue(mProgress.value, 0);
            animator.setDuration(100);
            animator.setInterpolator(Interpolators.ACCELERATE);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (ENABLE_SHELL_TRANSITIONS) {
                        if (mTaskAnimationManager.getCurrentCallbacks() != null) {
                            if (mRecentsAnimationController != null) {
                                finishRecentsAnimationForShell(dismissTask);
                            } else {
                                // the transition of recents animation hasn't started, wait for it
                                mCancelWhenRecentsStart = true;
                                mDismissTask = dismissTask;
                            }
                        }
                    } else if (dismissTask) {
                        // For now, just start the home intent so user is prompted to
                        // unlock the device.
                        startHomeIntentSafely(mContext, mGestureState.getHomeIntent(), null);
                        mHomeLaunched = true;
                    }
                    mStateCallback.setState(STATE_HANDLER_INVALIDATED);
                }
            });
            RemoteAnimationTargets targets = mTransformParams.getTargetSet();
            if (targets != null) {
                targets.addReleaseCheck(new DeviceLockedReleaseCheck(animator));
            }
            animator.start();
        } else {
            mStateCallback.setState(STATE_HANDLER_INVALIDATED);
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }

    private void startRecentsTransition() {
        mThresholdCrossed = true;
        mHomeLaunched = false;
        TestLogging.recordEvent(TestProtocol.SEQUENCE_PILFER, "pilferPointers");
        mInputMonitorCompat.pilferPointers();

        Intent intent = mGestureState.getHomeIntent()
                .putExtra(INTENT_EXTRA_LOG_TRACE_ID, mGestureState.getGestureId());
        mTaskAnimationManager.startRecentsAnimation(mGestureState, intent, this);
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets) {
        mRecentsAnimationController = controller;
        mTransformParams.setTargetSet(targets);
        applyTransform();
        mStateCallback.setState(STATE_TARGET_RECEIVED);
        if (mCancelWhenRecentsStart) {
            finishRecentsAnimationForShell(mDismissTask);
        }
    }

    @Override
    public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
        mRecentsAnimationController = null;
        mTransformParams.setTargetSet(null);
        mCancelWhenRecentsStart = false;
    }

    private void finishRecentsAnimationForShell(boolean dismissTask) {
        mCancelWhenRecentsStart = false;
        mTaskAnimationManager.finishRunningRecentsAnimation(dismissTask /* toHome */);
        if (dismissTask) {
            mHomeLaunched = true;
        }
    }

    private void endRemoteAnimation() {
        if (mHomeLaunched) {
            ActivityManagerWrapper.getInstance().cancelRecentsAnimation(false);
        } else if (mRecentsAnimationController != null) {
            mRecentsAnimationController.finishController(
                    false /* toRecents */, null /* callback */, false /* sendUserLeaveHint */);
        }
    }

    private void applyTransform() {
        mTransformParams.setProgress(mProgress.value);
        if (mTransformParams.getTargetSet() != null) {
            mTransformParams.applySurfaceParams(mTransformParams.createSurfaceParams(this));
        }
    }

    @Override
    public void onBuildTargetParams(
            SurfaceProperties builder, RemoteAnimationTarget app, TransformParams params) {
        mMatrix.setTranslate(0, mProgress.value * mMaxTranslationY);
        builder.setMatrix(mMatrix);
    }

    @Override
    public void onConsumerAboutToBeSwitched() {
        mStateCallback.setState(STATE_HANDLER_INVALIDATED);
    }

    @Override
    public boolean allowInterceptByParent() {
        return !mThresholdCrossed;
    }

    private static final class DeviceLockedReleaseCheck extends
            RemoteAnimationTargets.ReleaseCheck {

        private DeviceLockedReleaseCheck(Animator animator) {
            setCanRelease(true);

            animator.addListener(new AnimatorListenerAdapter() {

                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    setCanRelease(false);
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    super.onAnimationEnd(animation);
                    setCanRelease(true);
                }
            });
        }
    }
}
