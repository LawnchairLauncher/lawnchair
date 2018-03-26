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

import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.AlphaUpdateListener.updateVisibility;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_TRANSLATION;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled;
import static com.android.quickstep.views.LauncherRecentsView.TRANSLATION_X_FACTOR;
import static com.android.quickstep.views.LauncherRecentsView.TRANSLATION_Y_FACTOR;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.os.Build;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.PagedView;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.quickstep.views.LauncherRecentsView;

@TargetApi(Build.VERSION_CODES.O)
public class RecentsViewStateController implements StateHandler {

    private final Launcher mLauncher;
    private final LauncherRecentsView mRecentsView;

    public RecentsViewStateController(Launcher launcher) {
        mLauncher = launcher;
        mRecentsView = launcher.getOverviewPanel();
    }

    @Override
    public void setState(LauncherState state) {
        mRecentsView.setContentAlpha(state.overviewUi ? 1 : 0);
        updateVisibility(mRecentsView, isAccessibilityEnabled(mLauncher));
        float[] translationFactor = state.getOverviewTranslationFactor(mLauncher);
        mRecentsView.setTranslationXFactor(translationFactor[0]);
        mRecentsView.setTranslationYFactor(translationFactor[1]);
        if (state.overviewUi) {
            mRecentsView.resetTaskVisuals();
        }
    }

    @Override
    public void setStateWithAnimation(final LauncherState toState,
            AnimatorSetBuilder builder, AnimationConfig config) {

        // Scroll to the workspace card before changing to the NORMAL state.
        LauncherState fromState = mLauncher.getStateManager().getState();
        int currPage = mRecentsView.getCurrentPage();
        if (fromState.overviewUi && toState == NORMAL && currPage != 0 && !config.userControlled) {
            int maxSnapDuration = PagedView.SLOW_PAGE_SNAP_ANIMATION_DURATION;
            int durationPerPage = maxSnapDuration / 10;
            int snapDuration = Math.min(maxSnapDuration, durationPerPage * currPage);
            mRecentsView.snapToPage(0, snapDuration);
            // Let the snapping animation play for a bit before we translate off screen.
            builder.setStartDelay(snapDuration / 4);
        }

        PropertySetter setter = config.getProperSetter(builder);
        float[] translationFactor = toState.getOverviewTranslationFactor(mLauncher);
        setter.setFloat(mRecentsView, TRANSLATION_X_FACTOR,
                translationFactor[0],
                builder.getInterpolator(ANIM_OVERVIEW_TRANSLATION, LINEAR));
        setter.setFloat(mRecentsView, TRANSLATION_Y_FACTOR,
                translationFactor[1],
                builder.getInterpolator(ANIM_OVERVIEW_TRANSLATION, LINEAR));
        setter.setFloat(mRecentsView, CONTENT_ALPHA, toState.overviewUi ? 1 : 0, LINEAR);

        if (toState.overviewUi) {
            ValueAnimator updateAnim = ValueAnimator.ofFloat(0, 1);
            updateAnim.addUpdateListener(valueAnimator -> {
                // While animating into recents, update the visible task data as needed
                mRecentsView.loadVisibleTaskData();
            });
            updateAnim.setDuration(config.duration);
            builder.play(updateAnim);
        }
    }
}
