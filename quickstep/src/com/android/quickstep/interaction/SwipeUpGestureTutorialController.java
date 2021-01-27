/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.interaction;

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.util.DefaultDisplay.getSingleFrameMs;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.BaseSwipeUpHandlerV2.MAX_SWIPE_DURATION;
import static com.android.quickstep.interaction.TutorialController.TutorialType.HOME_NAVIGATION_COMPLETE;
import static com.android.quickstep.interaction.TutorialController.TutorialType.OVERVIEW_NAVIGATION_COMPLETE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Insets;
import android.graphics.Outline;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.WindowInsets;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.GestureState;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SwipeUpAnimationLogic;
import com.android.quickstep.SwipeUpAnimationLogic.RunningWindowAnim;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.TransformParams;
import com.android.systemui.shared.system.QuickStepContract;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

@TargetApi(Build.VERSION_CODES.R)
abstract class SwipeUpGestureTutorialController extends TutorialController {
    private final ViewSwipeUpAnimation mViewSwipeUpAnimation;
    private float mFakeTaskViewRadius;
    private Rect mFakeTaskViewRect = new Rect();
    private RunningWindowAnim mRunningWindowAnim;

    SwipeUpGestureTutorialController(TutorialFragment tutorialFragment, TutorialType tutorialType) {
        super(tutorialFragment, tutorialType);
        RecentsAnimationDeviceState deviceState = new RecentsAnimationDeviceState(mContext);
        OverviewComponentObserver observer = new OverviewComponentObserver(mContext, deviceState);
        mViewSwipeUpAnimation = new ViewSwipeUpAnimation(mContext, deviceState,
                new GestureState(observer, -1));
        observer.onDestroy();
        deviceState.destroy();

        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(mContext)
                .getDeviceProfile(mContext)
                .copy(mContext);
        Insets insets = mContext.getSystemService(WindowManager.class)
                .getCurrentWindowMetrics()
                .getWindowInsets()
                .getInsets(WindowInsets.Type.systemBars());
        dp.updateInsets(new Rect(insets.left, insets.top, insets.right, insets.bottom));
        mViewSwipeUpAnimation.initDp(dp);

        mFakeTaskViewRadius = QuickStepContract.getWindowCornerRadius(mContext.getResources());
        mFakeTaskView.setClipToOutline(true);
        mFakeTaskView.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(mFakeTaskViewRect, mFakeTaskViewRadius);
            }
        });
    }

    private void cancelRunningAnimation() {
        if (mRunningWindowAnim != null) {
            mRunningWindowAnim.cancel();
        }
        mRunningWindowAnim = null;
    }

    /** Fades the task view, optionally after animating to a fake Overview. */
    void fadeOutFakeTaskView(boolean toOverviewFirst, @Nullable Runnable onEndRunnable) {
        hideFeedback();
        hideHandCoachingAnimation();
        cancelRunningAnimation();
        PendingAnimation anim = new PendingAnimation(300);
        AnimatorListenerAdapter resetTaskView = new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation, boolean isReverse) {
                mFakeIconView.setVisibility(View.INVISIBLE);
                mFakeTaskView.setVisibility(View.INVISIBLE);
                mFakeTaskView.setAlpha(1);
                mRunningWindowAnim = null;
            }
        };
        if (toOverviewFirst) {
            anim.setFloat(mViewSwipeUpAnimation.getCurrentShift(), AnimatedFloat.VALUE, 1, ACCEL);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation, boolean isReverse) {
                    PendingAnimation fadeAnim = new PendingAnimation(300);
                    fadeAnim.setViewAlpha(mFakeTaskView, 0, ACCEL);
                    fadeAnim.addListener(resetTaskView);
                    AnimatorSet animset = fadeAnim.buildAnim();
                    animset.setStartDelay(100);
                    animset.start();
                    mRunningWindowAnim = RunningWindowAnim.wrap(animset);
                }
            });
        } else {
            anim.setViewAlpha(mFakeTaskView, 0, ACCEL);
            anim.setViewAlpha(mFakeIconView, 0, ACCEL);
            anim.addListener(resetTaskView);
        }
        if (onEndRunnable != null) {
            anim.addListener(AnimationSuccessListener.forRunnable(onEndRunnable));
        }
        AnimatorSet animset = anim.buildAnim();
        animset.start();
        mRunningWindowAnim = RunningWindowAnim.wrap(animset);
    }

    void animateFakeTaskViewHome(PointF finalVelocity, @Nullable Runnable onEndRunnable) {
        hideFeedback();
        hideHandCoachingAnimation();
        cancelRunningAnimation();
        RectFSpringAnim rectAnim =
                mViewSwipeUpAnimation.handleSwipeUpToHome(finalVelocity);
        // After home animation finishes, fade out and run onEndRunnable.
        rectAnim.addAnimatorListener(AnimationSuccessListener.forRunnable(
                () -> fadeOutFakeTaskView(false, onEndRunnable)));
        mRunningWindowAnim = RunningWindowAnim.wrap(rectAnim);
    }

    @Override
    public void setNavBarGestureProgress(@Nullable Float displacement) {
        if (displacement == null || mTutorialType == HOME_NAVIGATION_COMPLETE
                || mTutorialType == OVERVIEW_NAVIGATION_COMPLETE) {
            mFakeTaskView.setVisibility(View.INVISIBLE);
        } else {
            mFakeTaskView.setVisibility(View.VISIBLE);
            if (mRunningWindowAnim == null) {
                mViewSwipeUpAnimation.updateDisplacement(displacement);
            }
        }
    }

    class ViewSwipeUpAnimation extends SwipeUpAnimationLogic {

        ViewSwipeUpAnimation(Context context, RecentsAnimationDeviceState deviceState,
                             GestureState gestureState) {
            super(context, deviceState, gestureState, new FakeTransformParams());
        }

        void initDp(DeviceProfile dp) {
            initTransitionEndpoints(dp);
            mTaskViewSimulator.setPreviewBounds(
                    new Rect(0, 0, dp.widthPx, dp.heightPx), dp.getInsets());
        }

        @Override
        public void updateFinalShift() {
            mWindowTransitionController.setProgress(mCurrentShift.value, mDragLengthFactor);
            mTaskViewSimulator.apply(mTransformParams);
        }

        AnimatedFloat getCurrentShift() {
            return mCurrentShift;
        }

        RectFSpringAnim handleSwipeUpToHome(PointF velocity) {
            PointF velocityPxPerMs = new PointF(velocity.x, velocity.y);
            float currentShift = mCurrentShift.value;
            final float startShift = Utilities.boundToRange(currentShift - velocityPxPerMs.y
                    * getSingleFrameMs(mContext) / mTransitionDragLength, 0, mDragLengthFactor);
            float distanceToTravel = (1 - currentShift) * mTransitionDragLength;

            // we want the page's snap velocity to approximately match the velocity at
            // which the user flings, so we scale the duration by a value near to the
            // derivative of the scroll interpolator at zero, ie. 2.
            long baseDuration = Math.round(Math.abs(distanceToTravel / velocityPxPerMs.y));
            long duration = Math.min(MAX_SWIPE_DURATION, 2 * baseDuration);
            HomeAnimationFactory homeAnimFactory = new HomeAnimationFactory() {
                @Override
                public AnimatorPlaybackController createActivityAnimationToHome() {
                    return AnimatorPlaybackController.wrap(new AnimatorSet(), duration);
                }

                @NonNull
                @Override
                public RectF getWindowTargetRect() {
                    int fakeHomeIconSizePx = mDp.allAppsIconSizePx;
                    int fakeHomeIconLeft = (mDp.widthPx - fakeHomeIconSizePx) / 2;
                    int fakeHomeIconTop = mDp.heightPx - (mDp.allAppsCellHeightPx * 3);
                    return new RectF(fakeHomeIconLeft, fakeHomeIconTop,
                            fakeHomeIconLeft + fakeHomeIconSizePx,
                            fakeHomeIconTop + fakeHomeIconSizePx);
                }

                @Override
                public void update(RectF rect, float progress, float radius) {
                    mFakeIconView.setVisibility(View.VISIBLE);
                    mFakeIconView.update(rect, progress,
                            1f - SHAPE_PROGRESS_DURATION /* shapeProgressStart */,
                            radius,
                            false, /* isOpening */
                            mFakeIconView, mDp,
                            false /* isVerticalBarLayout */);
                    mFakeIconView.setAlpha(1);
                    mFakeTaskView.setAlpha(getWindowAlpha(progress));
                }

                @Override
                public void onCancel() {
                    mFakeIconView.setVisibility(View.INVISIBLE);
                }
            };
            RectFSpringAnim windowAnim = createWindowAnimationToHome(startShift, homeAnimFactory);
            windowAnim.start(mContext, velocityPxPerMs);
            return windowAnim;
        }
    }

    private class FakeTransformParams extends TransformParams {

        @Override
        public SurfaceParams[] createSurfaceParams(BuilderProxy proxy) {
            SurfaceParams.Builder builder = new SurfaceParams.Builder((SurfaceControl) null);
            proxy.onBuildTargetParams(builder, null, this);
            return new SurfaceParams[] {builder.build()};
        }

        @Override
        public void applySurfaceParams(SurfaceParams[] params) {
            SurfaceParams p = params[0];
            mFakeTaskView.setAnimationMatrix(p.matrix);
            mFakeTaskViewRect.set(p.windowCrop);
            mFakeTaskViewRadius = p.cornerRadius;
            mFakeTaskView.invalidateOutline();
        }
    }
}
