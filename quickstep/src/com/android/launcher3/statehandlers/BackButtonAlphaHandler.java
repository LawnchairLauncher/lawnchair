/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.launcher3.statehandlers;

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.AnimatedFloat.VALUE;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.UiThreadHelper;
import com.android.quickstep.AnimatedFloat;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SystemUiProxy;

/**
 * State handler for animating back button alpha
 */
public class BackButtonAlphaHandler implements StateHandler<LauncherState> {

    private final BaseQuickstepLauncher mLauncher;
    private final AnimatedFloat mBackAlpha = new AnimatedFloat(this::updateBackAlpha);

    public BackButtonAlphaHandler(BaseQuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void setState(LauncherState state) { }

    @Override
    public void setStateWithAnimation(LauncherState toState, StateAnimationConfig config,
            PendingAnimation animation) {
        if (config.onlyPlayAtomicComponent()) {
            return;
        }

        if (!SysUINavigationMode.getMode(mLauncher).hasGestures) {
            // If the nav mode is not gestural, then force back button alpha to be 1
            UiThreadHelper.setBackButtonAlphaAsync(mLauncher,
                    BaseQuickstepLauncher.SET_BACK_BUTTON_ALPHA, 1f, true /* animate */);
            return;
        }

        mBackAlpha.value = SystemUiProxy.INSTANCE.get(mLauncher).getLastBackButtonAlpha();
        animation.setFloat(mBackAlpha, VALUE,
                mLauncher.shouldBackButtonBeHidden(toState) ? 0 : 1, LINEAR);
    }

    private void updateBackAlpha() {
        UiThreadHelper.setBackButtonAlphaAsync(mLauncher,
                BaseQuickstepLauncher.SET_BACK_BUTTON_ALPHA, mBackAlpha.value, false /* animate */);
    }
}
