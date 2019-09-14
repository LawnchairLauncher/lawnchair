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

package com.android.launcher3;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.TaskViewUtils.findTaskViewToLaunch;
import static com.android.quickstep.TaskViewUtils.getRecentsWindowAnimator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.view.View;

import com.android.launcher3.anim.AnimatorPlaybackController;
import com.android.launcher3.anim.Interpolators;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.quickstep.util.ClipAnimationHelper;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.TaskView;
import com.android.systemui.shared.system.RemoteAnimationTargetCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A {@link QuickstepAppTransitionManagerImpl} that also implements recents transitions from
 * {@link RecentsView}.
 */
public final class LauncherAppTransitionManagerImpl extends QuickstepAppTransitionManagerImpl {

    public static final int INDEX_SHELF_ANIM = 0;
    public static final int INDEX_RECENTS_FADE_ANIM = 1;
    public static final int INDEX_RECENTS_TRANSLATE_X_ANIM = 2;

    public LauncherAppTransitionManagerImpl(Context context) {
        super(context);
    }

    @Override
    protected boolean isLaunchingFromRecents(@NonNull View v,
            @Nullable RemoteAnimationTargetCompat[] targets) {
        return mLauncher.getStateManager().getState().overviewUi
                && findTaskViewToLaunch(mLauncher, v, targets) != null;
    }

    @Override
    protected void composeRecentsLaunchAnimator(@NonNull AnimatorSet anim, @NonNull View v,
            @NonNull RemoteAnimationTargetCompat[] targets, boolean launcherClosing) {
        RecentsView recentsView = mLauncher.getOverviewPanel();
        boolean skipLauncherChanges = !launcherClosing;

        TaskView taskView = findTaskViewToLaunch(mLauncher, v, targets);

        ClipAnimationHelper helper = new ClipAnimationHelper(mLauncher);
        anim.play(getRecentsWindowAnimator(taskView, skipLauncherChanges, targets, helper)
                .setDuration(RECENTS_LAUNCH_DURATION));

        Animator childStateAnimation = null;
        // Found a visible recents task that matches the opening app, lets launch the app from there
        Animator launcherAnim;
        final AnimatorListenerAdapter windowAnimEndListener;
        if (launcherClosing) {
            launcherAnim = recentsView.createAdjacentPageAnimForTaskLaunch(taskView, helper);
            launcherAnim.setInterpolator(Interpolators.TOUCH_RESPONSE_INTERPOLATOR);
            launcherAnim.setDuration(RECENTS_LAUNCH_DURATION);

            // Make sure recents gets fixed up by resetting task alphas and scales, etc.
            windowAnimEndListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLauncher.getStateManager().moveToRestState();
                    mLauncher.getStateManager().reapplyState();
                }
            };
        } else {
            AnimatorPlaybackController controller =
                    mLauncher.getStateManager().createAnimationToNewWorkspace(NORMAL,
                            RECENTS_LAUNCH_DURATION);
            controller.dispatchOnStart();
            childStateAnimation = controller.getTarget();
            launcherAnim = controller.getAnimationPlayer().setDuration(RECENTS_LAUNCH_DURATION);
            windowAnimEndListener = new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLauncher.getStateManager().goToState(NORMAL, false);
                }
            };
        }
        anim.play(launcherAnim);

        // Set the current animation first, before adding windowAnimEndListener. Setting current
        // animation adds some listeners which need to be called before windowAnimEndListener
        // (the ordering of listeners matter in this case).
        mLauncher.getStateManager().setCurrentAnimation(anim, childStateAnimation);
        anim.addListener(windowAnimEndListener);
    }

    @Override
    protected Runnable composeViewContentAnimator(@NonNull AnimatorSet anim, float[] alphas,
            float[] trans) {
        RecentsView overview = mLauncher.getOverviewPanel();
        ObjectAnimator alpha = ObjectAnimator.ofFloat(overview,
                RecentsView.CONTENT_ALPHA, alphas);
        alpha.setDuration(CONTENT_ALPHA_DURATION);
        alpha.setInterpolator(LINEAR);
        anim.play(alpha);
        overview.setFreezeViewVisibility(true);

        ObjectAnimator transY = ObjectAnimator.ofFloat(overview, View.TRANSLATION_Y, trans);
        transY.setInterpolator(AGGRESSIVE_EASE);
        transY.setDuration(CONTENT_TRANSLATION_DURATION);
        anim.play(transY);

        return () -> {
            overview.setFreezeViewVisibility(false);
            mLauncher.getStateManager().reapplyState();
        };
    }

    @Override
    public int getStateElementAnimationsCount() {
        return 3;
    }

    @Override
    public Animator createStateElementAnimation(int index, float... values) {
        switch (index) {
            case INDEX_SHELF_ANIM:
                return mLauncher.getAllAppsController().createSpringAnimation(values);
            case INDEX_RECENTS_FADE_ANIM:
                return ObjectAnimator.ofFloat(mLauncher.getOverviewPanel(),
                        RecentsView.CONTENT_ALPHA, values);
            case INDEX_RECENTS_TRANSLATE_X_ANIM:
                return new SpringAnimationBuilder<>(mLauncher.getOverviewPanel(), VIEW_TRANSLATE_X)
                        .setDampingRatio(0.8f)
                        .setStiffness(250)
                        .setValues(values)
                        .build(mLauncher);
            default:
                return super.createStateElementAnimation(index, values);
        }
    }
}
