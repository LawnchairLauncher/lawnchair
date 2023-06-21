/*
 * Copyright (C) 2021 The Android Open Source Project
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
import android.animation.ObjectAnimator;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.launcher3.R;

import java.util.ArrayList;


/**
 * Helper View for the gesture tutorial mock taskbar view.
 *
 * This helper class allows animating this mock taskview to and from a mock hotseat and the bottom
 * of the screen.
 */
public class AnimatedTaskbarView extends ConstraintLayout {

    private View mBackground;
    private View mIconContainer;
    private View mAllAppsButton;
    private View mIcon1;
    private View mIcon2;
    private View mIcon3;
    private View mIcon4;
    private View mIcon5;

    @Nullable private Animator mRunningAnimator;

    public AnimatedTaskbarView(@NonNull Context context) {
        super(context);
    }

    public AnimatedTaskbarView(@NonNull Context context,
            @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public AnimatedTaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public AnimatedTaskbarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mBackground = findViewById(R.id.taskbar_background);
        mIconContainer = findViewById(R.id.icon_container);
        mAllAppsButton = findViewById(R.id.taskbar_all_apps);
        mIcon1 = findViewById(R.id.taskbar_icon_1);
        mIcon2 = findViewById(R.id.taskbar_icon_2);
        mIcon3 = findViewById(R.id.taskbar_icon_3);
        mIcon4 = findViewById(R.id.taskbar_icon_4);
        mIcon5 = findViewById(R.id.taskbar_icon_5);
    }

    /**
     * Animates this fake taskbar's disappearance into the given hotseat view.
     */
    public void animateDisappearanceToHotseat(ViewGroup hotseat) {
        ArrayList<Animator> animators = new ArrayList<>();
        int hotseatTop = hotseat.getTop();
        int hotseatLeft = hotseat.getLeft();

        animators.add(ObjectAnimator.ofFloat(mBackground, View.ALPHA, 1f, 0f));
        animators.add(ObjectAnimator.ofFloat(mAllAppsButton, View.ALPHA, 1f, 0f));
        animators.add(createIconDisappearanceToHotseatAnimator(
                mIcon1, hotseat.findViewById(R.id.hotseat_icon_1), hotseatTop, hotseatLeft));
        animators.add(createIconDisappearanceToHotseatAnimator(
                mIcon2, hotseat.findViewById(R.id.hotseat_icon_2), hotseatTop, hotseatLeft));
        animators.add(createIconDisappearanceToHotseatAnimator(
                mIcon3, hotseat.findViewById(R.id.hotseat_icon_3), hotseatTop, hotseatLeft));
        animators.add(createIconDisappearanceToHotseatAnimator(
                mIcon4, hotseat.findViewById(R.id.hotseat_icon_4), hotseatTop, hotseatLeft));
        animators.add(createIconDisappearanceToHotseatAnimator(
                mIcon5, hotseat.findViewById(R.id.hotseat_icon_5), hotseatTop, hotseatLeft));

        AnimatorSet animatorSet = new AnimatorSet();

        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                setVisibility(INVISIBLE);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                setVisibility(VISIBLE);
            }
        });

        start(animatorSet);
    }

    /**
     * Animates this fake taskbar's appearance from the given hotseat view.
     */
    public void animateAppearanceFromHotseat(ViewGroup hotseat) {
        ArrayList<Animator> animators = new ArrayList<>();
        int hotseatTop = hotseat.getTop();
        int hotseatLeft = hotseat.getLeft();

        animators.add(ObjectAnimator.ofFloat(mBackground, View.ALPHA, 0f, 1f));
        animators.add(ObjectAnimator.ofFloat(mAllAppsButton, View.ALPHA, 0f, 1f));
        animators.add(createIconAppearanceFromHotseatAnimator(
                mIcon1, hotseat.findViewById(R.id.hotseat_icon_1), hotseatTop, hotseatLeft));
        animators.add(createIconAppearanceFromHotseatAnimator(
                mIcon2, hotseat.findViewById(R.id.hotseat_icon_2), hotseatTop, hotseatLeft));
        animators.add(createIconAppearanceFromHotseatAnimator(
                mIcon3, hotseat.findViewById(R.id.hotseat_icon_3), hotseatTop, hotseatLeft));
        animators.add(createIconAppearanceFromHotseatAnimator(
                mIcon4, hotseat.findViewById(R.id.hotseat_icon_4), hotseatTop, hotseatLeft));
        animators.add(createIconAppearanceFromHotseatAnimator(
                mIcon5, hotseat.findViewById(R.id.hotseat_icon_5), hotseatTop, hotseatLeft));

        AnimatorSet animatorSet = new AnimatorSet();

        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                setVisibility(VISIBLE);
            }
        });

        start(animatorSet);
    }

    private void start(Animator animator) {
        if (mRunningAnimator != null) {
            mRunningAnimator.cancel();
        }

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                super.onAnimationCancel(animation);
                mRunningAnimator = null;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                mRunningAnimator = null;
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                mRunningAnimator = animator;
            }
        });

        animator.start();
    }

    private Animator createIconDisappearanceToHotseatAnimator(
            View taskbarIcon, View hotseatIcon, int hotseatTop, int hotseatLeft) {
        ArrayList<Animator> animators = new ArrayList<>();

        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.TRANSLATION_Y,
                0,
                (hotseatTop + hotseatIcon.getTop()) - (getTop() + taskbarIcon.getTop())));
        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.TRANSLATION_X,
                0,
                (hotseatLeft + hotseatIcon.getLeft()) - (getLeft() + taskbarIcon.getLeft())));
        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.SCALE_X,
                1f,
                (float) hotseatIcon.getWidth() / (float) taskbarIcon.getWidth()));
        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.SCALE_Y,
                1f,
                (float) hotseatIcon.getHeight() / (float) taskbarIcon.getHeight()));
        animators.add(ObjectAnimator.ofFloat(taskbarIcon, View.ALPHA, 1f, 0f));

        AnimatorSet animatorSet = new AnimatorSet();

        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                taskbarIcon.setVisibility(INVISIBLE);
            }

            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                taskbarIcon.setVisibility(VISIBLE);
            }
        });

        return animatorSet;
    }

    private Animator createIconAppearanceFromHotseatAnimator(
            View taskbarIcon, View hotseatIcon, int hotseatTop, int hotseatLeft) {
        ArrayList<Animator> animators = new ArrayList<>();

        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.TRANSLATION_Y,
                (hotseatTop + hotseatIcon.getTop()) - (getTop() + taskbarIcon.getTop()),
                0));
        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.TRANSLATION_X,
                (hotseatLeft + hotseatIcon.getLeft()) - (getLeft() + taskbarIcon.getLeft()),
                0));
        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.SCALE_X,
                (float) hotseatIcon.getWidth() / (float) taskbarIcon.getWidth(),
                1f));
        animators.add(ObjectAnimator.ofFloat(
                taskbarIcon,
                View.SCALE_Y,
                (float) hotseatIcon.getHeight() / (float) taskbarIcon.getHeight(),
                1f));
        animators.add(ObjectAnimator.ofFloat(taskbarIcon, View.ALPHA, 0f, 1f));

        AnimatorSet animatorSet = new AnimatorSet();

        animatorSet.playTogether(animators);
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                super.onAnimationStart(animation);
                taskbarIcon.setVisibility(VISIBLE);
            }
        });

        return animatorSet;
    }
}
