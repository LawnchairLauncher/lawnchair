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
import static com.android.launcher3.taskbar.navbutton.SetupNavLayoutterKt.GLIF_EXPRESSIVE_LIGHT_THEME;
import static com.android.launcher3.taskbar.navbutton.SetupNavLayoutterKt.GLIF_EXPRESSIVE_THEME;
import static com.android.launcher3.taskbar.navbutton.SetupNavLayoutterKt.SUW_THEME_SYSTEM_PROPERTY;
import static com.android.launcher3.touch.BaseSwipeDetector.calculateDuration;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.DIRECTION_POSITIVE;
import static com.android.launcher3.touch.SingleAxisSwipeDetector.VERTICAL;
import static com.android.quickstep.MultiStateCallback.DEBUG_STATES;
import static com.android.quickstep.OverviewComponentObserver.startHomeIntentSafely;
import static com.android.quickstep.util.ActiveGestureLog.INTENT_EXTRA_LOG_TRACE_ID;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.os.SystemProperties;
import android.view.MotionEvent;
import android.view.RemoteAnimationTarget;
import android.window.TransitionInfo;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Flags;
import com.android.launcher3.R;
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
import com.android.quickstep.util.ActiveGestureLog;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransaction.SurfaceProperties;
import com.android.systemui.shared.recents.model.ThumbnailData;
import com.android.systemui.shared.system.InputMonitorCompat;

import java.util.HashMap;

/**
 * Input consumer which delegates the swipe-progress handling
 */
public class ProgressDelegateInputConsumer implements InputConsumer,
        RecentsAnimationCallbacks.RecentsAnimationListener,
        SingleAxisSwipeDetector.Listener {
    private static final String TAG = "ProgressDelegateInputConsumer";

    private static final float SWIPE_DISTANCE_THRESHOLD = 0.2f;
    private static final float AUTO_END_SWIPE_DISTANCE_THRESHOLD = 0.5f;

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

    private boolean mIsNewExpressiveThemeAnimation = false;
    private RemoteAnimationTarget[] mRemoteAnimationTargets;
    private final float mBaseReleaseVelocity;
    private boolean mAutoEndDrag = false;

    public ProgressDelegateInputConsumer(
            Context context,
            TaskAnimationManager taskAnimationManager,
            GestureState gestureState,
            InputMonitorCompat inputMonitorCompat,
            AnimatedFloat progress) {
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

        mBaseReleaseVelocity = mContext.getResources()
                .getDimensionPixelSize(R.dimen.base_swift_detector_fling_release_velocity);

        String SUWTheme = SystemProperties.get(SUW_THEME_SYSTEM_PROPERTY, "");
        mIsNewExpressiveThemeAnimation = (SUWTheme.equals(GLIF_EXPRESSIVE_THEME)
                || SUWTheme.equals(GLIF_EXPRESSIVE_LIGHT_THEME))
                && Flags.enableNewAllSetAnimation();
    }

    @Override
    public int getType() {
        return TYPE_PROGRESS_DELEGATE;
    }

    @Override
    public int getDisplayId() {
        return mGestureState.getDisplayId();
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
        if (mAutoEndDrag) {
            return true;
        }
        if (mDisplaySize.y > 0) {
            float progress = Math.min(0, displacement) / -mDisplaySize.y;
            mProgress.updateValue(progress);

            if (mIsNewExpressiveThemeAnimation) {
                setClosingTargetAlpha(progress);

                if (progress >= AUTO_END_SWIPE_DISTANCE_THRESHOLD) {
                    mAutoEndDrag = true;
                    onDragEnd(mBaseReleaseVelocity, true);
                }
            }
        }
        return true;
    }

    /** Fades out the closing window during the swipe gesture. */
    private void setClosingTargetAlpha(float progress) {
        if (mIsNewExpressiveThemeAnimation && mRemoteAnimationTargets != null) {
            for (RemoteAnimationTarget t : mRemoteAnimationTargets) {
                if (t.mode == RemoteAnimationTarget.MODE_CLOSING) {
                    SurfaceTransaction transaction = new SurfaceTransaction();
                    SurfaceProperties builder = transaction.forSurface(t.leash);
                    float alphaProgress = Interpolators.clampToProgress(progress, 0.6f, 1f);
                    builder.setAlpha(1f - alphaProgress);
                    transaction.getTransaction().apply();
                }
            }
        }
    }

    @Override
    public void onDragEnd(float velocity) {
        if (mAutoEndDrag) {
            return;
        }
        final boolean willExit;
        if (mSwipeDetector.isFling(velocity)) {
            willExit = velocity < 0;
        } else {
            willExit = mProgress.value > (mIsNewExpressiveThemeAnimation
                    ? AUTO_END_SWIPE_DISTANCE_THRESHOLD
                    : SWIPE_DISTANCE_THRESHOLD);
        }
        onDragEnd(velocity, willExit);
    }

    private void onDragEnd(float velocity, boolean willExit) {
        float endValue = willExit ? 1 : 0;
        long duration = calculateDuration(velocity, endValue - mProgress.value);
        mFlingEndsOnHome = willExit;

        ObjectAnimator anim = mProgress.animateToValue(endValue);
        anim.setDuration(duration).setInterpolator(scrollInterpolatorForVelocity(velocity));
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                setClosingTargetAlpha((float) valueAnimator.getAnimatedValue());
            }
        });
        anim.addListener(AnimatorListeners.forSuccessCallback(
                () -> mStateCallback.setState(STATE_FLING_FINISHED)));
        anim.start();
    }

    private void onFlingFinished() {
        boolean endToHome = mFlingEndsOnHome == null ? true : mFlingEndsOnHome;
        if (mRecentsAnimationController != null) {
            mRecentsAnimationController.finishController(
                    /* toHome= */ endToHome,
                    /* callback= */ null,
                    /* sendUserLeaveHint= */ false,
                    /* reason= */ new ActiveGestureLog.CompoundString(
                            "ProgressDelegateInputConsumer.onFlingFinished"));
        } else if (endToHome) {
            startHomeIntentSafely(mContext, null, TAG, getDisplayId());
        }
        reset();
    }

    @Override
    public void onRecentsAnimationStart(RecentsAnimationController controller,
            RecentsAnimationTargets targets, TransitionInfo transitionInfo) {
        mRemoteAnimationTargets = targets.unfilteredApps;
        mRecentsAnimationController = controller;
        mStateCallback.setState(STATE_TARGET_RECEIVED);
    }

    @Override
    public void onRecentsAnimationCanceled(HashMap<Integer, ThumbnailData> thumbnailDatas) {
        reset();
    }

    private void reset() {
        mRemoteAnimationTargets = null;
        mRecentsAnimationController = null;
        mAutoEndDrag = false;
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
