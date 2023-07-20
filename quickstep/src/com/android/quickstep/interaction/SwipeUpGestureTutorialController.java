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

import static android.view.View.INVISIBLE;
import static android.view.View.VISIBLE;

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.launcher3.util.window.RefreshRateTracker.getSingleFrameMs;
import static com.android.launcher3.views.FloatingIconView.SHAPE_PROGRESS_DURATION;
import static com.android.quickstep.AbsSwipeUpHandler.MAX_SWIPE_DURATION;
import static com.android.quickstep.interaction.TutorialController.TutorialType.HOME_NAVIGATION_COMPLETE;
import static com.android.quickstep.interaction.TutorialController.TutorialType.OVERVIEW_NAVIGATION_COMPLETE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Outline;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Build;
import android.view.View;
import android.view.ViewOutlineProvider;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.quickstep.GestureState;
import com.android.quickstep.OverviewComponentObserver;
import com.android.quickstep.RecentsAnimationDeviceState;
import com.android.quickstep.RemoteTargetGluer;
import com.android.quickstep.SwipeUpAnimationLogic;
import com.android.quickstep.SwipeUpAnimationLogic.RunningWindowAnim;
import com.android.quickstep.util.RecordingSurfaceTransaction;
import com.android.quickstep.util.RectFSpringAnim;
import com.android.quickstep.util.SurfaceTransaction;
import com.android.quickstep.util.SurfaceTransaction.MockProperties;
import com.android.quickstep.util.TransformParams;

@TargetApi(Build.VERSION_CODES.R)
abstract class SwipeUpGestureTutorialController extends TutorialController {

    private static final int FAKE_PREVIOUS_TASK_MARGIN = Utilities.dpToPx(24);

    protected static final long TASK_VIEW_END_ANIMATION_DURATION_MILLIS = 300;
    protected static final long TASK_VIEW_FILL_SCREEN_ANIMATION_DELAY_MILLIS = 300;
    private static final long HOME_SWIPE_ANIMATION_DURATION_MILLIS = 625;
    private static final long OVERVIEW_SWIPE_ANIMATION_DURATION_MILLIS = 1000;

    final ViewSwipeUpAnimation mTaskViewSwipeUpAnimation;
    private float mFakeTaskViewRadius;
    private final Rect mFakeTaskViewRect = new Rect();
    RunningWindowAnim mRunningWindowAnim;
    private boolean mShowTasks = false;
    private boolean mShowPreviousTasks = false;

    private final AnimatorListenerAdapter mResetTaskView = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationEnd(Animator animation) {
            resetTaskViews();
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

        int height = mTutorialFragment.getRootView().getFullscreenHeight();
        int width = mTutorialFragment.getRootView().getWidth();
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

    void resetTaskViews() {
        mFakeHotseatView.setVisibility(View.INVISIBLE);
        mFakeIconView.setVisibility(View.INVISIBLE);
        if (FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            mFakeIconView.getBackground().setTint(getFakeTaskViewColor());
        }
        if (mTutorialFragment.getActivity() != null) {
            int height = mTutorialFragment.getRootView().getFullscreenHeight();
            int width = mTutorialFragment.getRootView().getWidth();
            mFakeTaskViewRect.set(0, 0, width, height);
        }
        mFakeTaskViewRadius = 0;
        mFakeTaskView.invalidateOutline();
        mFakeTaskView.setVisibility(View.VISIBLE);
        if (FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            mFakeTaskView.setBackgroundColor(getFakeTaskViewColor());
        }
        mFakeTaskView.setAlpha(1);
        mFakePreviousTaskView.setVisibility(View.INVISIBLE);
        mFakePreviousTaskView.setAlpha(1);
        mFakePreviousTaskView.setToSingleRowLayout(false);
        mShowTasks = false;
        mShowPreviousTasks = false;
        mRunningWindowAnim = null;
    }
    void fadeOutFakeTaskView(boolean toOverviewFirst, @Nullable Runnable onEndRunnable) {
        fadeOutFakeTaskView(
                toOverviewFirst,
                /* animatePreviousTask= */ true,
                /* resetViews= */ true,
                /* updateListener= */ null,
                onEndRunnable);
    }

    /** Fades the task view, optionally after animating to a fake Overview. */
    void fadeOutFakeTaskView(boolean toOverviewFirst,
            boolean animatePreviousTask,
            boolean resetViews,
            @Nullable ValueAnimator.AnimatorUpdateListener updateListener,
            @Nullable Runnable onEndRunnable) {
        cancelRunningAnimation();
        PendingAnimation anim = new PendingAnimation(300);
        if (toOverviewFirst) {
            anim.setFloat(mTaskViewSwipeUpAnimation
                    .getCurrentShift(), AnimatedFloat.VALUE, 1, ACCELERATE);
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation, boolean isReverse) {
                    PendingAnimation fadeAnim =
                            new PendingAnimation(TASK_VIEW_END_ANIMATION_DURATION_MILLIS);
                    fadeAnim.setFloat(mTaskViewSwipeUpAnimation
                            .getCurrentShift(), AnimatedFloat.VALUE, 0, ACCELERATE);
                    if (resetViews) {
                        fadeAnim.addListener(mResetTaskView);
                    }
                    if (onEndRunnable != null) {
                        fadeAnim.addListener(AnimatorListeners.forSuccessCallback(onEndRunnable));
                    }
                    if (updateListener != null) {
                        fadeAnim.addOnFrameListener(updateListener);
                    }
                    AnimatorSet animset = fadeAnim.buildAnim();

                    if (animatePreviousTask && mTutorialFragment.isLargeScreen()) {
                        animset.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                super.onAnimationStart(animation);
                                Animator multiRowAnimation =
                                        mFakePreviousTaskView.createAnimationToMultiRowLayout();

                                if (multiRowAnimation != null) {
                                    multiRowAnimation.setDuration(
                                            TASK_VIEW_END_ANIMATION_DURATION_MILLIS).start();
                                }
                            }
                        });
                    }

