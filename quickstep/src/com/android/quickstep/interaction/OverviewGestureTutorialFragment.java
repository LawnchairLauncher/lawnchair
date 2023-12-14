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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.R;
import com.android.launcher3.logging.StatsLogManager;
import com.android.quickstep.interaction.TutorialController.TutorialType;

import java.util.ArrayList;

/** Shows the Overview gesture interactive tutorial. */
public class OverviewGestureTutorialFragment extends TutorialFragment {

    public OverviewGestureTutorialFragment() {
        this(false);
    }

    public OverviewGestureTutorialFragment(boolean fromTutorialMenu) {
        super(fromTutorialMenu);
    }

    @NonNull
    @Override
    TutorialType getDefaultTutorialType() {
        return TutorialType.OVERVIEW_NAVIGATION;
    }

    @Nullable
    @Override
    Integer getEdgeAnimationResId() {
        return R.drawable.gesture_tutorial_loop_overview;
    }

    @Nullable
    @Override
    protected Animator createGestureAnimation() {
        if (!(mTutorialController instanceof OverviewGestureTutorialController)) {
            return null;
        }
        float fingerDotStartTranslationY = (float) mRootView.getFullscreenHeight() / 2;
        OverviewGestureTutorialController controller =
                (OverviewGestureTutorialController) mTutorialController;

        AnimatorSet fingerDotAppearanceAnimator = controller.createFingerDotAppearanceAnimatorSet();
        fingerDotAppearanceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);

                mFingerDotView.setTranslationY(fingerDotStartTranslationY);
            }
        });

        AnimatorSet fingerDotDisappearanceAnimator =
                controller.createFingerDotDisappearanceAnimatorSet();
        fingerDotDisappearanceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                controller.animateTaskViewToOverview(false);
            }
        });

        Animator animationPause = controller.createAnimationPause();
        animationPause.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                controller.resetFakeTaskViewFromOverview();
            }
        });
        ArrayList<Animator> animators = new ArrayList<>();

        animators.add(fingerDotAppearanceAnimator);
        animators.add(controller.createFingerDotOverviewSwipeAnimator(fingerDotStartTranslationY));
        animators.add(controller.createAnimationPause());
        animators.add(fingerDotDisappearanceAnimator);
        animators.add(animationPause);

        AnimatorSet finalAnimation = new AnimatorSet();
        finalAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                controller.resetFakeTaskView(false);
            }
        });
        finalAnimation.playSequentially(animators);

        return finalAnimation;
    }

    @Override
    TutorialController createController(TutorialType type) {
        return new OverviewGestureTutorialController(this, type);
    }

    @Override
    Class<? extends TutorialController> getControllerClass() {
        return OverviewGestureTutorialController.class;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        releaseFeedbackAnimation();
        return super.onTouch(view, motionEvent);
    }

    @Override
    void logTutorialStepShown(@NonNull StatsLogManager statsLogManager) {
        statsLogManager.logger().log(
                StatsLogManager.LauncherEvent.LAUNCHER_GESTURE_TUTORIAL_OVERVIEW_STEP_SHOWN);
    }

    @Override
    void logTutorialStepCompleted(@NonNull StatsLogManager statsLogManager) {
        statsLogManager.logger().log(
                StatsLogManager.LauncherEvent.LAUNCHER_GESTURE_TUTORIAL_OVERVIEW_STEP_COMPLETED);
    }
}
