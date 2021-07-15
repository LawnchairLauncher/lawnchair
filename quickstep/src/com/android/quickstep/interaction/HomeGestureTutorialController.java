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

import android.annotation.TargetApi;
import android.graphics.PointF;
import android.os.Build;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;

/** A {@link TutorialController} for the Home tutorial. */
@TargetApi(Build.VERSION_CODES.R)
final class HomeGestureTutorialController extends SwipeUpGestureTutorialController {

    HomeGestureTutorialController(HomeGestureTutorialFragment fragment, TutorialType tutorialType) {
        super(fragment, tutorialType);
    }

    @Override
    public Integer getIntroductionTitle() {
        return R.string.home_gesture_intro_title;
    }

    @Override
    public Integer getIntroductionSubtitle() {
        return R.string.home_gesture_intro_subtitle;
    }

    @Override
    protected int getMockAppTaskThumbnailResId(boolean forDarkMode) {
        return forDarkMode ? R.drawable.mock_webpage_dark_mode : R.drawable.mock_webpage_light_mode;
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        if (mGestureCompleted) {
            return;
        }
        switch (mTutorialType) {
            case HOME_NAVIGATION:
                switch (result) {
                    case BACK_COMPLETED_FROM_LEFT:
                    case BACK_COMPLETED_FROM_RIGHT:
                    case BACK_CANCELLED_FROM_LEFT:
                    case BACK_CANCELLED_FROM_RIGHT:
                        showFeedback(R.string.home_gesture_feedback_swipe_too_far_from_edge);
                        break;
                }
                break;
            case HOME_NAVIGATION_COMPLETE:
                if (result == BackGestureResult.BACK_COMPLETED_FROM_LEFT
                        || result == BackGestureResult.BACK_COMPLETED_FROM_RIGHT) {
                    mTutorialFragment.closeTutorial();
                }
                break;
        }
    }

    @Override
    public void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity) {
        if (mGestureCompleted) {
            return;
        }
        switch (mTutorialType) {
            case HOME_NAVIGATION:
                switch (result) {
                    case HOME_GESTURE_COMPLETED: {
                        mTutorialFragment.releaseGestureVideoView();
                        animateFakeTaskViewHome(finalVelocity, null);
                        int subtitleResId = mTutorialFragment.isAtFinalStep()
                                ? R.string.home_gesture_feedback_complete_without_follow_up
                                : R.string.home_gesture_feedback_complete_with_follow_up;
                        showFeedback(subtitleResId, true);
                        break;
                    }
                    case HOME_NOT_STARTED_TOO_FAR_FROM_EDGE:
                    case OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE:
                        showFeedback(R.string.home_gesture_feedback_swipe_too_far_from_edge);
                        break;
                    case OVERVIEW_GESTURE_COMPLETED:
                        fadeOutFakeTaskView(true, true, () ->
                                showFeedback(R.string.home_gesture_feedback_overview_detected));
                        break;
                    case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
                    case HOME_OR_OVERVIEW_CANCELLED:
                        fadeOutFakeTaskView(false, true, null);
                        showFeedback(R.string.home_gesture_feedback_wrong_swipe_direction);
                        break;
                }
                break;
            case HOME_NAVIGATION_COMPLETE:
                if (result == NavBarGestureResult.HOME_GESTURE_COMPLETED) {
                    mTutorialFragment.closeTutorial();
                }
                break;
        }
    }

}
