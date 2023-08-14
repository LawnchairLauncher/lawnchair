/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import static com.android.launcher3.anim.AnimatedFloat.VALUE;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;

import androidx.annotation.Nullable;
import androidx.dynamicanimation.animation.SpringForce;

import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.taskbar.TaskbarControllers.LoggableTaskbarController;
import com.android.launcher3.util.DisplayController;

import java.io.PrintWriter;

/**
 * Manages the spring animation when stashing the transient taskbar.
 */
public class TaskbarSpringOnStashController implements LoggableTaskbarController {

    private final TaskbarActivityContext mContext;
    private TaskbarControllers mControllers;
    private final AnimatedFloat mTranslationForStash = new AnimatedFloat(
            this::updateTranslationYForStash);

    private final boolean mIsTransientTaskbar;

    private final float mStartVelocityPxPerS;

    public TaskbarSpringOnStashController(TaskbarActivityContext context) {
        mContext = context;
        mIsTransientTaskbar = DisplayController.isTransientTaskbar(mContext);
        mStartVelocityPxPerS = context.getResources()
                .getDimension(R.dimen.transient_taskbar_stash_spring_velocity_dp_per_s);
    }

    /**
     * Initialization method.
     */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    private void updateTranslationYForStash() {
        if (!mIsTransientTaskbar) {
            return;
        }

        float transY = mTranslationForStash.value;
        mControllers.stashedHandleViewController.setTranslationYForStash(transY);
        mControllers.taskbarViewController.setTranslationYForStash(transY);
        mControllers.taskbarDragLayerController.setTranslationYForStash(transY);
    }

    /**
     * Returns a spring animation to be used when stashing the transient taskbar.
     */
    public @Nullable ValueAnimator createSpringToStash() {
        if (!mIsTransientTaskbar) {
            return null;
        }
        return new SpringAnimationBuilder(mContext)
                .setStartValue(mTranslationForStash.value)
                .setEndValue(0)
                .setDampingRatio(SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY)
                .setStiffness(SpringForce.STIFFNESS_LOW)
                .setStartVelocity(mStartVelocityPxPerS)
                .build(mTranslationForStash, VALUE);
    }

    /**
     * Returns an animation to reset the stash translation back to 0 when unstashing.
     */
    public @Nullable ObjectAnimator createResetAnimForUnstash() {
        if (!mIsTransientTaskbar) {
            return null;
        }
        return mTranslationForStash.animateToValue(0);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarSpringOnStashController:");

        pw.println(prefix + "\tmTranslationForStash=" + mTranslationForStash.value);
        pw.println(prefix + "\tmStartVelocityPxPerS=" + mStartVelocityPxPerS);
    }
}

