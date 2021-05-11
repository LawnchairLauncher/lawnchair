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

import static com.android.launcher3.LauncherState.CLEAR_ALL_BUTTON;
import static com.android.launcher3.LauncherState.OVERVIEW_ACTIONS;
import static com.android.launcher3.LauncherState.OVERVIEW_SPLIT_SELECT;
import static com.android.launcher3.LauncherState.SPLIT_PLACHOLDER_VIEW;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_ACTIONS_FADE;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.TASK_MODALNESS;
import static com.android.quickstep.views.SplitPlaceholderView.ALPHA_FLOAT;
import static com.android.quickstep.views.TaskView.FLAG_UPDATE_ALL;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.FloatProperty;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimatorListeners;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.views.ClearAllButton;
import com.android.quickstep.views.LauncherRecentsView;
import com.android.quickstep.views.RecentsView;

/**
 * State handler for handling UI changes for {@link LauncherRecentsView}. In addition to managing
 * the basic view properties, this class also manages changes in the task visuals.
 */
@TargetApi(Build.VERSION_CODES.O)
public final class RecentsViewStateController extends
        BaseRecentsViewStateController<LauncherRecentsView> {

    public RecentsViewStateController(BaseQuickstepLauncher launcher) {
        super(launcher);
    }

    @Override
    public void setState(@NonNull LauncherState state) {
        super.setState(state);
        if (state.overviewUi) {
            mRecentsView.updateEmptyMessage();
            mRecentsView.resetTaskVisuals();
        }
        setAlphas(PropertySetter.NO_ANIM_PROPERTY_SETTER, new StateAnimationConfig(), state);
        mRecentsView.setFullscreenProgress(state.getOverviewFullscreenProgress());
    }

    @Override
    void setStateWithAnimationInternal(@NonNull LauncherState toState,
            @NonNull StateAnimationConfig config, @NonNull PendingAnimation builder) {
        super.setStateWithAnimationInternal(toState, config, builder);

        if (toState.overviewUi) {
            // While animating into recents, update the visible task data as needed
            builder.addOnFrameCallback(() -> mRecentsView.loadVisibleTaskData(FLAG_UPDATE_ALL));
            mRecentsView.updateEmptyMessage();
        } else {
            builder.addListener(
                    AnimatorListeners.forSuccessCallback(mRecentsView::resetTaskVisuals));
        }

        // Create or dismiss split screen select animations
        LauncherState currentState = mLauncher.getStateManager().getState();
        if (isSplitSelectionState(toState) && !isSplitSelectionState(currentState)) {
            builder.add(mRecentsView.createSplitSelectInitAnimation().buildAnim());
        } else if (!isSplitSelectionState(toState) && isSplitSelectionState(currentState)) {
            builder.add(mRecentsView.cancelSplitSelect(true).buildAnim());
        }

        setAlphas(builder, config, toState);
        builder.setFloat(mRecentsView, FULLSCREEN_PROGRESS,
                toState.getOverviewFullscreenProgress(), LINEAR);
    }

    /**
     * @return true if {@param toState} is {@link LauncherState#OVERVIEW_SPLIT_SELECT}
     */
    private boolean isSplitSelectionState(@NonNull LauncherState toState) {
        return toState == OVERVIEW_SPLIT_SELECT;
    }

    private void setAlphas(PropertySetter propertySetter, StateAnimationConfig config,
            LauncherState state) {
        float clearAllButtonAlpha = state.areElementsVisible(mLauncher, CLEAR_ALL_BUTTON) ? 1 : 0;
        propertySetter.setFloat(mRecentsView.getClearAllButton(), ClearAllButton.VISIBILITY_ALPHA,
                clearAllButtonAlpha, LINEAR);
        float overviewButtonAlpha = state.areElementsVisible(mLauncher, OVERVIEW_ACTIONS)
                && mRecentsView.shouldShowOverviewActionsForState(state) ? 1 : 0;
        propertySetter.setFloat(mLauncher.getActionsView().getVisibilityAlpha(),
                MultiValueAlpha.VALUE, overviewButtonAlpha, config.getInterpolator(
                        ANIM_OVERVIEW_ACTIONS_FADE, LINEAR));

        float splitPlaceholderAlpha = state.areElementsVisible(mLauncher, SPLIT_PLACHOLDER_VIEW) ?
                0.85f : 0;
        propertySetter.setFloat(mRecentsView.getSplitPlaceholder(), ALPHA_FLOAT,
                splitPlaceholderAlpha, LINEAR);
    }

    @Override
    FloatProperty<RecentsView> getTaskModalnessProperty() {
        return TASK_MODALNESS;
    }

    @Override
    FloatProperty<RecentsView> getContentAlphaProperty() {
        return CONTENT_ALPHA;
    }
}
