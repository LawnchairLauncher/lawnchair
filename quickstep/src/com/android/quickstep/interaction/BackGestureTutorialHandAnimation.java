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

import android.content.Context;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import androidx.core.content.ContextCompat;

import com.android.launcher3.R;
import com.android.quickstep.interaction.BackGestureTutorialFragment.TutorialType;

import java.time.Duration;

/** Hand coaching animation. */
final class BackGestureTutorialHandAnimation {

    // A delay for waiting the Activity fully launches.
    private static final Duration ANIMATION_START_DELAY = Duration.ofMillis(300L);

    private final ImageView mHandCoachingView;
    private final AnimatedVectorDrawable mGestureAnimation;

    private boolean mIsAnimationPlayed = false;

    BackGestureTutorialHandAnimation(Context context, View rootView) {
        mHandCoachingView = rootView.findViewById(
                R.id.back_gesture_tutorial_fragment_hand_coaching);
        mGestureAnimation = (AnimatedVectorDrawable) ContextCompat.getDrawable(context,
                R.drawable.back_gesture);
    }

    boolean isRunning() {
        return mGestureAnimation.isRunning();
    }

    /**
     * Starts animation if the playground is launched for the first time.
     */
    void maybeStartLoopedAnimation(TutorialType tutorialType) {
        if (isRunning() || mIsAnimationPlayed) {
            return;
        }

        mIsAnimationPlayed = true;
        clearAnimationCallbacks();
        mGestureAnimation.registerAnimationCallback(
                new Animatable2.AnimationCallback() {
                    @Override
                    public void onAnimationEnd(Drawable drawable) {
                        super.onAnimationEnd(drawable);
                        mGestureAnimation.start();
                    }
                });
        start(tutorialType);
    }

    private void start(TutorialType tutorialType) {
        // Because the gesture animation has only the right side form.
        // The left side form of the gesture animation is made from flipping the View.
        float rotationY = tutorialType == TutorialType.LEFT_EDGE_BACK_NAVIGATION ? 180f : 0f;
        mHandCoachingView.setRotationY(rotationY);
        mHandCoachingView.setImageDrawable(mGestureAnimation);
        mHandCoachingView.postDelayed(() -> mGestureAnimation.start(),
                ANIMATION_START_DELAY.toMillis());
    }

    private void clearAnimationCallbacks() {
        mGestureAnimation.clearAnimationCallbacks();
    }

    void stop() {
        mIsAnimationPlayed = false;
        clearAnimationCallbacks();
        mGestureAnimation.stop();
    }
}
