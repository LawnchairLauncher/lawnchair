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

import androidx.annotation.Nullable;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.MultiValueAlpha;

/**
 * StateHandler to animate Taskbar according to Launcher's state machine. Does nothing if Taskbar
 * isn't present (i.e. {@link #setAnimationController} is never called).
 */
public class TaskbarStateHandler implements StateManager.StateHandler<LauncherState> {

    private final BaseQuickstepLauncher mLauncher;

    // Contains Taskbar-related properties we should aniamte. If null, don't do anything.
    private @Nullable MultiValueAlpha.AlphaProperty mTaskbarAlpha = null;

    public TaskbarStateHandler(BaseQuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    public void setAnimationController(MultiValueAlpha.AlphaProperty taskbarAlpha) {
        mTaskbarAlpha = taskbarAlpha;
    }

    @Override
    public void setState(LauncherState state) {
        setState(state, PropertySetter.NO_ANIM_PROPERTY_SETTER);
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
        if (mTaskbarAlpha == null) {
            return;
        }

        boolean isTaskbarVisible = (toState.getVisibleElements(mLauncher) & TASKBAR) != 0;
        setter.setFloat(mTaskbarAlpha, MultiValueAlpha.VALUE, isTaskbarVisible ? 1f : 0f, LINEAR);
    }
}
