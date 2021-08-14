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
import static com.android.launcher3.util.DisplayController.getSingleFrameMs;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.AbsSwipeUpHandler.MAX_SWIPE_DURATION;
import static com.android.quickstep.interaction.TutorialController.TutorialType.HOME_NAVIGATION_COMPLETE;
import static com.android.quickstep.interaction.TutorialController.TutorialType.OVERVIEW_NAVIGATION_COMPLETE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.SurfaceControl;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.GestureState;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.SwipeUpAnimationLogic;
import com.android.quickstep.SwipeUpAnimationLogic.RunningWindowAnim;
import com.android.quickstep.util.AppCloseConfig;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.TransformParams;
import com.android.systemui.shared.system.SyncRtSurfaceTransactionApplierCompat.SurfaceParams;

@TargetApi(Build.VERSION_CODES.R)
abstract class SwipeUpGestureTutorialController extends TutorialController {

    private static final int FAKE_PREVIOUS_TASK_MARGIN = Utilities.dpToPx(12);

    final ViewSwipeUpAnimation mTaskViewSwipeUpAnimation;
    private float mFakeTaskViewRadius;
    private Rect mFakeTaskViewRect = new Rect();
    RunningWindowAnim mRunningWindowAnim;
    private boolean mShowTasks = false;
    private boolean mShowPreviousTasks = false;

