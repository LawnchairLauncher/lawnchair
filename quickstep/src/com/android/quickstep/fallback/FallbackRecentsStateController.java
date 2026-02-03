/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.fallback;

import static com.android.app.animation.Interpolators.FINAL_FRAME;
import static com.android.app.animation.Interpolators.INSTANT;
import static com.android.app.animation.Interpolators.LINEAR;
import static com.android.launcher3.Flags.enableDesktopExplodedView;
import static com.android.launcher3.util.OverviewReleaseFlags.enableGridOnlyOverview;
import static com.android.launcher3.Flags.enableLargeDesktopWindowingTile;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_MODAL;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_SCRIM_FADE;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW;
import static com.android.quickstep.fallback.RecentsState.OVERVIEW_SPLIT_SELECT;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_HORIZONTAL_OFFSET;
import static com.android.quickstep.views.RecentsView.DESKTOP_CAROUSEL_DETACH_PROGRESS;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_GRID_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_MODALNESS;
import static com.android.quickstep.views.RecentsView.TASK_PRIMARY_SPLIT_TRANSLATION;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_SPLIT_TRANSLATION;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;
import static com.android.quickstep.views.RecentsView.TASK_THUMBNAIL_SPLASH_ALPHA;
import static com.android.quickstep.views.RecentsViewUtils.DESK_EXPLODE_PROGRESS;
import static com.android.quickstep.views.TaskView.FLAG_UPDATE_ALL;

import android.util.FloatProperty;
import android.util.Pair;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.android.launcher3.anim.AnimatedFloat;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.quickstep.views.AddDesktopButton;
import com.android.quickstep.views.ClearAllButton;
import com.android.quickstep.views.RecentsView;
import com.android.quickstep.views.RecentsViewContainer;

/**
 * State controller for fallback recents activity
 */
public class FallbackRecentsStateController implements StateHandler<RecentsState> {

    private final StateAnimationConfig mNoConfig = new StateAnimationConfig();
    private final RecentsViewContainer mRecentsViewContainer;
    private final FallbackRecentsView mRecentsView;

    public FallbackRecentsStateController(RecentsViewContainer container) {
        mRecentsViewContainer = container;
        mRecentsView = container.getOverviewPanel();
    }

    @Override
    public void setState(RecentsState state) {
        mRecentsView.updateEmptyMessage();
        mRecentsView.resetTaskVisuals();
        setProperties(state, mNoConfig, PropertySetter.NO_ANIM_PROPERTY_SETTER);
    }

    @Override
    public void setStateWithAnimation(RecentsState toState, StateAnimationConfig config,
            PendingAnimation setter) {
        if (config.hasAnimationFlag(SKIP_OVERVIEW)) {
            return;
        }
        // While animating into recents, update the visible task data as needed
        setter.addOnFrameCallback(() -> mRecentsView.loadVisibleTaskData(FLAG_UPDATE_ALL));
        setter.addEndListener(success -> {
            if (!success && !toState.isRecentsViewVisible()) {
                mRecentsView.reset();
            }
        });
        mRecentsView.updateEmptyMessage();

        setProperties(toState, config, setter);
    }

