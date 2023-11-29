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

import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;
import static com.android.quickstep.interaction.TutorialController.TutorialType.BACK_NAVIGATION;
import static com.android.quickstep.interaction.TutorialController.TutorialType.BACK_NAVIGATION_COMPLETE;

import android.annotation.LayoutRes;
import android.graphics.PointF;
import android.view.View;

import com.android.app.animation.Interpolators;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;
import com.android.quickstep.util.LottieAnimationColorUtils;

import java.util.Map;

/** A {@link TutorialController} for the Back tutorial. */
final class BackGestureTutorialController extends TutorialController {
    private static final float Y_TRANSLATION_SMOOTHENING_FACTOR = .2f;
    private static final float EXITING_APP_MIN_SIZE_PERCENTAGE = .8f;

    BackGestureTutorialController(BackGestureTutorialFragment fragment, TutorialType tutorialType) {
        super(fragment, tutorialType);
        // Set the Lottie animation colors specifically for the Back gesture
        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            LottieAnimationColorUtils.updateToArgbColors(
                    mAnimatedGestureDemonstration,
                    Map.of(".onSurfaceBack", fragment.mRootView.mColorOnSurfaceBack,
                            ".surfaceBack", fragment.mRootView.mColorSurfaceBack,
                            ".secondaryBack", fragment.mRootView.mColorSecondaryBack));

            LottieAnimationColorUtils.updateToArgbColors(
                    mCheckmarkAnimation,
                    Map.of(".checkmark",
                            Utilities.isDarkTheme(mContext)
                                    ? fragment.mRootView.mColorOnSurfaceBack
                                    : fragment.mRootView.mColorSecondaryBack,
                            ".checkmarkBackground", fragment.mRootView.mColorSurfaceBack));
        }
    }

    @Override
    public int getIntroductionTitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.back_gesture_tutorial_title
                : R.string.back_gesture_intro_title;
    }

    @Override
    public int getIntroductionSubtitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.back_gesture_tutorial_subtitle
                : R.string.back_gesture_intro_subtitle;
    }

    @Override
    public int getSpokenIntroductionSubtitle() {
        return R.string.back_gesture_spoken_intro_subtitle;
    }

    @Override
    public int getSuccessFeedbackTitle() {
        return R.string.gesture_tutorial_nice;
    }

    @Override
    public int getSuccessFeedbackSubtitle() {
        return mTutorialFragment.isAtFinalStep()
                ? R.string.back_gesture_feedback_complete_without_follow_up
                : R.string.back_gesture_feedback_complete_with_overview_follow_up;
    }

    @Override
    public int getTitleTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_MainTitle_Back;
    }

    @Override
    public int getSuccessTitleTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_MainTitle_Success_Back;
    }

    @Override
    public int getDoneButtonTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_ButtonLabel_Back;
    }

    @Override
    public int getDoneButtonColor() {
        return Utilities.isDarkTheme(mContext)
                ? mTutorialFragment.mRootView.mColorOnSurfaceBack
                : mTutorialFragment.mRootView.mColorSecondaryBack;
    }

    @Override
    protected int getMockAppTaskLayoutResId() {
        return getMockAppTaskCurrentPageLayoutResId();
    }

    @Override
    protected int getGestureLottieAnimationId() {
        return mTutorialFragment.isLargeScreen()
                ? mTutorialFragment.isFoldable()
                    ? R.raw.back_gesture_tutorial_open_foldable_animation
                    : R.raw.back_gesture_tutorial_tablet_animation
                : R.raw.back_gesture_tutorial_animation;
    }

    @LayoutRes
    int getMockAppTaskCurrentPageLayoutResId() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.layout.back_gesture_tutorial_background
                : mTutorialFragment.isLargeScreen()
                        ? R.layout.gesture_tutorial_tablet_mock_conversation
                        : R.layout.gesture_tutorial_mock_conversation;
    }

    @LayoutRes
    int getMockAppTaskPreviousPageLayoutResId() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.layout.back_gesture_tutorial_background
                : mTutorialFragment.isLargeScreen()
                        ? R.layout.gesture_tutorial_tablet_mock_conversation_list
                        : R.layout.gesture_tutorial_mock_conversation_list;
    }

    @Override
    protected int getFakeLauncherColor() {
        return mTutorialFragment.mRootView.mColorSurfaceContainer;
    }

    @Override
    protected int getExitingAppColor() {
        return mTutorialFragment.mRootView.mColorSurfaceBack;
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        if (isGestureCompleted()) {
            return;
        }
        switch (mTutorialType) {
            case BACK_NAVIGATION:
                handleBackAttempt(result);
                break;
            case BACK_NAVIGATION_COMPLETE:
                if (result == BackGestureResult.BACK_COMPLETED_FROM_LEFT
                        || result == BackGestureResult.BACK_COMPLETED_FROM_RIGHT) {
                    mTutorialFragment.close();
                }
                break;
        }
    }

    @Override
    public void onBackGestureProgress(float diffx, float diffy, boolean isLeftGesture) {
        if (isGestureCompleted()) {
            return;
        }

        float normalizedSwipeProgress = Math.abs(diffx / mScreenWidth);
        float smoothedExitingAppScale = Utilities.mapBoundToRange(
                normalizedSwipeProgress,
                /* lowerBound = */ 0f,
                /* upperBound = */ 1f,
                /* toMin = */ 1f,
                /* toMax = */ EXITING_APP_MIN_SIZE_PERCENTAGE,
                Interpolators.DECELERATE);

        // shrink the exiting app as we progress through the back gesture
        mExitingAppView.setPivotX(isLeftGesture ? mScreenWidth : 0);
        mExitingAppView.setPivotY(mScreenHeight / 2f);
        mExitingAppView.setScaleX(smoothedExitingAppScale);
        mExitingAppView.setScaleY(smoothedExitingAppScale);
        mExitingAppView.setTranslationY(diffy * Y_TRANSLATION_SMOOTHENING_FACTOR);
        mExitingAppView.setTranslationX(Utilities.mapBoundToRange(
                normalizedSwipeProgress,
                /* lowerBound = */ 0f,
                /* upperBound = */ 1f,
                /* toMin = */ 0,
                /* toMax = */ mExitingAppMargin,
                Interpolators.DECELERATE)
                * (isLeftGesture ? -1 : 1));

        // round the corners of the exiting app as we progress through the back gesture
        mExitingAppRadius = (int) Utilities.mapBoundToRange(
                normalizedSwipeProgress,
                /* lowerBound = */ 0f,
                /* upperBound = */ 1f,
                /* toMin = */ mExitingAppStartingCornerRadius,
                /* toMax = */ mExitingAppEndingCornerRadius,
                Interpolators.EMPHASIZED_DECELERATE);
        mExitingAppView.invalidateOutline();
    }

    private void handleBackAttempt(BackGestureResult result) {
        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            resetViewsForBackGesture();
        }

        switch (result) {
            case BACK_COMPLETED_FROM_LEFT:
            case BACK_COMPLETED_FROM_RIGHT:
                mTutorialFragment.releaseFeedbackAnimation();
                if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
                    mExitingAppView.setVisibility(View.GONE);
                }
                updateFakeAppTaskViewLayout(getMockAppTaskPreviousPageLayoutResId());
                showSuccessFeedback();
                break;
            case BACK_CANCELLED_FROM_LEFT:
            case BACK_CANCELLED_FROM_RIGHT:
                showFeedback(R.string.back_gesture_feedback_cancelled);
                break;
            case BACK_NOT_STARTED_TOO_FAR_FROM_EDGE:
                showFeedback(R.string.back_gesture_feedback_swipe_too_far_from_edge);
                break;
            case BACK_NOT_STARTED_IN_NAV_BAR_REGION:
                showFeedback(R.string.back_gesture_feedback_swipe_in_nav_bar);
                break;
        }
    }

    @Override
    public void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity) {
        if (isGestureCompleted()) {
            return;
        }
        if (mTutorialType == BACK_NAVIGATION_COMPLETE) {
            if (result == NavBarGestureResult.HOME_GESTURE_COMPLETED) {
                mTutorialFragment.close();
            }
        } else if (mTutorialType == BACK_NAVIGATION) {
            switch (result) {
                case HOME_NOT_STARTED_TOO_FAR_FROM_EDGE:
                case OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE:
                case HOME_OR_OVERVIEW_CANCELLED:
                    showFeedback(R.string.back_gesture_feedback_swipe_too_far_from_edge);
                    break;
                case HOME_GESTURE_COMPLETED:
                case OVERVIEW_GESTURE_COMPLETED:
                case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
                default:
                    showFeedback(R.string.back_gesture_feedback_swipe_in_nav_bar);

            }
        }
    }
}
