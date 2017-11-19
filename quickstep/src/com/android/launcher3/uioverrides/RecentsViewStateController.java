/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.launcher3.uioverrides;

import static com.android.launcher3.WorkspaceStateTransitionAnimation.NO_ANIM_PROPERTY_SETTER;

import android.animation.AnimatorSet;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.WorkspaceStateTransitionAnimation.AnimatedPropertySetter;
import com.android.launcher3.WorkspaceStateTransitionAnimation.PropertySetter;
import com.android.launcher3.anim.AnimationLayerSet;

public class RecentsViewStateController implements StateHandler {

    private final Launcher mLauncher;

    public RecentsViewStateController(Launcher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void setState(LauncherState state) {
        setState(state, NO_ANIM_PROPERTY_SETTER);
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, AnimationLayerSet layerViews,
            AnimatorSet anim, AnimationConfig config) {
        setState(toState, new AnimatedPropertySetter(config.duration, layerViews, anim));
    }

    private void setState(LauncherState state, PropertySetter setter) {
        setter.setViewAlpha(null, mLauncher.getOverviewPanel(),
                state == LauncherState.OVERVIEW ? 1 : 0);
    }
}
