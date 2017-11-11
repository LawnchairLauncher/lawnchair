/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.animation.RectEvaluator;
import android.annotation.TargetApi;
import android.app.ActivityManager.RunningTaskInfo;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.UserHandle;
import android.support.annotation.BinderThread;
import android.support.annotation.UiThread;
import android.util.FloatProperty;
import android.view.Choreographer;
import android.view.Choreographer.FrameCallback;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.states.InternalStateHandler;
import com.android.launcher3.uioverrides.OverviewState;
import com.android.systemui.shared.recents.model.RecentsTaskLoadPlan;
import com.android.systemui.shared.recents.model.Task.TaskKey;
import com.android.systemui.shared.system.ActivityManagerWrapper;
import com.android.systemui.shared.system.BackgroundExecutor;
import com.android.systemui.shared.system.WindowManagerWrapper;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

@TargetApi(Build.VERSION_CODES.O)
public class NavBarSwipeInteractionHandler extends InternalStateHandler implements FrameCallback {

    private static FloatProperty<NavBarSwipeInteractionHandler> SHIFT =
            new FloatProperty<NavBarSwipeInteractionHandler>("currentShift") {
        @Override
        public void setValue(NavBarSwipeInteractionHandler handler, float v) {
            handler.setShift(v);
        }

        @Override
        public Float get(NavBarSwipeInteractionHandler handler) {
            return handler.mCurrentShift;
        }
    };

    // The following constants need to be scaled based on density. The scaled versions will be
    // assigned to the corresponding member variables below.
    private static final int FLING_THRESHOLD_VELOCITY = 500;
    private static final int MIN_FLING_VELOCITY = 250;

    private static final float MIN_PROGRESS_FOR_OVERVIEW = 0.5f;

    private final Rect mStableInsets = new Rect();
    private final Rect mSourceRect = new Rect();
    private final Rect mTargetRect = new Rect();
    private final Rect mCurrentRect = new Rect();
    private final RectEvaluator mRectEvaluator = new RectEvaluator(mCurrentRect);

    private final Bitmap mTaskSnapshot;
    private final int mRunningTaskId;
    private Future<RecentsTaskLoadPlan> mFutureLoadPlan;

    private Launcher mLauncher;
    private Choreographer mChoreographer;
    private SnapshotDragView mDragView;
    private RecentsView mRecentsView;
    private Hotseat mHotseat;

    private float mStartDelta;
    private float mLastDelta;

    // Shift in the range of [0, 1].
    // 0 => preview snapShot is completely visible, and hotseat is completely translated down
    // 1 => preview snapShot is completely aligned with the recents view and hotseat is completely
    // visible.
    private float mCurrentShift;

    // These are updated on the binder thread, and eventually picked up on doFrame
    private float mCurrentDisplacement;
    private boolean mTouchEnded = false;
    private float mEndVelocity;

    NavBarSwipeInteractionHandler(Bitmap taskSnapShot, RunningTaskInfo runningTaskInfo) {
        mTaskSnapshot = taskSnapShot;
        mRunningTaskId = runningTaskInfo.id;
        WindowManagerWrapper.getInstance().getStableInsets(mStableInsets);
    }

    @Override
    public void onLauncherResume() {
        mStartDelta = mCurrentDisplacement;
        mLastDelta = mStartDelta;
        mChoreographer = Choreographer.getInstance();

        scheduleNextFrame();
    }

    @Override
    public void onCreate(Launcher launcher) {
        mLauncher = launcher;
        mDragView = new SnapshotDragView(mLauncher, mTaskSnapshot);
        mLauncher.getDragLayer().addView(mDragView);
        mDragView.setPivotX(0);
        mDragView.setPivotY(0);
        mRecentsView = mLauncher.getOverviewPanel();
        mHotseat = mLauncher.getHotseat();

        // Optimization
        mLauncher.getAppsView().setVisibility(View.GONE);

        // Launch overview
        mRecentsView.update(consumeLastLoadPlan());
        mLauncher.getStateManager().goToState(LauncherState.OVERVIEW, false /* animate */);
    }

    @Override
    public void onNewIntent(Launcher launcher, boolean alreadyOnHome) {
        mLauncher = launcher;
        mDragView = new SnapshotDragView(mLauncher, mTaskSnapshot);
        mLauncher.getDragLayer().addView(mDragView);
        mDragView.setPivotX(0);
        mDragView.setPivotY(0);
        mRecentsView = mLauncher.getOverviewPanel();
        mHotseat = mLauncher.getHotseat();

        // Optimization
        mLauncher.getAppsView().setVisibility(View.GONE);

        // Launch overview, animate if already on home
        mRecentsView.update(consumeLastLoadPlan());
        mLauncher.getStateManager().goToState(LauncherState.OVERVIEW, alreadyOnHome);
    }

    @BinderThread
    public void updateDisplacement(float displacement) {
        mCurrentDisplacement = displacement;
    }

