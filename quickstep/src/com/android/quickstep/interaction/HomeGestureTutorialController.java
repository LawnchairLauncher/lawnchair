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

import static com.android.quickstep.interaction.TutorialController.TutorialType.HOME_NAVIGATION_COMPLETE;

import android.view.View;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;

/** A {@link TutorialController} for the Home tutorial. */
final class HomeGestureTutorialController extends TutorialController {

    HomeGestureTutorialController(HomeGestureTutorialFragment fragment, TutorialType tutorialType) {
        super(fragment, tutorialType);
    }

    @Override
    void transitToController() {
        super.transitToController();
        if (mTutorialType != HOME_NAVIGATION_COMPLETE) {
            showHandCoachingAnimation();
        }
    }

    @Override
    Integer getTitleStringId() {
        switch (mTutorialType) {
            case HOME_NAVIGATION:
                return R.string.home_gesture_tutorial_playground_title;
            case HOME_NAVIGATION_COMPLETE:
                return R.string.gesture_tutorial_confirm_title;
        }
        return null;
    }

    @Override
    Integer getSubtitleStringId() {
        if (mTutorialType == TutorialType.HOME_NAVIGATION) {
            return R.string.home_gesture_tutorial_playground_subtitle;
        }
        return null;
    }

    @Override
    Integer getActionButtonStringId() {
        if (mTutorialType == HOME_NAVIGATION_COMPLETE) {
            return R.string.gesture_tutorial_action_button_label_done;
        }
        return null;
    }

    @Override
    void onActionButtonClicked(View button) {
        mTutorialFragment.closeTutorial();
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        switch (mTutorialType) {
            case HOME_NAVIGATION:
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
    public void onNavBarGestureAttempted(NavBarGestureResult result) {
        switch (mTutorialType) {
            case HOME_NAVIGATION:
                switch (result) {
                    case HOME_GESTURE_COMPLETED:
                        hideHandCoachingAnimation();
                        mTutorialFragment.changeController(HOME_NAVIGATION_COMPLETE);
                        break;
                    case HOME_NOT_STARTED_TOO_FAR_FROM_EDGE:
                    case OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE:
                        showFeedback(R.string.home_gesture_feedback_swipe_too_far_from_edge);
                        break;
                    case OVERVIEW_GESTURE_COMPLETED:
                        showFeedback(R.string.home_gesture_feedback_overview_detected);
                        break;
                    case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
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
