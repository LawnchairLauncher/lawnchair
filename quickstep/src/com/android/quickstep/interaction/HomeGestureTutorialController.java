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

import android.annotation.TargetApi;
import android.graphics.PointF;
import android.os.Build;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;
import com.android.quickstep.util.LottieAnimationColorUtils;

import java.util.Map;

/** A {@link TutorialController} for the Home tutorial. */
@TargetApi(Build.VERSION_CODES.R)
final class HomeGestureTutorialController extends SwipeUpGestureTutorialController {

    HomeGestureTutorialController(HomeGestureTutorialFragment fragment, TutorialType tutorialType) {
        super(fragment, tutorialType);

        // Set the Lottie animation colors specifically for the Home gesture
        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
            LottieAnimationColorUtils.updateColors(
                    mAnimatedGestureDemonstration,
                    Map.of(".onSurfaceHome", fragment.mRootView.mColorOnSurfaceHome,
                            ".surfaceHome", fragment.mRootView.mColorSurfaceHome,
                            ".secondaryHome", fragment.mRootView.mColorSecondaryHome));

            LottieAnimationColorUtils.updateColors(
                    mCheckmarkAnimation,
                    Map.of(".checkmark",
                            Utilities.isDarkTheme(mContext)
                                    ? fragment.mRootView.mColorOnSurfaceHome
                                    : fragment.mRootView.mColorSecondaryHome,
                            ".checkmarkBackground", fragment.mRootView.mColorSurfaceHome));
        }
    }

    @Override
    public int getIntroductionTitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.home_gesture_tutorial_title
                : R.string.home_gesture_intro_title;
    }

    @Override
    public int getIntroductionSubtitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.home_gesture_tutorial_subtitle
                : R.string.home_gesture_intro_subtitle;
    }

    @Override
    public int getSpokenIntroductionSubtitle() {
        return R.string.home_gesture_spoken_intro_subtitle;
    }

    @Override
    public int getSuccessFeedbackTitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.home_gesture_tutorial_success
                : R.string.gesture_tutorial_nice;
    }

    @Override
    public int getSuccessFeedbackSubtitle() {
        return mTutorialFragment.isAtFinalStep()
                ? R.string.home_gesture_feedback_complete_without_follow_up
                : R.string.home_gesture_feedback_complete_with_follow_up;
    }

    @Override
    public int getTitleTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_MainTitle_Home;
    }

    @Override
    public int getSuccessTitleTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_MainTitle_Success_Home;
    }

    @Override
    public int getDoneButtonTextAppearance() {
        return R.style.TextAppearance_GestureTutorial_ButtonLabel_Home;
    }

    @Override
    public int getDoneButtonColor() {
        return Utilities.isDarkTheme(mContext)
                ? mTutorialFragment.mRootView.mColorOnSurfaceHome
                : mTutorialFragment.mRootView.mColorSecondaryHome;
    }

    @Override
    protected int getMockAppTaskLayoutResId() {
        return mTutorialFragment.isLargeScreen()
                ? R.layout.gesture_tutorial_tablet_mock_webpage
                : R.layout.gesture_tutorial_mock_webpage;
    }

    @Override
    protected int getGestureLottieAnimationId() {
        return mTutorialFragment.isLargeScreen()
                ? mTutorialFragment.isFoldable()
                    ? R.raw.home_gesture_tutorial_open_foldable_animation
                    : R.raw.home_gesture_tutorial_tablet_animation
                : R.raw.home_gesture_tutorial_animation;
    }

    @Override
    protected int getFakeTaskViewColor() {
        return isGestureCompleted() ? getFakeLauncherColor() : getExitingAppColor();
    }

    @Override
    protected int getFakeLauncherColor() {
        return mTutorialFragment.mRootView.mColorSurfaceContainer;
    }

    @Override
    protected int getExitingAppColor() {
        return mTutorialFragment.mRootView.mColorSurfaceHome;
    }

    @Override
    protected int getHotseatIconColor() {
        return getExitingAppColor();
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        if (isGestureCompleted()) {
            return;
        }
        switch (mTutorialType) {
            case HOME_NAVIGATION:
                switch (result) {
                    case BACK_COMPLETED_FROM_LEFT:
                    case BACK_COMPLETED_FROM_RIGHT:
                    case BACK_CANCELLED_FROM_LEFT:
                    case BACK_CANCELLED_FROM_RIGHT:
                    case BACK_NOT_STARTED_TOO_FAR_FROM_EDGE:
                        resetTaskViews();
                        showFeedback(R.string.home_gesture_feedback_swipe_too_far_from_edge);
                        break;
                }
                break;
            case HOME_NAVIGATION_COMPLETE:
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
            case HOME_NAVIGATION:
                switch (result) {
                    case HOME_GESTURE_COMPLETED: {
                        mTutorialFragment.releaseFeedbackAnimation();
                        animateFakeTaskViewHome(finalVelocity, null);
                        showSuccessFeedback();
                        break;
                    }
                    case HOME_NOT_STARTED_TOO_FAR_FROM_EDGE:
                    case OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE:
                        resetTaskViews();
                        showFeedback(R.string.home_gesture_feedback_swipe_too_far_from_edge);
                        break;
                    case OVERVIEW_GESTURE_COMPLETED:
                        fadeOutFakeTaskView(false, () -> {
                            showFeedback(R.string.home_gesture_feedback_overview_detected);
                            showFakeTaskbar(/* animateFromHotseat= */ false);
                        });
                        break;
                    case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
                    case HOME_OR_OVERVIEW_CANCELLED:
                        fadeOutFakeTaskView(false, null);
                        showFeedback(R.string.home_gesture_feedback_wrong_swipe_direction);
                        break;
                }
                break;
            case HOME_NAVIGATION_COMPLETE:
                if (result == NavBarGestureResult.HOME_GESTURE_COMPLETED) {
                    mTutorialFragment.close();
                }
                break;
        }
    }

}