    private AnimatorListenerAdapter mResetTaskView = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            mFakeHotseatView.setVisibility(View.INVISIBLE);
            mFakeIconView.setVisibility(View.INVISIBLE);
            if (mTutorialFragment.getActivity() != null) {
                DisplayMetrics displayMetrics =
                        mTutorialFragment.getResources().getDisplayMetrics();
                int height = displayMetrics.heightPixels;
                int width = displayMetrics.widthPixels;
                mFakeTaskViewRect.set(0, 0, width, height);
            }
            mFakeTaskViewRadius = 0;
            mFakeTaskView.invalidateOutline();
            mFakeTaskView.setVisibility(View.VISIBLE);
            mFakeTaskView.setAlpha(1);
            mFakePreviousTaskView.setVisibility(View.INVISIBLE);
            mFakePreviousTaskView.setAlpha(1);
            mShowTasks = false;
            mShowPreviousTasks = false;
            mRunningWindowAnim = null;
        }
    };

    SwipeUpGestureTutorialController(TutorialFragment tutorialFragment, TutorialType tutorialType) {
        super(tutorialFragment, tutorialType);
        RecentsAnimationDeviceState deviceState = new RecentsAnimationDeviceState(mContext);
        OverviewComponentObserver observer = new OverviewComponentObserver(mContext, deviceState);
        mTaskViewSwipeUpAnimation = new ViewSwipeUpAnimation(mContext, deviceState,
                new GestureState(observer, -1));
        observer.onDestroy();
        deviceState.destroy();

        DeviceProfile dp = InvariantDeviceProfile.INSTANCE.get(mContext)
                .getDeviceProfile(mContext)
                .copy(mContext);
        mTaskViewSwipeUpAnimation.initDp(dp);

        DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
        int height = displayMetrics.heightPixels;
        int width = displayMetrics.widthPixels;
        mFakeTaskViewRect.set(0, 0, width, height);
        mFakeTaskViewRadius = 0;

        ViewOutlineProvider outlineProvider = new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(mFakeTaskViewRect, mFakeTaskViewRadius);
            }
        };

        mFakeTaskView.setClipToOutline(true);
        mFakeTaskView.setOutlineProvider(outlineProvider);

        mFakePreviousTaskView.setClipToOutline(true);
        mFakePreviousTaskView.setOutlineProvider(outlineProvider);
    }

    private void cancelRunningAnimation() {
        if (mRunningWindowAnim != null) {
            mRunningWindowAnim.cancel();
        }
        mRunningWindowAnim = null;
    }

    /** Fades the task view, optionally after animating to a fake Overview. */
    void fadeOutFakeTaskView(boolean toOverviewFirst, boolean reset,
                             @Nullable Runnable onEndRunnable) {
        hideFeedback(true);
        cancelRunningAnimation();
        PendingAnimation anim = new PendingAnimation(300);
        if (toOverviewFirst) {
            anim.setFloat(mTaskViewSwipeUpAnimation
                    .getCurrentShift(), AnimatedFloat.VALUE, 1, ACCEL);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation, boolean isReverse) {
                    PendingAnimation fadeAnim = new PendingAnimation(300);
                    if (reset) {
                        fadeAnim.setFloat(mTaskViewSwipeUpAnimation
                                .getCurrentShift(), AnimatedFloat.VALUE, 0, ACCEL);
                        fadeAnim.addListener(mResetTaskView);
                    } else {
                        fadeAnim.setViewAlpha(mFakeTaskView, 0, ACCEL);
                        fadeAnim.setViewAlpha(mFakePreviousTaskView, 0, ACCEL);
                    }
                    if (onEndRunnable != null) {
                        fadeAnim.addListener(AnimatorListeners.forSuccessCallback(onEndRunnable));
                    }
                    AnimatorSet animset = fadeAnim.buildAnim();
                    animset.setStartDelay(100);
                    animset.start();
                    mRunningWindowAnim = RunningWindowAnim.wrap(animset);
                }
            });
        } else {
            if (reset) {
                anim.setFloat(mTaskViewSwipeUpAnimation
                        .getCurrentShift(), AnimatedFloat.VALUE, 0, ACCEL);
                anim.addListener(mResetTaskView);
            } else {
                anim.setViewAlpha(mFakeTaskView, 0, ACCEL);
                anim.setViewAlpha(mFakePreviousTaskView, 0, ACCEL);
            }
            if (onEndRunnable != null) {
                anim.addListener(AnimatorListeners.forSuccessCallback(onEndRunnable));
            }
        }
        AnimatorSet animset = anim.buildAnim();
        animset.start();
        mRunningWindowAnim = RunningWindowAnim.wrap(animset);
    }

    void resetFakeTaskView() {
        PendingAnimation anim = new PendingAnimation(300);
        anim.setFloat(mTaskViewSwipeUpAnimation
                .getCurrentShift(), AnimatedFloat.VALUE, 0, ACCEL);
        anim.setViewAlpha(mFakeTaskView, 1, ACCEL);
        anim.addListener(mResetTaskView);
        AnimatorSet animset = anim.buildAnim();
        animset.start();
        mRunningWindowAnim = RunningWindowAnim.wrap(animset);
    }

    void animateFakeTaskViewHome(PointF finalVelocity, @Nullable Runnable onEndRunnable) {
        hideFeedback(true);
        cancelRunningAnimation();
        mFakePreviousTaskView.setVisibility(View.INVISIBLE);
        mFakeHotseatView.setVisibility(View.VISIBLE);
        mShowPreviousTasks = false;
        RectFSpringAnim rectAnim =
                mTaskViewSwipeUpAnimation.handleSwipeUpToHome(finalVelocity);
        // After home animation finishes, fade out and run onEndRunnable.
        PendingAnimation fadeAnim = new PendingAnimation(300);
        fadeAnim.setViewAlpha(mFakeIconView, 0, ACCEL);
        if (onEndRunnable != null) {
            fadeAnim.addListener(AnimatorListeners.forSuccessCallback(onEndRunnable));
        }
        AnimatorSet animset = fadeAnim.buildAnim();
        rectAnim.addAnimatorListener(AnimatorListeners.forSuccessCallback(animset::start));
        mRunningWindowAnim = RunningWindowAnim.wrap(rectAnim);
    }

    @Override
    public void setNavBarGestureProgress(@Nullable Float displacement) {
        if (mGestureCompleted) {
            return;
        }
        if (displacement != null) {
            hideFeedback(true);
        }
        if (mTutorialType == HOME_NAVIGATION_COMPLETE
                || mTutorialType == OVERVIEW_NAVIGATION_COMPLETE) {
            mFakeTaskView.setVisibility(View.INVISIBLE);
            mFakePreviousTaskView.setVisibility(View.INVISIBLE);
        } else {
            mShowTasks = true;
            mFakeTaskView.setVisibility(View.VISIBLE);
            if (mShowPreviousTasks) {
                mFakePreviousTaskView.setVisibility(View.VISIBLE);
            }
            if (mRunningWindowAnim == null && displacement != null) {
                mTaskViewSwipeUpAnimation.updateDisplacement(displacement);
            }
        }
    }

    @Override
    public void onMotionPaused(boolean unused) {
        if (mGestureCompleted) {
            return;
        }
        if (mShowTasks) {
            if (!mShowPreviousTasks) {
                mFakePreviousTaskView.setTranslationX(
                        -(2 * mFakePreviousTaskView.getWidth() + FAKE_PREVIOUS_TASK_MARGIN));
                mFakePreviousTaskView.animate()
                    .setDuration(300)
                    .translationX(-(mFakePreviousTaskView.getWidth() + FAKE_PREVIOUS_TASK_MARGIN))
                    .start();
            }
            mShowPreviousTasks = true;
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
                    int fakeHomeIconSizePx = Utilities.dpToPx(60);
                    int fakeHomeIconLeft = mFakeHotseatView.getLeft();
                    int fakeHomeIconTop = mDp.heightPx - Utilities.dpToPx(216);
                    return new RectF(fakeHomeIconLeft, fakeHomeIconTop,
                            fakeHomeIconLeft + fakeHomeIconSizePx,
                            fakeHomeIconTop + fakeHomeIconSizePx);
                }

                @Override
                public void update(@Nullable AppCloseConfig config, RectF rect, float progress,
                        float radius) {
                    mFakeIconView.setVisibility(View.VISIBLE);
                    mFakeIconView.update(rect, progress,
                            1f - SHAPE_PROGRESS_DURATION /* shapeProgressStart */,
                            radius, 255,
                            false, /* isOpening */
                            mFakeIconView, mDp,
                            false /* isVerticalBarLayout */);
                    mFakeIconView.setAlpha(1);
                    mFakeTaskView.setAlpha(getWindowAlpha(progress));
                    mFakePreviousTaskView.setAlpha(getWindowAlpha(progress));
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
            mFakePreviousTaskView.setAnimationMatrix(p.matrix);
            mFakeTaskViewRect.set(p.windowCrop);
            mFakeTaskViewRadius = p.cornerRadius;
            mFakeTaskView.invalidateOutline();
            mFakePreviousTaskView.invalidateOutline();
        }
    }
}
