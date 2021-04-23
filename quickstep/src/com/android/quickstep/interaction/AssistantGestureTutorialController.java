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

import static com.android.quickstep.interaction.TutorialController.TutorialType.ASSISTANT_COMPLETE;

import android.graphics.PointF;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;

/** A {@link TutorialController} for the Assistant tutorial. */
final class AssistantGestureTutorialController extends TutorialController {

    AssistantGestureTutorialController(AssistantGestureTutorialFragment fragment,
                                       TutorialType tutorialType) {
        super(fragment, tutorialType);
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        switch (mTutorialType) {
            case ASSISTANT:
                switch (result) {
                    case BACK_COMPLETED_FROM_LEFT:
                    case BACK_COMPLETED_FROM_RIGHT:
                    case BACK_CANCELLED_FROM_LEFT:
                    case BACK_CANCELLED_FROM_RIGHT:
                        showFeedback(R.string.assistant_gesture_feedback_swipe_too_far_from_corner);
                        break;
                }
                break;
            case ASSISTANT_COMPLETE:
                if (result == BackGestureResult.BACK_COMPLETED_FROM_LEFT
                        || result == BackGestureResult.BACK_COMPLETED_FROM_RIGHT) {
                    mTutorialFragment.closeTutorial();
                }
                break;
        }
    }


    @Override
    public void onNavBarGestureAttempted(NavBarGestureResult result, PointF finalVelocity) {
        switch (mTutorialType) {
            case ASSISTANT:
                switch (result) {
                    case HOME_GESTURE_COMPLETED:
                    case OVERVIEW_GESTURE_COMPLETED:
                    case HOME_NOT_STARTED_TOO_FAR_FROM_EDGE:
                    case OVERVIEW_NOT_STARTED_TOO_FAR_FROM_EDGE:
                    case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
                    case HOME_OR_OVERVIEW_CANCELLED:
                        showFeedback(R.string.assistant_gesture_feedback_swipe_too_far_from_corner);
                        break;
                    case ASSISTANT_COMPLETED:
                        hideFeedback(true);
                        showRippleEffect(null);
                        showFeedback(R.string.assistant_gesture_tutorial_playground_subtitle);
                        break;
                    case ASSISTANT_NOT_STARTED_BAD_ANGLE:
                        showFeedback(R.string.assistant_gesture_feedback_swipe_not_diagonal);
                        break;
                    case ASSISTANT_NOT_STARTED_SWIPE_TOO_SHORT:
                        showFeedback(R.string.assistant_gesture_feedback_swipe_not_long_enough);
                        break;
                }
                break;
            case ASSISTANT_COMPLETE:
                if (result == NavBarGestureResult.HOME_GESTURE_COMPLETED) {
                    mTutorialFragment.closeTutorial();
                }
                break;
        }
    }

    @Override
    public void setAssistantProgress(float progress) {
        // TODO: Create an animation.
    }
}
