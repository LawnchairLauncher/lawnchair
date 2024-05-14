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

import static android.view.View.VISIBLE;

import static com.android.launcher3.taskbar.bubbles.BubbleBarController.isBubbleBarEnabled;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_EXPANDED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED;
import static com.android.wm.shell.common.bubbles.BubbleConstants.BUBBLE_EXPANDED_SCRIM_ALPHA;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE;

import android.animation.ObjectAnimator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.util.DisplayController;
import com.android.quickstep.SystemUiProxy;
import com.android.systemui.shared.system.QuickStepContract.SystemUiStateFlags;

import java.io.PrintWriter;

/**
 * Handles properties/data collection, and passes the results to {@link TaskbarScrimView} to render.
 */
public class TaskbarScrimViewController implements TaskbarControllers.LoggableTaskbarController,
        TaskbarControllers.BackgroundRendererController {

    private static final Interpolator SCRIM_ALPHA_IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    private static final Interpolator SCRIM_ALPHA_OUT = new PathInterpolator(0f, 0f, 0.8f, 1f);

    private final TaskbarActivityContext mActivity;
    private final TaskbarScrimView mScrimView;
    private boolean mTaskbarVisible;
    @SystemUiStateFlags
    private long mSysUiStateFlags;

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
     * Called when the taskbar visibility changes.
     *
     * @param visibility the current visibility of {@link TaskbarView}.
     */
    public void onTaskbarVisibilityChanged(int visibility) {
        mTaskbarVisible = visibility == VISIBLE;
        if (shouldShowScrim()) {
            showScrim(true, getScrimAlpha(), false /* skipAnim */);
        } else if (mScrimView.getScrimAlpha() > 0f) {
            showScrim(false, 0, false /* skipAnim */);
        }
    }

    /**
     * Updates the scrim state based on the flags.
     */
    public void updateStateForSysuiFlags(@SystemUiStateFlags long stateFlags, boolean skipAnim) {
        if (isBubbleBarEnabled() && DisplayController.isTransientTaskbar(mActivity)) {
            // These scrims aren't used if bubble bar & transient taskbar are active.
            return;
        }
        mSysUiStateFlags = stateFlags;
        showScrim(shouldShowScrim(), getScrimAlpha(), skipAnim);
    }

    private boolean shouldShowScrim() {
        final boolean bubblesExpanded = (mSysUiStateFlags & SYSUI_STATE_BUBBLES_EXPANDED) != 0;
        boolean isShadeVisible = (mSysUiStateFlags & SYSUI_STATE_NOTIFICATION_PANEL_VISIBLE) != 0;
        return bubblesExpanded && !mControllers.navbarButtonsViewController.isImeVisible()
                && !isShadeVisible
                && !mControllers.taskbarStashController.isStashed()
                && mTaskbarVisible;
    }

    private float getScrimAlpha() {
        final boolean manageMenuExpanded =
                (mSysUiStateFlags & SYSUI_STATE_BUBBLES_MANAGE_MENU_EXPANDED) != 0;
        return manageMenuExpanded
                // When manage menu shows there's the first scrim and second scrim so figure out
                // what the total transparency would be.
                ? (BUBBLE_EXPANDED_SCRIM_ALPHA + (BUBBLE_EXPANDED_SCRIM_ALPHA
                * (1 - BUBBLE_EXPANDED_SCRIM_ALPHA)))
                : shouldShowScrim() ? BUBBLE_EXPANDED_SCRIM_ALPHA : 0;
    }

    private void showScrim(boolean showScrim, float alpha, boolean skipAnim) {
        mScrimView.setOnClickListener(showScrim ? (view) -> onClick() : null);
        mScrimView.setClickable(showScrim);
        if (skipAnim) {
            mScrimView.setScrimAlpha(alpha);
        } else {
            ObjectAnimator anim = mScrimAlpha.animateToValue(showScrim ? alpha : 0);
            anim.setInterpolator(showScrim ? SCRIM_ALPHA_IN : SCRIM_ALPHA_OUT);
            anim.start();
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
