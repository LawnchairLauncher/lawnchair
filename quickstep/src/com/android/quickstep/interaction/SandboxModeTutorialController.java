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

import android.graphics.PointF;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;

/** A {@link TutorialController} for the Sandbox Mode. */
public class SandboxModeTutorialController extends SwipeUpGestureTutorialController {

    SandboxModeTutorialController(SandboxModeTutorialFragment fragment, TutorialType tutorialType) {
        super(fragment, tutorialType);
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        switch (result) {
            case BACK_COMPLETED_FROM_LEFT:
            case BACK_COMPLETED_FROM_RIGHT:
                showRippleEffect(null);
                showFeedback(R.string.sandbox_mode_back_gesture_feedback_successful);
                break;
            case BACK_CANCELLED_FROM_LEFT:
            case BACK_CANCELLED_FROM_RIGHT:
                showFeedback(R.string.back_gesture_feedback_cancelled);
                break;
            case BACK_NOT_STARTED_TOO_FAR_FROM_EDGE:
                showFeedback(R.string.sandbox_mode_back_gesture_feedback_swipe_too_far_from_edge);
                break;
        }
    }

    @Override
    public void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity) {
        switch (result) {
            case ASSISTANT_COMPLETED:
                showRippleEffect(null);
                showFeedback(R.string.sandbox_mode_assistant_gesture_feedback_successful);
                break;
            case HOME_GESTURE_COMPLETED:
                animateFakeTaskViewHome(finalVelocity, () -> {
                    showFeedback(R.string.sandbox_mode_home_gesture_feedback_successful);
                });
                break;
            case OVERVIEW_GESTURE_COMPLETED:
                fadeOutFakeTaskView(true, true, () -> {
                    showFeedback(R.string.sandbox_mode_overview_gesture_feedback_successful);
                });
                break;
            case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
            case HOME_OR_OVERVIEW_CANCELLED:
            case HOME_NOT_STARTED_TOO_FAR_FROM_EDGE:
            case OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE:
                showFeedback(R.string.home_gesture_feedback_swipe_too_far_from_edge);
                break;
        }
    }
}
