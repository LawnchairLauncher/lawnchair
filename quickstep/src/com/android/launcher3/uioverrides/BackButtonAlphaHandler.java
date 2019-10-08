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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.quickstep.OverviewInteractionState;

public class BackButtonAlphaHandler implements LauncherStateManager.StateHandler {

    private static final String TAG = "BackButtonAlphaHandler";

    private final Launcher mLauncher;
    private final OverviewInteractionState mOverviewInteractionState;

    public BackButtonAlphaHandler(Launcher launcher) {
        mLauncher = launcher;
        mOverviewInteractionState = OverviewInteractionState.INSTANCE.get(mLauncher);
    }

    @Override
    public void setState(LauncherState state) {
        UiFactory.onLauncherStateOrFocusChanged(mLauncher);
    }

    @Override
    public void setStateWithAnimation(LauncherState toState,
            AnimatorSetBuilder builder, LauncherStateManager.AnimationConfig config) {
        if (!config.playNonAtomicComponent()) {
            return;
        }
        float fromAlpha = mOverviewInteractionState.getBackButtonAlpha();
        float toAlpha = toState.hideBackButton ? 0 : 1;
        if (Float.compare(fromAlpha, toAlpha) != 0) {
            ValueAnimator anim = ValueAnimator.ofFloat(fromAlpha, toAlpha);
            anim.setDuration(config.duration);
            anim.addUpdateListener(valueAnimator -> {
                final float alpha = (float) valueAnimator.getAnimatedValue();
                mOverviewInteractionState.setBackButtonAlpha(alpha, false);
            });
            anim.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // Reapply the final alpha in case some state (e.g. window focus) changed.
                    UiFactory.onLauncherStateOrFocusChanged(mLauncher);
                }
            });
            builder.play(anim);
        }
    }
}
