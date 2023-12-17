/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static com.android.app.animation.Interpolators.AGGRESSIVE_EASE_IN_OUT;
import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.LauncherState.QUICK_SWITCH_FROM_HOME;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_MODAL;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.RECENTS_GRID_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;
import static com.android.quickstep.views.RecentsView.TASK_THUMBNAIL_SPLASH_ALPHA;

import android.util.FloatProperty;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.quickstep.views.RecentsView;

/**
 * State handler for recents view. Manages UI changes and animations for recents view based off the
 * current {@link LauncherState}.
 *
 * @param <T> the recents view
 */
public abstract class BaseRecentsViewStateController<T extends RecentsView>
        implements StateHandler<LauncherState> {
    protected final T mRecentsView;
    protected final QuickstepLauncher mLauncher;

    public BaseRecentsViewStateController(@NonNull QuickstepLauncher launcher) {
        mLauncher = launcher;
        mRecentsView = launcher.getOverviewPanel();
    }

    @Override
    public void setState(@NonNull LauncherState state) {
        float[] scaleAndOffset = state.getOverviewScaleAndOffset(mLauncher);
        RECENTS_SCALE_PROPERTY.set(mRecentsView, scaleAndOffset[0]);
        ADJACENT_PAGE_HORIZONTAL_OFFSET.set(mRecentsView, scaleAndOffset[1]);
        TASK_SECONDARY_TRANSLATION.set(mRecentsView, 0f);

        getContentAlphaProperty().set(mRecentsView, state.overviewUi ? 1f : 0);
        getTaskModalnessProperty().set(mRecentsView, state.getOverviewModalness());
        RECENTS_GRID_PROGRESS.set(mRecentsView,
                state.displayOverviewTasksAsGrid(mLauncher.getDeviceProfile()) ? 1f : 0f);
        TASK_THUMBNAIL_SPLASH_ALPHA.set(mRecentsView, state.showTaskThumbnailSplash() ? 1f : 0f);
    }

    @Override
    public void setStateWithAnimation(LauncherState toState, StateAnimationConfig config,
            PendingAnimation builder) {
        if (config.hasAnimationFlag(SKIP_OVERVIEW)) {
            return;
        }
        setStateWithAnimationInternal(toState, config, builder);
        builder.addEndListener(success -> {
            if (!success) {
                mRecentsView.reset();
            }
        });
    }

    /**
     * Core logic for animating the recents view UI.
     *
     * @param toState state to animate to
     * @param config current animation config
     * @param setter animator set builder
     */
    void setStateWithAnimationInternal(@NonNull final LauncherState toState,
            @NonNull StateAnimationConfig config, @NonNull PendingAnimation setter) {
        float[] scaleAndOffset = toState.getOverviewScaleAndOffset(mLauncher);
        setter.setFloat(mRecentsView, RECENTS_SCALE_PROPERTY, scaleAndOffset[0],
                config.getInterpolator(ANIM_OVERVIEW_SCALE, LINEAR));
        setter.setFloat(mRecentsView, ADJACENT_PAGE_HORIZONTAL_OFFSET, scaleAndOffset[1],
                config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_X, LINEAR));
        setter.setFloat(mRecentsView, TASK_SECONDARY_TRANSLATION, 0f,
                config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, LINEAR));

        boolean exitingOverview = !FeatureFlags.ENABLE_SPLIT_FROM_WORKSPACE_TO_WORKSPACE.get()
                && !toState.overviewUi;
        if (mRecentsView.isSplitSelectionActive() && exitingOverview) {
            setter.add(mRecentsView.getSplitSelectController().getSplitAnimationController()
                    .createPlaceholderDismissAnim(mLauncher));
            setter.setViewAlpha(
                    mRecentsView.getSplitInstructionsView(),
                    0,
                    config.getInterpolator(
                            ANIM_OVERVIEW_SPLIT_SELECT_INSTRUCTIONS_FADE,
                            LINEAR
                    )
            );
        }

        setter.setFloat(mRecentsView, getContentAlphaProperty(), toState.overviewUi ? 1 : 0,
                config.getInterpolator(ANIM_OVERVIEW_FADE, AGGRESSIVE_EASE_IN_OUT));

        setter.setFloat(
                mRecentsView, getTaskModalnessProperty(),
                toState.getOverviewModalness(),
                config.getInterpolator(ANIM_OVERVIEW_MODAL, LINEAR));

        LauncherState fromState = mLauncher.getStateManager().getState();
        setter.setFloat(mRecentsView, TASK_THUMBNAIL_SPLASH_ALPHA,
                toState.showTaskThumbnailSplash() ? 1f : 0f,
                !toState.showTaskThumbnailSplash() && fromState == QUICK_SWITCH_FROM_HOME
                        ? LINEAR : INSTANT);

        boolean showAsGrid = toState.displayOverviewTasksAsGrid(mLauncher.getDeviceProfile());
        Interpolator gridProgressInterpolator = showAsGrid
                ? fromState == QUICK_SWITCH_FROM_HOME ? LINEAR : INSTANT
                : FINAL_FRAME;
        setter.setFloat(mRecentsView, RECENTS_GRID_PROGRESS, showAsGrid ? 1f : 0f,
                gridProgressInterpolator);
    }

    abstract FloatProperty getTaskModalnessProperty();

    /**
     * Get property for content alpha for the recents view.
     *
     * @return the float property for the view's content alpha
     */
    abstract FloatProperty getContentAlphaProperty();
}