    private void setProperties(RecentsState state, StateAnimationConfig config,
            PropertySetter setter) {
        float clearAllButtonAlpha = state.hasClearAllButton() ? 1 : 0;
        setter.setFloat(mRecentsView.getClearAllButton(),
                ClearAllButton.VISIBILITY_ALPHA, clearAllButtonAlpha, LINEAR);
        if (mRecentsView.getAddDeskButton() != null) {
            float addDeskButtonAlpha = state.hasAddDeskButton() ? 1 : 0;
            setter.setFloat(mRecentsView.getAddDeskButton(), AddDesktopButton.VISIBILITY_ALPHA,
                    addDeskButtonAlpha, LINEAR);
        }
        float overviewButtonAlpha = state.hasOverviewActions() ? 1 : 0;
        setter.setFloat(mRecentsViewContainer.getActionsView().getVisibilityAlpha(),
                AnimatedFloat.VALUE, overviewButtonAlpha, LINEAR);

        float[] scaleAndOffset = state.getOverviewScaleAndOffset(mRecentsViewContainer);
        setter.setFloat(mRecentsView, RECENTS_SCALE_PROPERTY, scaleAndOffset[0],
                config.getInterpolator(ANIM_OVERVIEW_SCALE, LINEAR));
        setter.setFloat(mRecentsView, ADJACENT_PAGE_HORIZONTAL_OFFSET, scaleAndOffset[1],
                config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_X, LINEAR));
        setter.setFloat(mRecentsView, TASK_SECONDARY_TRANSLATION, 0f,
                config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, LINEAR));

        setter.setFloat(mRecentsView, TASK_MODALNESS, state.getOverviewModalness(),
                config.getInterpolator(ANIM_OVERVIEW_MODAL,
                        enableGridOnlyOverview() && !state.isRecentsViewVisible() ? FINAL_FRAME
                                : LINEAR));
        setter.setFloat(mRecentsView, FULLSCREEN_PROGRESS, state.isFullScreen() ? 1 : 0, LINEAR);
        boolean showAsGrid =
                state.displayOverviewTasksAsGrid(mRecentsViewContainer.getDeviceProfile());
        setter.setFloat(mRecentsView, RECENTS_GRID_PROGRESS, showAsGrid ? 1f : 0f,
                getOverviewInterpolator(state));
        setter.setFloat(mRecentsView, TASK_THUMBNAIL_SPLASH_ALPHA,
                state.showTaskThumbnailSplash() ? 1f : 0f, getOverviewInterpolator(state));
        if (enableLargeDesktopWindowingTile()) {
            setter.setFloat(mRecentsView, DESKTOP_CAROUSEL_DETACH_PROGRESS,
                    state.detachDesktopCarousel() ? 1f : 0f,
                    getOverviewInterpolator(state));
        }
        if (enableDesktopExplodedView()) {
            setter.setFloat(mRecentsView, DESK_EXPLODE_PROGRESS,
                    state.showExplodedDesktopView() ? 1f : 0f,
                    getOverviewInterpolator(state));
        }

        setter.setScrimColors(mRecentsViewContainer.getScrimView(),
                state.getScrimColor(mRecentsViewContainer.asContext()),
                config.getInterpolator(ANIM_SCRIM_FADE, LINEAR));
        if (isSplitSelectionState(state)) {
            int duration = state.getTransitionDuration(mRecentsViewContainer, true);
            // TODO (b/246851887): Pass in setter as a NO_ANIM PendingAnimation instead
            PendingAnimation pa = new PendingAnimation(duration);
            mRecentsView.createSplitSelectInitAnimation(pa, duration);
            setter.add(pa.buildAnim());
        }

        Pair<FloatProperty<RecentsView<?, ?>>, FloatProperty<RecentsView<?, ?>>> taskViewsFloat =
                mRecentsView.getPagedOrientationHandler().getSplitSelectTaskOffset(
                        TASK_PRIMARY_SPLIT_TRANSLATION, TASK_SECONDARY_SPLIT_TRANSLATION,
                        mRecentsViewContainer.getDeviceProfile());
        setter.setFloat(mRecentsView, taskViewsFloat.first, isSplitSelectionState(state)
                ? mRecentsView.getSplitSelectTranslation() : 0, LINEAR);
        setter.setFloat(mRecentsView, taskViewsFloat.second, 0, LINEAR);
    }

    private Interpolator getOverviewInterpolator(RecentsState toState) {
        return toState.isRecentsViewVisible() ? INSTANT : FINAL_FRAME;
    }

    /**
     * @return true if {@param toState} is {@link RecentsState#OVERVIEW_SPLIT_SELECT}
     */
    private boolean isSplitSelectionState(@NonNull RecentsState toState) {
        return toState == OVERVIEW_SPLIT_SELECT;
    }
}
