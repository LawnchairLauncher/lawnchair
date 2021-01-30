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

import static com.android.launcher3.LauncherState.TASKBAR;

import android.animation.Animator;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.SystemUiProxy;
import com.android.systemui.shared.system.QuickStepContract;

/**
 * Works with TaskbarController to update the TaskbarView's alpha based on LauncherState, whether
 * Launcher is in the foreground, etc.
 */
public class TaskbarVisibilityController {

    private static final long IME_VISIBILITY_ALPHA_DURATION = 120;

    private final BaseQuickstepLauncher mLauncher;
    private final TaskbarController.TaskbarVisibilityControllerCallbacks mTaskbarCallbacks;

    // Background alpha.
    private AnimatedFloat mTaskbarBackgroundAlpha = new AnimatedFloat(
            this::onTaskbarBackgroundAlphaChanged);

    // Overall visibility.
    private AnimatedFloat mTaskbarVisibilityAlphaForLauncherState = new AnimatedFloat(
            this::updateVisibilityAlpha);
    private AnimatedFloat mTaskbarVisibilityAlphaForIme = new AnimatedFloat(
            this::updateVisibilityAlpha);

    public TaskbarVisibilityController(BaseQuickstepLauncher launcher,
            TaskbarController.TaskbarVisibilityControllerCallbacks taskbarCallbacks) {
        mLauncher = launcher;
        mTaskbarCallbacks = taskbarCallbacks;
    }

    protected void init() {
        mTaskbarBackgroundAlpha.updateValue(mLauncher.hasBeenResumed() ? 0f : 1f);
        boolean isVisibleForLauncherState = (mLauncher.getStateManager().getState()
                .getVisibleElements(mLauncher) & TASKBAR) != 0;
        mTaskbarVisibilityAlphaForLauncherState.updateValue(isVisibleForLauncherState ? 1f : 0f);
        boolean isImeVisible = (SystemUiProxy.INSTANCE.get(mLauncher).getLastSystemUiStateFlags()
                & QuickStepContract.SYSUI_STATE_IME_SHOWING) != 0;
        mTaskbarVisibilityAlphaForIme.updateValue(isImeVisible ? 0f : 1f);
    }

    protected void cleanup() {
    }

    protected AnimatedFloat getTaskbarVisibilityForLauncherState() {
        return mTaskbarVisibilityAlphaForLauncherState;
    }

    protected Animator createAnimToBackgroundAlpha(float toAlpha, long duration) {
        return mTaskbarBackgroundAlpha.animateToValue(mTaskbarBackgroundAlpha.value, toAlpha)
                .setDuration(duration);
    }

    protected void animateToVisibilityForIme(float toAlpha) {
        mTaskbarVisibilityAlphaForIme.animateToValue(mTaskbarVisibilityAlphaForIme.value, toAlpha)
                .setDuration(IME_VISIBILITY_ALPHA_DURATION).start();
    }

    private void onTaskbarBackgroundAlphaChanged() {
        mTaskbarCallbacks.updateTaskbarBackgroundAlpha(mTaskbarBackgroundAlpha.value);
        updateVisibilityAlpha();
    }

    private void updateVisibilityAlpha() {
        // We use mTaskbarBackgroundAlpha as a proxy for whether Launcher is resumed/paused, the
        // assumption being that Taskbar should always be visible regardless of the current
        // LauncherState if Launcher is paused.
        float alphaDueToLauncher = Math.max(mTaskbarBackgroundAlpha.value,
                mTaskbarVisibilityAlphaForLauncherState.value);
        float alphaDueToOther = mTaskbarVisibilityAlphaForIme.value;
        mTaskbarCallbacks.updateTaskbarVisibilityAlpha(alphaDueToLauncher * alphaDueToOther);
    }
}
