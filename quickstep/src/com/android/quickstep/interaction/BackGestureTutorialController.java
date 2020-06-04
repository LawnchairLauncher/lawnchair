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

import static com.android.quickstep.interaction.TutorialController.TutorialType.BACK_NAVIGATION_COMPLETE;
import static com.android.quickstep.interaction.TutorialController.TutorialType.LEFT_EDGE_BACK_NAVIGATION;

import android.graphics.PointF;
import android.view.View;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;

/** A {@link TutorialController} for the Back tutorial. */
final class BackGestureTutorialController extends TutorialController {

    BackGestureTutorialController(BackGestureTutorialFragment fragment, TutorialType tutorialType) {
        super(fragment, tutorialType);
    }

    @Override
    Integer getTitleStringId() {
        switch (mTutorialType) {
            case RIGHT_EDGE_BACK_NAVIGATION:
                return R.string.back_gesture_tutorial_playground_title_swipe_inward_right_edge;
            case LEFT_EDGE_BACK_NAVIGATION:
                return R.string.back_gesture_tutorial_playground_title_swipe_inward_left_edge;
            case BACK_NAVIGATION_COMPLETE:
                return R.string.gesture_tutorial_confirm_title;
        }
        return null;
    }

    @Override
    Integer getSubtitleStringId() {
        switch (mTutorialType) {
            case RIGHT_EDGE_BACK_NAVIGATION:
                return R.string.back_gesture_tutorial_engaged_subtitle_swipe_inward_right_edge;
            case LEFT_EDGE_BACK_NAVIGATION:
                return R.string.back_gesture_tutorial_engaged_subtitle_swipe_inward_left_edge;
            case BACK_NAVIGATION_COMPLETE:
                return R.string.back_gesture_tutorial_confirm_subtitle;
        }
        return null;
    }

    @Override
    Integer getActionButtonStringId() {
        if (mTutorialType == BACK_NAVIGATION_COMPLETE) {
            return R.string.gesture_tutorial_action_button_label_done;
        }
        return null;
    }

    @Override
    Integer getActionTextButtonStringId() {
        if (mTutorialType == BACK_NAVIGATION_COMPLETE) {
            return R.string.gesture_tutorial_action_button_label_settings;
        }
        return null;
    }

    @Override
    void onActionButtonClicked(View button) {
        mTutorialFragment.closeTutorial();
    }

    @Override
    void onActionTextButtonClicked(View button) {
        mTutorialFragment.startSystemNavigationSetting();
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        switch (mTutorialType) {
            case RIGHT_EDGE_BACK_NAVIGATION:
                handleAttemptFromRight(result);
                break;
            case LEFT_EDGE_BACK_NAVIGATION:
                handleAttemptFromLeft(result);
                break;
            case BACK_NAVIGATION_COMPLETE:
                if (result == BackGestureResult.BACK_COMPLETED_FROM_LEFT
                        || result == BackGestureResult.BACK_COMPLETED_FROM_RIGHT) {
                    mTutorialFragment.closeTutorial();
                }
                break;
        }
    }

    private void handleAttemptFromRight(BackGestureResult result) {
        switch (result) {
            case BACK_COMPLETED_FROM_RIGHT:
                hideFeedback();
                hideHandCoachingAnimation();
                showRippleEffect(
                        () -> mTutorialFragment.changeController(LEFT_EDGE_BACK_NAVIGATION));
                break;
            case BACK_CANCELLED_FROM_RIGHT:
                showFeedback(R.string.back_gesture_feedback_cancelled_right_edge);
                break;
            case BACK_COMPLETED_FROM_LEFT:
            case BACK_CANCELLED_FROM_LEFT:
            case BACK_NOT_STARTED_TOO_FAR_FROM_EDGE:
                showFeedback(R.string.back_gesture_feedback_swipe_too_far_from_right_edge);
                break;
            case BACK_NOT_STARTED_IN_NAV_BAR_REGION:
                showFeedback(R.string.back_gesture_feedback_swipe_in_nav_bar);
                break;
        }
    }

    private void handleAttemptFromLeft(BackGestureResult result) {
        switch (result) {
            case BACK_COMPLETED_FROM_LEFT:
                hideFeedback();
                hideHandCoachingAnimation();
                showRippleEffect(
                        () -> mTutorialFragment.changeController(BACK_NAVIGATION_COMPLETE));
                break;
            case BACK_CANCELLED_FROM_LEFT:
                showFeedback(R.string.back_gesture_feedback_cancelled_left_edge);
                break;
            case BACK_COMPLETED_FROM_RIGHT:
            case BACK_CANCELLED_FROM_RIGHT:
            case BACK_NOT_STARTED_TOO_FAR_FROM_EDGE:
                showFeedback(R.string.back_gesture_feedback_swipe_too_far_from_left_edge);
                break;
            case BACK_NOT_STARTED_IN_NAV_BAR_REGION:
                showFeedback(R.string.back_gesture_feedback_swipe_in_nav_bar);
                break;
        }
    }

    @Override
    public void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity) {
        if (mTutorialType == BACK_NAVIGATION_COMPLETE) {
            if (result == NavBarGestureResult.HOME_GESTURE_COMPLETED) {
                mTutorialFragment.closeTutorial();
            }
        }
    }
}
