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

import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.config.FeatureFlags.ENABLE_NEW_GESTURE_NAV_TUTORIAL;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.annotation.Nullable;
import android.annotation.TargetApi;
import android.graphics.PointF;
import android.os.Build;
import android.os.Handler;
import android.view.View;

import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.quickstep.SwipeUpAnimationLogic;
import com.android.quickstep.interaction.EdgeBackGestureHandler.BackGestureResult;
import com.android.quickstep.interaction.NavBarGestureHandler.NavBarGestureResult;

import java.util.ArrayList;

/** A {@link TutorialController} for the Overview tutorial. */
@TargetApi(Build.VERSION_CODES.R)
final class OverviewGestureTutorialController extends SwipeUpGestureTutorialController {

    OverviewGestureTutorialController(OverviewGestureTutorialFragment fragment,
            TutorialType tutorialType) {
        super(fragment, tutorialType);
    }
    @Override
    public int getIntroductionTitle() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.string.overview_gesture_tutorial_title
                : R.string.overview_gesture_intro_title;
    }

    @Override
    public int getIntroductionSubtitle() {
        return R.string.overview_gesture_intro_subtitle;
    }

    @Override
    public int getSpokenIntroductionSubtitle() {
        return R.string.overview_gesture_spoken_intro_subtitle;
    }

    @Override
    public int getSuccessFeedbackSubtitle() {
        return mTutorialFragment.getNumSteps() > 1 && mTutorialFragment.isAtFinalStep()
                ? R.string.overview_gesture_feedback_complete_with_follow_up
                : R.string.overview_gesture_feedback_complete_without_follow_up;
    }

    @Override
    protected int getMockAppTaskLayoutResId() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.layout.gesture_tutorial_mock_task_view
                : mTutorialFragment.isLargeScreen()
                        ? R.layout.gesture_tutorial_tablet_mock_conversation_list
                        : R.layout.gesture_tutorial_mock_conversation_list;
    }

    @Override
    protected int getGestureLottieAnimationId() {
        return mTutorialFragment.isLargeScreen()
                ? R.raw.overview_gesture_tutorial_tablet_animation
                : R.raw.overview_gesture_tutorial_animation;
    }

    @Override
    protected int getSwipeActionColorResId() {
        return ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()
                ? R.color.gesture_overview_background
                : R.color.gesture_overview_tutorial_swipe_rect;
    }

    @Override
    protected int getMockPreviousAppTaskThumbnailColorResId() {
        return R.color.gesture_overview_tutorial_swipe_rect;
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
                        showFeedback(R.string.overview_gesture_feedback_swipe_too_far_from_edge);
                        break;
                    case OVERVIEW_GESTURE_COMPLETED:
                        mTutorialFragment.releaseFeedbackAnimation();
                        if (ENABLE_NEW_GESTURE_NAV_TUTORIAL.get()) {
                            onMotionPaused(true /*arbitrary value*/);
                            animateTaskViewToOverview(() -> {
                                mFakeTaskView.setVisibility(View.INVISIBLE);
                                if(!mTutorialFragment.isLargeScreen()){
                                    mFakePreviousTaskView.animateToFillScreen(() -> {
                                        mFakeLauncherView.setBackgroundColor(
                                                mContext.getColor(
                                                        R.color.gesture_overview_tutorial_swipe_rect
                                                ));
                                        showSuccessFeedback();
                                    });
                                } else {
                                    mFakeLauncherView.setBackgroundColor(
                                            mContext.getColor(
                                                    R.color.gesture_overview_tutorial_swipe_rect
                                            ));
                                    showSuccessFeedback();
                                }
                            });
                        } else {
                            animateTaskViewToOverview(null);
                            onMotionPaused(true /*arbitrary value*/);
                            showSuccessFeedback();
                        }
                        break;
                    case HOME_OR_OVERVIEW_NOT_STARTED_WRONG_SWIPE_DIRECTION:
                    case HOME_OR_OVERVIEW_CANCELLED:
                        fadeOutFakeTaskView(false, true, null);
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
     * @param runnable
     */
    public void animateTaskViewToOverview(@Nullable Runnable runnable) {
        PendingAnimation anim = new PendingAnimation(TASK_VIEW_END_ANIMATION_DURATION_MILLIS);
        anim.setFloat(mTaskViewSwipeUpAnimation
                .getCurrentShift(), AnimatedFloat.VALUE, 1, ACCEL);

        anim.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animator) {
                if (runnable != null) {
                    new Handler().postDelayed(runnable, 300);
                }
            }
        });

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
