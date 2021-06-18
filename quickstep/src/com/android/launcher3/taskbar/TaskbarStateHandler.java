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
import static com.android.launcher3.anim.Interpolators.LINEAR;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.SystemUiProxy;

/**
 * StateHandler to animate Taskbar according to Launcher's state machine.
 */
public class TaskbarStateHandler implements StateManager.StateHandler<LauncherState> {

    private final BaseQuickstepLauncher mLauncher;

    private AnimatedFloat mNavbarButtonAlpha = new AnimatedFloat(this::updateNavbarButtonAlpha);

    public TaskbarStateHandler(BaseQuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void setState(LauncherState state) {
        setState(state, PropertySetter.NO_ANIM_PROPERTY_SETTER);
        // Force update the alpha in case it was not initialized properly
        updateNavbarButtonAlpha();
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, StateAnimationConfig config,
            PendingAnimation animation) {
        setState(toState, animation);
    }

    /**
     * Sets the provided state
     */
    public void setState(LauncherState toState, PropertySetter setter) {
        boolean isTaskbarVisible = (toState.getVisibleElements(mLauncher) & TASKBAR) != 0;
        // Make the nav bar visible in states that taskbar isn't visible.
        // TODO: We should draw our own handle instead of showing the nav bar.
        float navbarButtonAlpha = isTaskbarVisible ? 0f : 1f;
        setter.setFloat(mNavbarButtonAlpha, AnimatedFloat.VALUE, navbarButtonAlpha, LINEAR);
    }


    private void updateNavbarButtonAlpha() {
        SystemUiProxy.INSTANCE.get(mLauncher).setNavBarButtonAlpha(mNavbarButtonAlpha.value, false);
    }
}
