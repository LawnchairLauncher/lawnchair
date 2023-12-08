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
package com.android.quickstep.util;

import static com.android.launcher3.LauncherAnimUtils.HOTSEAT_SCALE_PROPERTY_FACTORY;
import static com.android.launcher3.LauncherAnimUtils.SCALE_INDEX_REVEAL_ANIM;
import static com.android.launcher3.LauncherAnimUtils.WORKSPACE_SCALE_PROPERTY_FACTORY;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_SCRIM;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.util.FloatProperty;
import android.view.View;

import com.android.app.animation.Interpolators;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.statehandlers.DepthController;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DynamicResource;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.ResourceProvider;

/**
 * Creates an animation that reveals the workspace.
 * This is used in conjunction with the swipe up to home animation.
 */
public class WorkspaceRevealAnim {

    // Should be used for animations running alongside this WorkspaceRevealAnim.
    public static final int DURATION_MS = 350;
    private static final FloatProperty<Workspace<?>> WORKSPACE_SCALE_PROPERTY =
            WORKSPACE_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_REVEAL_ANIM);

    private static final FloatProperty<Hotseat> HOTSEAT_SCALE_PROPERTY =
            HOTSEAT_SCALE_PROPERTY_FACTORY.get(SCALE_INDEX_REVEAL_ANIM);

    private final float mScaleStart;
    private final AnimatorSet mAnimators = new AnimatorSet();

    public WorkspaceRevealAnim(Launcher launcher, boolean animateOverviewScrim) {
        prepareToAnimate(launcher, animateOverviewScrim);

        ResourceProvider rp = DynamicResource.provider(launcher);
        mScaleStart = rp.getFloat(R.dimen.swipe_up_scale_start);

        Workspace<?> workspace = launcher.getWorkspace();
        workspace.setPivotToScaleWithSelf(launcher.getHotseat());

        // Add reveal animations.
        addRevealAnimatorsForView(workspace, WORKSPACE_SCALE_PROPERTY);
        addRevealAnimatorsForView(launcher.getHotseat(), HOTSEAT_SCALE_PROPERTY);

        // Add overview scrim animation.
        if (animateOverviewScrim) {
            PendingAnimation overviewScrimBuilder = new PendingAnimation(DURATION_MS);
            launcher.getWorkspace().getStateTransitionAnimation()
                    .setScrim(overviewScrimBuilder, NORMAL, new StateAnimationConfig());
            mAnimators.play(overviewScrimBuilder.buildAnim());
        }

        // Add depth controller animation.
        if (launcher instanceof QuickstepLauncher) {
            PendingAnimation depthBuilder = new PendingAnimation(DURATION_MS);
            DepthController depth = ((QuickstepLauncher) launcher).getDepthController();
            depth.setStateWithAnimation(NORMAL, new StateAnimationConfig(), depthBuilder);
            mAnimators.play(depthBuilder.buildAnim());
        }

        // Add sysui scrim animation.
        mAnimators.play(launcher.getRootView().getSysUiScrim()
                .getSysUIMultiplier().animateToValue(0f, 1f));

        mAnimators.setDuration(DURATION_MS);
        mAnimators.setInterpolator(Interpolators.DECELERATED_EASE);
    }

    private <T extends View>  void addRevealAnimatorsForView(T v, FloatProperty<T> scaleProperty) {
        ObjectAnimator scale = ObjectAnimator.ofFloat(v, scaleProperty, mScaleStart, 1f);
        scale.setDuration(DURATION_MS);
        scale.setInterpolator(Interpolators.DECELERATED_EASE);
        mAnimators.play(scale);

        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, View.ALPHA, 0, 1f);
        alpha.setDuration(DURATION_MS);
        alpha.setInterpolator(Interpolators.DECELERATED_EASE);
        mAnimators.play(alpha);

        mAnimators.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scaleProperty.set(v, 1f);
                v.setAlpha(1f);
            }
        });
    }

    /**
     * Setup workspace with 0 duration.
     */
    private void prepareToAnimate(Launcher launcher, boolean animateOverviewScrim) {
        StateAnimationConfig config = new StateAnimationConfig();
        config.animFlags = SKIP_OVERVIEW | SKIP_DEPTH_CONTROLLER | SKIP_SCRIM;
        config.duration = 0;
        // setRecentsAttachedToAppWindow() will animate recents out.
        launcher.getStateManager().createAtomicAnimation(BACKGROUND_APP, NORMAL, config).start();

        // Stop scrolling so that it doesn't interfere with the translation offscreen.
        launcher.<RecentsView>getOverviewPanel().forceFinishScroller();

        if (animateOverviewScrim) {
            launcher.getWorkspace().getStateTransitionAnimation()
                    .setScrim(NO_ANIM_PROPERTY_SETTER, BACKGROUND_APP, config);
        }
    }

    public AnimatorSet getAnimators() {
        return mAnimators;
    }

    public WorkspaceRevealAnim addAnimatorListener(Animator.AnimatorListener listener) {
        mAnimators.addListener(listener);
        return this;
    }

    /**
     * Starts the animation.
     */
    public void start() {
        mAnimators.start();
    }
}