                    animset.setStartDelay(100);
                    animset.start();
                    mRunningWindowAnim = RunningWindowAnim.wrap(animset);
                }
            });
        } else {
            anim.setFloat(mTaskViewSwipeUpAnimation
                    .getCurrentShift(), AnimatedFloat.VALUE, 0, ACCELERATE);
            if (resetViews) {
                anim.addListener(mResetTaskView);
            }
            if (onEndRunnable != null) {
                anim.addListener(AnimatorListeners.forSuccessCallback(onEndRunnable));
            }
        }
        AnimatorSet animset = anim.buildAnim();
        hideFakeTaskbar(/* animateToHotseat= */ false);
        animset.start();
        mRunningWindowAnim = RunningWindowAnim.wrap(animset);
    }

    void resetFakeTaskViewFromOverview() {
        resetFakeTaskView(false, false);
    }

    void resetFakeTaskView(boolean animateFromHome) {
        resetFakeTaskView(animateFromHome, true);
    }

    void resetFakeTaskView(boolean animateFromHome, boolean animateTaskbar) {
        mFakeTaskView.setVisibility(View.VISIBLE);
        PendingAnimation anim = new PendingAnimation(300);
        anim.setFloat(mTaskViewSwipeUpAnimation
                .getCurrentShift(), AnimatedFloat.VALUE, 0, ACCELERATE);
        anim.setViewAlpha(mFakeTaskView, 1, ACCELERATE);
        anim.addListener(mResetTaskView);
        AnimatorSet animset = anim.buildAnim();
        if (animateTaskbar) {
            showFakeTaskbar(animateFromHome);
        }
        animset.start();
        mRunningWindowAnim = RunningWindowAnim.wrap(animset);
    }

    void animateFakeTaskViewHome(PointF finalVelocity, @Nullable Runnable onEndRunnable) {
        cancelRunningAnimation();
        hideFakeTaskbar(/* animateToHotseat= */ true);
        mFakePreviousTaskView.setVisibility(View.INVISIBLE);
        mFakeHotseatView.setVisibility(View.VISIBLE);
        mShowPreviousTasks = false;
        RectFSpringAnim rectAnim =
                mTaskViewSwipeUpAnimation.handleSwipeUpToHome(finalVelocity);
        // After home animation finishes, fade out and run onEndRunnable.
        PendingAnimation fadeAnim = new PendingAnimation(300);
        fadeAnim.setViewAlpha(mFakeIconView, 0, ACCELERATE);
        final View hotseatIconView = mHotseatIconView;
        if (hotseatIconView != null) {
            hotseatIconView.setVisibility(INVISIBLE);
            fadeAnim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationStart(Animator animation) {
                    super.onAnimationStart(animation);
                    hotseatIconView.setVisibility(VISIBLE);
                }
            });
        }
        if (onEndRunnable != null) {
            fadeAnim.addListener(AnimatorListeners.forSuccessCallback(onEndRunnable));
        }
        AnimatorSet animset = fadeAnim.buildAnim();
        rectAnim.addAnimatorListener(AnimatorListeners.forSuccessCallback(animset::start));
        mRunningWindowAnim = RunningWindowAnim.wrap(rectAnim);
    }

    @Override
    public void setNavBarGestureProgress(@Nullable Float displacement) {
        if (isGestureCompleted()) {
            return;
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
        if (isGestureCompleted()) {
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
            super(context, deviceState, gestureState);
            mRemoteTargetHandles[0] = new RemoteTargetGluer.RemoteTargetHandle(
                    mRemoteTargetHandles[0].getTaskViewSimulator(), new FakeTransformParams());

            for (RemoteTargetGluer.RemoteTargetHandle handle
                    : mTargetGluer.getRemoteTargetHandles()) {
                // Override home screen rotation preference so that home and overview animations
                // work properly
                handle.getTaskViewSimulator()
                        .getOrientationState()
                        .ignoreAllowHomeRotationPreference();
            }
        }

        void initDp(DeviceProfile dp) {
            initTransitionEndpoints(dp);
            mRemoteTargetHandles[0].getTaskViewSimulator().setPreviewBounds(
                    new Rect(0, 0, dp.widthPx, dp.heightPx), dp.getInsets());
        }

        @Override
        public void onCurrentShiftUpdated() {
            mRemoteTargetHandles[0].getPlaybackController()
                    .setProgress(mCurrentShift.value, mDragLengthFactor);
            mRemoteTargetHandles[0].getTaskViewSimulator().apply(
                    mRemoteTargetHandles[0].getTransformParams());
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
                    int fakeHomeIconLeft = getHotseatIconLeft();
                    int fakeHomeIconTop = getHotseatIconTop();
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
                            mFakeIconView, mDp);
                    mFakeIconView.setAlpha(1);
                    if (FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
                        int iconColor = ColorUtils.blendARGB(
                                getFakeTaskViewColor(), getHotseatIconColor(), progress);
                        mFakeIconView.getBackground().setTint(iconColor);
                        mFakeTaskView.setBackgroundColor(iconColor);
                    }
                    mFakeTaskView.setAlpha(getWindowAlpha(progress));
                    mFakePreviousTaskView.setAlpha(getWindowAlpha(progress));
                }

                @Override
                public void onCancel() {
                    mFakeIconView.setVisibility(View.INVISIBLE);
                }
            };
            RectFSpringAnim windowAnim = createWindowAnimationToHome(startShift,
                    homeAnimFactory)[0];
            windowAnim.start(mContext, mDp, velocityPxPerMs);
            return windowAnim;
        }
    }

    protected Animator createFingerDotHomeSwipeAnimator(float fingerDotStartTranslationY) {
        Animator homeSwipeAnimator = createFingerDotSwipeUpAnimator(fingerDotStartTranslationY)
                .setDuration(HOME_SWIPE_ANIMATION_DURATION_MILLIS);

        homeSwipeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                animateFakeTaskViewHome(
                        new PointF(
                                0f,
                                fingerDotStartTranslationY / HOME_SWIPE_ANIMATION_DURATION_MILLIS),
                        null);
            }
        });

        return homeSwipeAnimator;
    }

    protected Animator createFingerDotOverviewSwipeAnimator(float fingerDotStartTranslationY) {
        Animator overviewSwipeAnimator = createFingerDotSwipeUpAnimator(fingerDotStartTranslationY)
                .setDuration(OVERVIEW_SWIPE_ANIMATION_DURATION_MILLIS);

        overviewSwipeAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mFakePreviousTaskView.setVisibility(View.VISIBLE);
                onMotionPaused(true /*arbitrary value*/);
            }
        });

        return overviewSwipeAnimator;
    }


    private Animator createFingerDotSwipeUpAnimator(float fingerDotStartTranslationY) {
        ValueAnimator swipeAnimator = ValueAnimator.ofFloat(0f, 1f);

        swipeAnimator.addUpdateListener(valueAnimator -> {
            float gestureProgress =
                    -fingerDotStartTranslationY * valueAnimator.getAnimatedFraction();
            setNavBarGestureProgress(gestureProgress);
            mFingerDotView.setTranslationY(fingerDotStartTranslationY + gestureProgress);
        });

        return swipeAnimator;
    }

    private class FakeTransformParams extends TransformParams {

        @Override
        public SurfaceTransaction createSurfaceParams(BuilderProxy proxy) {
            RecordingSurfaceTransaction transaction = new RecordingSurfaceTransaction();
            proxy.onBuildTargetParams(transaction.mockProperties, null, this);
            return transaction;
        }

        @Override
        public void applySurfaceParams(SurfaceTransaction params) {
            if (params instanceof RecordingSurfaceTransaction) {
                MockProperties p = ((RecordingSurfaceTransaction) params).mockProperties;
                mFakeTaskView.setAnimationMatrix(p.matrix);
                mFakePreviousTaskView.setAnimationMatrix(p.matrix);
                mFakeTaskViewRect.set(p.windowCrop);
                mFakeTaskViewRadius = p.cornerRadius;
                mFakeTaskView.invalidateOutline();
                mFakePreviousTaskView.invalidateOutline();
            }
        }
    }
}
