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
package com.android.launcher3.taskbar;

import static com.android.launcher3.taskbar.bubbles.BubbleBarController.BUBBLE_BAR_ENABLED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED;

import android.animation.ObjectAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.SystemUiProxy;

import java.io.PrintWriter;

/**
 * Handles properties/data collection, and passes the results to {@link TaskbarScrimView} to render.
 */
public class TaskbarScrimViewController implements TaskbarControllers.LoggableTaskbarController,
        TaskbarControllers.BackgroundRendererController {

    private static final float SCRIM_ALPHA = 0.6f;

    private static final Interpolator SCRIM_ALPHA_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    private static final Interpolator SCRIM_ALPHA_OUT = new PathInterpolator(0f, 0f, 0.8f, 1f);

    private final TaskbarActivityContext mActivity;
    private final TaskbarScrimView mScrimView;

    // Alpha property for the scrim.
    private final AnimatedFloat mScrimAlpha = new AnimatedFloat(this::updateScrimAlpha);

    // Initialized in init.
    private TaskbarControllers mControllers;

    public TaskbarScrimViewController(TaskbarActivityContext activity, TaskbarScrimView scrimView) {
        mActivity = activity;
        mScrimView = scrimView;
    }

    /**
     * Initializes the controller
     */
    public void init(TaskbarControllers controllers) {
        mControllers = controllers;
    }

    /**
     * Updates the scrim state based on the flags.
     */
    public void updateStateForSysuiFlags(int stateFlags, boolean skipAnim) {
        if (BUBBLE_BAR_ENABLED && DisplayController.isTransientTaskbar(mActivity)) {
            // These scrims aren't used if bubble bar & transient taskbar are active.
            return;
        }
        final boolean bubblesExpanded = (stateFlags & SYSUI_STATE_BUBBLES_EXPANDED) != 0;
        final boolean manageMenuExpanded =
                (stateFlags & SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED) != 0;
        final boolean showScrim = !mControllers.navbarButtonsViewController.isImeVisible()
                && bubblesExpanded
                && mControllers.taskbarStashController.isTaskbarVisibleAndNotStashing();
        final float scrimAlpha = manageMenuExpanded
                // When manage menu shows there's the first scrim and second scrim so figure out
                // what the total transparency would be.
                ? (SCRIM_ALPHA + (SCRIM_ALPHA * (1 - SCRIM_ALPHA)))
                : showScrim ? SCRIM_ALPHA : 0;
        showScrim(showScrim, scrimAlpha, skipAnim);
    }

    private void showScrim(boolean showScrim, float alpha, boolean skipAnim) {
        mScrimView.setOnClickListener(showScrim ? (view) -> onClick() : null);
        mScrimView.setClickable(showScrim);
        ObjectAnimator anim = mScrimAlpha.animateToValue(showScrim ? alpha : 0);
        anim.setInterpolator(showScrim ? SCRIM_ALPHA_IN : SCRIM_ALPHA_OUT);
        anim.start();
        if (skipAnim) {
            anim.end();
        }
    }

    private void updateScrimAlpha() {
        mScrimView.setScrimAlpha(mScrimAlpha.value);
    }

    private void onClick() {
        SystemUiProxy.INSTANCE.get(mActivity).onBackPressed();
    }

    @Override
    public void setCornerRoundness(float cornerRoundness) {
        mScrimView.setCornerRoundness(cornerRoundness);
    }

    @Override
    public void dumpLogs(String prefix, PrintWriter pw) {
        pw.println(prefix + "TaskbarScrimViewController:");

        pw.println(prefix + "\tmScrimAlpha.value=" + mScrimAlpha.value);
    }
}
