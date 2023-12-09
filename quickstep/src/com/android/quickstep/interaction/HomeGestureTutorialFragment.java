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

/** Shows the Home gesture interactive tutorial. */
public class HomeGestureTutorialFragment extends TutorialFragment {

    public HomeGestureTutorialFragment() {
        this(false);
    }

    public HomeGestureTutorialFragment(boolean fromTutorialMenu) {
        super(fromTutorialMenu);
    }

    @NonNull
    @Override
    TutorialType getDefaultTutorialType() {
        return TutorialType.HOME_NAVIGATION;
    }

    @Nullable
    @Override
    Integer getEdgeAnimationResId() {
        return R.drawable.gesture_tutorial_loop_home;
    }

    @Nullable
    @Override
    protected Animator createGestureAnimation() {
        if (!(mTutorialController instanceof HomeGestureTutorialController)) {
            return null;
        }
        float fingerDotStartTranslationY = (float) mRootView.getFullscreenHeight() / 2;
        HomeGestureTutorialController controller =
                (HomeGestureTutorialController) mTutorialController;

        AnimatorSet fingerDotAppearanceAnimator = controller.createFingerDotAppearanceAnimatorSet();
        fingerDotAppearanceAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mFingerDotView.setTranslationY(fingerDotStartTranslationY);
            }
        });

        Animator animationPause = controller.createAnimationPause();
        animationPause.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                controller.resetFakeTaskView(true);
            }
        });
        ArrayList<Animator> animators = new ArrayList<>();

        animators.add(fingerDotAppearanceAnimator);
        animators.add(controller.createFingerDotHomeSwipeAnimator(fingerDotStartTranslationY));
        animators.add(controller.createFingerDotDisappearanceAnimatorSet());
        animators.add(animationPause);

        AnimatorSet finalAnimation = new AnimatorSet();
        finalAnimation.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                controller.resetFakeTaskView(true);
            }
        });
        finalAnimation.playSequentially(animators);

        return finalAnimation;
    }

    @Override
    TutorialController createController(TutorialType type) {
        return new HomeGestureTutorialController(this, type);
    }

    @Override
    Class<? extends TutorialController> getControllerClass() {
        return HomeGestureTutorialController.class;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        releaseFeedbackAnimation();
        return super.onTouch(view, motionEvent);
    }

    @Override
    void logTutorialStepShown(@NonNull StatsLogManager statsLogManager) {
        statsLogManager.logger().log(
                StatsLogManager.LauncherEvent.LAUNCHER_GESTURE_TUTORIAL_HOME_STEP_SHOWN);
    }

    @Override
    void logTutorialStepCompleted(@NonNull StatsLogManager statsLogManager) {
        statsLogManager.logger().log(
                StatsLogManager.LauncherEvent.LAUNCHER_GESTURE_TUTORIAL_HOME_STEP_COMPLETED);
    }
}