    @BinderThread
    public void endTouch(float endVelocity) {
        mTouchEnded = true;
        mEndVelocity = endVelocity;
    }

    @UiThread
    private void scheduleNextFrame() {
        if (!mTouchEnded) {
            mChoreographer.postFrameCallback(this);
        } else {
            animateToFinalShift();
        }
    }

    @Override
    public void doFrame(long l) {
        mLastDelta = mCurrentDisplacement;

        float translation = Utilities.boundToRange(mStartDelta - mLastDelta, 0,
                mHotseat.getHeight());
        int hotseatHeight = mHotseat.getHeight();
        float shift = hotseatHeight == 0 ? 0 : translation / hotseatHeight;
        setShift(shift);
        scheduleNextFrame();
    }

    @UiThread
    private void setShift(float shift) {
        if (mTargetRect.isEmpty()) {
            DragLayer dl = mLauncher.getDragLayer();

            // Init target rect.
            View targetView = ((ViewGroup) mRecentsView.getChildAt(0)).getChildAt(0);
            dl.getViewRectRelativeToSelf(targetView, mTargetRect);
            mTargetRect.right = mTargetRect.left + mTargetRect.width();
            mTargetRect.bottom = mTargetRect.top + mTargetRect.height();
            mSourceRect.set(0, 0, dl.getWidth(), dl.getHeight());
        }

        if (!mSourceRect.isEmpty()) {
            mCurrentShift = shift;
            int hotseatHeight = mHotseat.getHeight();
            mHotseat.setTranslationY((1 - shift) * hotseatHeight);

            mRectEvaluator.evaluate(shift, mSourceRect, mTargetRect);

            float scale = (float) mCurrentRect.width() / mSourceRect.width();
            mDragView.setTranslationX(mCurrentRect.left - mStableInsets.left * scale * shift);
            mDragView.setTranslationY(mCurrentRect.top - mStableInsets.top * scale * shift);
            mDragView.setScaleX(scale);
            mDragView.setScaleY(scale);
            mDragView.getViewBounds().setClipTop((int) (mStableInsets.top * shift));
            mDragView.getViewBounds().setClipBottom((int) (mStableInsets.bottom * shift));
        }
    }

    void setLastLoadPlan(Future<RecentsTaskLoadPlan> futureLoadPlan) {
        if (mFutureLoadPlan != null) {
            mFutureLoadPlan.cancel(true);
        }
        mFutureLoadPlan = futureLoadPlan;
    }

    private RecentsTaskLoadPlan consumeLastLoadPlan() {
        try {
            if (mFutureLoadPlan != null) {
                return mFutureLoadPlan.get();
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        } finally {
            mFutureLoadPlan = null;
        }
        return null;
    }

    @UiThread
    private void animateToFinalShift() {
        float flingThreshold = Utilities.pxFromDp(FLING_THRESHOLD_VELOCITY,
                    mLauncher.getResources().getDisplayMetrics());
        boolean isFling = Math.abs(mEndVelocity) > flingThreshold;

        long duration = 200;
        final float endShift;
        if (!isFling) {
            endShift = mCurrentShift >= MIN_PROGRESS_FOR_OVERVIEW ? 1 : 0;
        } else {
            endShift = mEndVelocity < 0 ? 1 : 0;
            float minFlingVelocity = Utilities.pxFromDp(MIN_FLING_VELOCITY,
                    mLauncher.getResources().getDisplayMetrics());
            if (Math.abs(mEndVelocity) > minFlingVelocity) {
                float distanceToTravel = (endShift - mCurrentShift) * mHotseat.getHeight();

                // we want the page's snap velocity to approximately match the velocity at
                // which the user flings, so we scale the duration by a value near to the
                // derivative of the scroll interpolator at zero, ie. 5. We use 4 to make
                // it a little slower.
                duration = 4 * Math.round(1000 * Math.abs(distanceToTravel / mEndVelocity));
            }
        }

        ObjectAnimator anim = ObjectAnimator.ofFloat(this, SHIFT, endShift)
                .setDuration(duration);
        anim.setInterpolator(Interpolators.SCROLL);
        anim.addListener(new AnimationSuccessListener() {
            @Override
            public void onAnimationSuccess(Animator animator) {
                if (Float.compare(mCurrentShift, 0) == 0) {
                    resumeLastTask();
                } else {
                    mDragView.close(false);
                }
            }
        });
        anim.start();
    }

    @UiThread
    private void resumeLastTask() {
        // TODO: These should be done as part of ActivityOptions#OnAnimationStarted
        mHotseat.setTranslationY(0);
        mLauncher.setOnResumeCallback(() -> mDragView.close(false));

        // TODO: For now, assume that the task stack will have loaded in the bg, will update
        // the lib api later for direct call
        mRecentsView.launchTaskWithId(mRunningTaskId);
    }
}
