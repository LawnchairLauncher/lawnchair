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

import static com.android.quickstep.interaction.TutorialController.TutorialType.BACK_NAVIGATION;
import static com.android.quickstep.interaction.TutorialController.TutorialType.BACK_NAVIGATION_COMPLETE;

import android.graphics.PointF;

import androidx.appcompat.content.res.AppCompatResources;

import com.android.launcher3.R;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;

/** A {@link TutorialController} for the Back tutorial. */
final class BackGestureTutorialController extends TutorialController {

    BackGestureTutorialController(BackGestureTutorialFragment fragment, TutorialType tutorialType) {
        super(fragment, tutorialType);
    }

    @Override
    public Integer getIntroductionTitle() {
        return R.string.back_gesture_intro_title;
    }

    @Override
    public Integer getIntroductionSubtitle() {
        return R.string.back_gesture_intro_subtitle;
    }

    @Override
    protected int getMockAppTaskThumbnailResId(boolean forDarkMode) {
        return R.drawable.mock_conversation;
    }

    @Override
    public void onBackGestureAttempted(BackGestureResult result) {
        if (mGestureCompleted) {
            return;
        }
        switch (mTutorialType) {
            case BACK_NAVIGATION:
                handleBackAttempt(result);
                break;
            case BACK_NAVIGATION_COMPLETE:
                if (result == BackGestureResult.BACK_COMPLETED_FROM_LEFT
                        || result == BackGestureResult.BACK_COMPLETED_FROM_RIGHT) {
                    mTutorialFragment.closeTutorial();
                }
                break;
        }
    }

    private void handleBackAttempt(BackGestureResult result) {
        switch (result) {
            case BACK_COMPLETED_FROM_LEFT:
            case BACK_COMPLETED_FROM_RIGHT:
                mTutorialFragment.releaseGestureVideoView();
                hideFeedback(true);
                mFakeTaskView.setBackground(AppCompatResources.getDrawable(mContext,
                        R.drawable.mock_conversations_list));
                int subtitleResId = mTutorialFragment.isAtFinalStep()
                        ? R.string.back_gesture_feedback_complete_without_follow_up
                        : R.string.back_gesture_feedback_complete_with_overview_follow_up;
                showFeedback(subtitleResId, true);
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
        if (mGestureCompleted) {
            return;
        }
        if (mTutorialType == BACK_NAVIGATION_COMPLETE) {
            if (result == NavBarGestureResult.HOME_GESTURE_COMPLETED) {
                mTutorialFragment.closeTutorial();
            }
        } else if (mTutorialType == BACK_NAVIGATION) {
            showFeedback(R.string.back_gesture_feedback_swipe_in_nav_bar);
        }
    }
}
