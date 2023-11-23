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

import static com.android.app.animation.Interpolators.ACCELERATE;
import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.TargetApi;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.quickstep.SwipeUpAnimationLogic;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;
import com.android.quickstep.util.LottieAnimationColorUtils;

import java.util.ArrayList;
import java.util.Map;

/** A {@link TutorialController} for the Overview tutorial. */
@TargetApi(Build.VERSION_CODES.R)
final class OverviewGestureTutorialController extends SwipeUpGestureTutorialController {

    private static final float LAUNCHER_COLOR_BLENDING_RATIO = 0.4f;

    OverviewGestureTutorialController(OverviewGestureTutorialFragment fragment,
            TutorialType tutorialType) {
        super(fragment, tutorialType);

        // Set the Lottie animation colors specifically for the Overview gesture
        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            LottieAnimationColorUtils.updateToArgbColors(
                    mAnimatedGestureDemonstration,
                    Map.of(".onSurfaceOverview", fragment.mRootView.mColorOnSurfaceOverview,
                            ".surfaceOverview", fragment.mRootView.mColorSurfaceOverview,
                            ".secondaryOverview", fragment.mRootView.mColorSecondaryOverview));

            LottieAnimationColorUtils.updateToArgbColors(
                    mCheckmarkAnimation,
                    Map.of(".checkmark",
                            Utilities.isDarkTheme(mContext)
                                    ? fragment.mRootView.mColorOnSurfaceOverview
                                    : fragment.mRootView.mColorSecondaryOverview,
                            ".checkmarkBackground", fragment.mRootView.mColorSurfaceOverview));
        }
    }
    @Override
    public int getIntroductionTitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.overview_gesture_tutorial_title
                : R.string.overview_gesture_intro_title;
    }

    @Override
    public int getIntroductionSubtitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.overview_gesture_tutorial_subtitle
                : R.string.overview_gesture_intro_subtitle;
    }

    @Override
    public int getSpokenIntroductionSubtitle() {
        return R.string.overview_gesture_spoken_intro_subtitle;
    }

    @Override
    public int getSuccessFeedbackTitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.overview_gesture_tutorial_success
                : R.string.gesture_tutorial_nice;
    }

    @Override
    public int getSuccessFeedbackSubtitle() {
        return mTutorialFragment.getNumSteps() > 1 && mTutorialFragment.isAtFinalStep()
                ? R.string.overview_gesture_feedback_complete_with_follow_up
                : R.string.overview_gesture_feedback_complete_without_follow_up;
    }

    @Override
    public int getTitleTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_MainTitle_Overview;
    }

    @Override
    public int getSuccessTitleTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_MainTitle_Success_Overview;
    }

    @Override
    public int getDoneButtonTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_ButtonLabel_Overview;
    }

    @Override
    public int getDoneButtonColor() {
        return Utilities.isDarkTheme(mContext)
                ? mTutorialFragment.mRootView.mColorOnSurfaceOverview
                : mTutorialFragment.mRootView.mColorSecondaryOverview;
    }

    @Override
    protected int getMockAppTaskLayoutResId() {
        return mTutorialFragment.isLargeScreen()
                ? R.layout.gesture_tutorial_tablet_mock_conversation_list
                : R.layout.gesture_tutorial_mock_conversation_list;
    }

    @Override
    protected int getGestureLottieAnimationId() {
        return mTutorialFragment.isLargeScreen()
                ? mTutorialFragment.isFoldable()
                    ? R.raw.overview_gesture_tutorial_open_foldable_animation
                    : R.raw.overview_gesture_tutorial_tablet_animation
                : R.raw.overview_gesture_tutorial_animation;
    }

    @ColorInt
    private int getFakeTaskViewStartColor() {
        return mTutorialFragment.mRootView.mColorSurfaceOverview;
    }

    @ColorInt
    private int getFakeTaskViewEndColor() {
        return getMockPreviousAppTaskThumbnailColor();
    }

    @Override
    protected int getFakeTaskViewColor() {
        return isGestureCompleted()
                ? getFakeTaskViewEndColor()
                : getFakeTaskViewStartColor();
    }

    @Override
    protected int getFakeLauncherColor() {
        return ColorUtils.blendARGB(
                mTutorialFragment.mRootView.mColorSurfaceContainer,
                mTutorialFragment.mRootView.mColorOnSurfaceOverview,
                LAUNCHER_COLOR_BLENDING_RATIO);
    }

    @Override
    protected int getHotseatIconColor() {
        return mTutorialFragment.mRootView.mColorOnSurfaceOverview;
    }

    @Override
    protected int getMockPreviousAppTaskThumbnailColor() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? mTutorialFragment.mRootView.mColorSurfaceContainer
                : mContext.getResources().getColor(
                        R.color.gesture_tutorial_fake_previous_task_view_color);
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        if (isGestureCompleted()) {
            return;
        }
        switch (mTutorialType) {
            case OVERVIEW_NAVIGATION:
                switch (result) {
                    case BACK_COMPLETED_FROM_LEFT:
                    case BACK_COMPLETED_FROM_RIGHT:
                    case BACK_CANCELLED_FROM_LEFT:
                    case BACK_CANCELLED_FROM_RIGHT:
                    case BACK_NOT_STARTED_TOO_FAR_FROM_EDGE:
                        resetTaskViews();
                        showFeedback(R.string.overview_gesture_feedback_swipe_too_far_from_edge);
                        break;
                }
                break;
            case OVERVIEW_NAVIGATION_COMPLETE:
                if (result == BackGestureResult.BACK_COMPLETED_FROM_LEFT
                        || result == BackGestureResult.BACK_COMPLETED_FROM_RIGHT) {
                    mTutorialFragment.close();
                }
                break;
        }
    }

    @Override
    public void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity) {
        if (isGestureCompleted()) {
            return;
        }
        switch (mTutorialType) {
            case OVERVIEW_NAVIGATION:
                switch (result) {
                    case HOME_GESTURE_COMPLETED: {
                        animateFakeTaskViewHome(finalVelocity, () -> {
                            showFeedback(R.string.overview_gesture_feedback_home_detected);
                            resetFakeTaskView(true);
                        });
                        break;
                    }
                    case HOME_NOT_STARTED_TOO_FAR_FROM_EDGE:
                    case OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE:
                        resetTaskViews();
                        showFeedback(R.string.overview_gesture_feedback_swipe_too_far_from_edge);
                        break;
                    case OVERVIEW_GESTURE_COMPLETED:
                        setGestureCompleted();
                        mTutorialFragment.releaseFeedbackAnimation();
                        animateTaskViewToOverview(ENABLE_NEW_GESTURE_NAV_TUTORIAL.get());
                        onMotionPaused(true /*arbitrary value*/);
                        if (!ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
                            showSuccessFeedback();
                        }
                        break;
                    case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
                    case HOME_OR_OVERVIEW_CANCELLED:
                        fadeOutFakeTaskView(false, null);
                        showFeedback(R.string.overview_gesture_feedback_wrong_swipe_direction);
                        break;
                }
                break;
            case OVERVIEW_NAVIGATION_COMPLETE:
                if (result == NavBarGestureResult.HOME_GESTURE_COMPLETED) {
                    mTutorialFragment.close();
                }
                break;
        }
    }

    /**
     * runnable executed with slight delay to ease the swipe animation after landing on overview
     */
    public void animateTaskViewToOverview(boolean animateDelayedSuccessFeedback) {
        PendingAnimation anim = new PendingAnimation(TASK_VIEW_END_ANIMATION_DURATION_MILLIS);
        anim.setFloat(mTaskViewSwipeUpAnimation
                .getCurrentShift(), AnimatedFloat.VALUE, 1, ACCELERATE);

        if (animateDelayedSuccessFeedback) {
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animator) {
                    new Handler().postDelayed(
                            () -> fadeOutFakeTaskView(
                                    /* toOverviewFirst= */ true,
                                    /* animatePreviousTask= */ false,
                                    /* resetViews= */ false,
                                    /* updateListener= */ v -> mFakeTaskView.setBackgroundColor(
                                            ColorUtils.blendARGB(
                                                    getFakeTaskViewStartColor(),
                                                    getFakeTaskViewEndColor(),
                                                    v.getAnimatedFraction())),
                                    /* onEndRunnable= */ () -> {
                                        showSuccessFeedback();
                                        resetTaskViews();
                                    }),
                            TASK_VIEW_FILL_SCREEN_ANIMATION_DELAY_MILLIS);
                }
            });
        }

        ArrayList<Animator> animators = new ArrayList<>();

        if (mTutorialFragment.isLargeScreen()) {
            Animator multiRowAnimation = mFakePreviousTaskView.createAnimationToMultiRowLayout();

            if (multiRowAnimation != null) {
                multiRowAnimation.setDuration(TASK_VIEW_END_ANIMATION_DURATION_MILLIS);
                animators.add(multiRowAnimation);
            }
        }
        animators.add(anim.buildAnim());

        AnimatorSet animset = new AnimatorSet();
        animset.playTogether(animators);
        animset.start();
        mRunningWindowAnim = SwipeUpAnimationLogic.RunningWindowAnim.wrap(animset);
    }
}
