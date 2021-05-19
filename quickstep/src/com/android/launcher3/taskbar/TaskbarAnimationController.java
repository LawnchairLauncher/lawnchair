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
import com.android.launcher3.Utilities;
import com.android.launcher3.taskbar.LauncherTaskbarUIController.TaskbarAnimationControllerCallbacks;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.SystemUiProxy;
import com.android.systemui.shared.system.QuickStepContract;

/**
 * Works with TaskbarController to update the TaskbarView's visual properties based on factors such
 * as LauncherState, whether Launcher is in the foreground, etc.
 */
public class TaskbarAnimationController {

    private static final long IME_VISIBILITY_ALPHA_DURATION = 120;

    private final BaseQuickstepLauncher mLauncher;
    private final TaskbarAnimationControllerCallbacks mTaskbarCallbacks;

    // Background alpha.
    private final AnimatedFloat mTaskbarBackgroundAlpha = new AnimatedFloat(
            this::onTaskbarBackgroundAlphaChanged);

    // Overall visibility.
    private final AnimatedFloat mTaskbarVisibilityAlphaForLauncherState = new AnimatedFloat(
            this::updateVisibilityAlpha);
    private final AnimatedFloat mTaskbarVisibilityAlphaForIme = new AnimatedFloat(
            this::updateVisibilityAlphaForIme);

    // Scale.
    private final AnimatedFloat mTaskbarScaleForLauncherState = new AnimatedFloat(
            this::updateScale);

    // TranslationY.
    private final AnimatedFloat mTaskbarTranslationYForLauncherState = new AnimatedFloat(
            this::updateTranslationY);

    public TaskbarAnimationController(BaseQuickstepLauncher launcher,
            TaskbarAnimationControllerCallbacks taskbarCallbacks) {
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

        onTaskbarBackgroundAlphaChanged();
        updateVisibilityAlpha();
    }

    protected void cleanup() {
        setNavBarButtonAlpha(1f);
    }

    protected AnimatedFloat getTaskbarVisibilityForLauncherState() {
        return mTaskbarVisibilityAlphaForLauncherState;
    }

    protected AnimatedFloat getTaskbarScaleForLauncherState() {
        return mTaskbarScaleForLauncherState;
    }

    protected AnimatedFloat getTaskbarTranslationYForLauncherState() {
        return mTaskbarTranslationYForLauncherState;
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
        updateScale();
        updateTranslationY();
    }

    private void updateVisibilityAlpha() {
        // We use mTaskbarBackgroundAlpha as a proxy for whether Launcher is resumed/paused, the
        // assumption being that Taskbar should always be visible regardless of the current
        // LauncherState if Launcher is paused.
        float alphaDueToIme = mTaskbarVisibilityAlphaForIme.value;
        float alphaDueToLauncher = Math.max(mTaskbarBackgroundAlpha.value,
                mTaskbarVisibilityAlphaForLauncherState.value);
        float taskbarAlpha = alphaDueToLauncher * alphaDueToIme;
        mTaskbarCallbacks.updateTaskbarVisibilityAlpha(taskbarAlpha);

        // Make the nav bar invisible if taskbar is visible.
        setNavBarButtonAlpha(1f - taskbarAlpha);
    }

    private void updateVisibilityAlphaForIme() {
        updateVisibilityAlpha();
        float taskbarAlphaDueToIme = mTaskbarVisibilityAlphaForIme.value;
        mTaskbarCallbacks.updateImeBarVisibilityAlpha(1f - taskbarAlphaDueToIme);
    }

    private void updateScale() {
        // We use mTaskbarBackgroundAlpha as a proxy for whether Launcher is resumed/paused, the
        // assumption being that Taskbar should always be at scale 1f regardless of the current
        // LauncherState if Launcher is paused.
        float scale = mTaskbarScaleForLauncherState.value;
        scale = Utilities.mapRange(mTaskbarBackgroundAlpha.value, scale, 1f);
        mTaskbarCallbacks.updateTaskbarScale(scale);
    }

    private void updateTranslationY() {
        // We use mTaskbarBackgroundAlpha as a proxy for whether Launcher is resumed/paused, the
        // assumption being that Taskbar should always be at translationY 0f regardless of the
        // current LauncherState if Launcher is paused.
        float translationY = mTaskbarTranslationYForLauncherState.value;
        translationY = Utilities.mapRange(mTaskbarBackgroundAlpha.value, translationY, 0f);
        mTaskbarCallbacks.updateTaskbarTranslationY(translationY);
    }

    private void setNavBarButtonAlpha(float navBarAlpha) {
        SystemUiProxy.INSTANCE.get(mLauncher).setNavBarButtonAlpha(navBarAlpha, false);
    }
}
