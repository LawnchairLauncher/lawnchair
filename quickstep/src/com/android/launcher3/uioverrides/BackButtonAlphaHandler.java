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

package com.android.launcher3.uioverrides;

import android.animation.ValueAnimator;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.util.UiThreadHelper;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SystemUiProxy;

public class BackButtonAlphaHandler implements LauncherStateManager.StateHandler {

    private final BaseQuickstepLauncher mLauncher;

    public BackButtonAlphaHandler(BaseQuickstepLauncher launcher) {
        mLauncher = launcher;
    }

    @Override
    public void setState(LauncherState state) { }

    @Override
    public void setStateWithAnimation(LauncherState toState,
            AnimatorSetBuilder builder, LauncherStateManager.AnimationConfig config) {
        if (config.onlyPlayAtomicComponent()) {
            return;
        }

        if (!SysUINavigationMode.getMode(mLauncher).hasGestures) {
            // If the nav mode is not gestural, then force back button alpha to be 1
            UiThreadHelper.setBackButtonAlphaAsync(mLauncher,
                    BaseQuickstepLauncher.SET_BACK_BUTTON_ALPHA, 1f, true /* animate */);
            return;
        }

        float fromAlpha = SystemUiProxy.INSTANCE.get(mLauncher).getLastBackButtonAlpha();
        float toAlpha = toState.hideBackButton ? 0 : 1;
        if (Float.compare(fromAlpha, toAlpha) != 0) {
            ValueAnimator anim = ValueAnimator.ofFloat(fromAlpha, toAlpha);
            anim.setDuration(config.duration);
            anim.addUpdateListener(valueAnimator -> {
                final float alpha = (float) valueAnimator.getAnimatedValue();
                UiThreadHelper.setBackButtonAlphaAsync(mLauncher,
                        BaseQuickstepLauncher.SET_BACK_BUTTON_ALPHA, alpha, false /* animate */);
            });
            builder.play(anim);
        }
    }
}
