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

import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_MODAL;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.states.StateAnimationConfig.PLAY_ATOMIC_OVERVIEW_PEEK;
import static com.android.launcher3.states.StateAnimationConfig.PLAY_ATOMIC_OVERVIEW_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW;
import static com.android.quickstep.views.RecentsView.ADJACENT_PAGE_OFFSET;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;
import static com.android.quickstep.views.RecentsView.RECENTS_SCALE_PROPERTY;
import static com.android.quickstep.views.RecentsView.TASK_MODALNESS;
import static com.android.quickstep.views.RecentsView.TASK_SECONDARY_TRANSLATION;

import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.statemanager.StateManager.StateHandler;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.quickstep.RecentsActivity;

/**
 * State controller for fallback recents activity
 */
public class FallbackRecentsStateController implements StateHandler<RecentsState> {

    private final StateAnimationConfig mNoConfig = new StateAnimationConfig();
    private final RecentsActivity mActivity;
    private final FallbackRecentsView mRecentsView;

    public FallbackRecentsStateController(RecentsActivity activity) {
        mActivity = activity;
        mRecentsView = activity.getOverviewPanel();
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
        if (!config.hasAnimationFlag(PLAY_ATOMIC_OVERVIEW_PEEK | PLAY_ATOMIC_OVERVIEW_SCALE)) {
            // The entire recents animation is played atomically.
            return;
        }
        if (config.hasAnimationFlag(SKIP_OVERVIEW)) {
            return;
        }
        // While animating into recents, update the visible task data as needed
        setter.addOnFrameCallback(mRecentsView::loadVisibleTaskData);
        mRecentsView.updateEmptyMessage();

        setProperties(toState, config, setter);
    }

    private void setProperties(RecentsState state, StateAnimationConfig config,
            PropertySetter setter) {
        float buttonAlpha = state.hasButtons() ? 1 : 0;
        setter.setFloat(mActivity.getActionsView().getVisibilityAlpha(),
                MultiValueAlpha.VALUE, buttonAlpha, LINEAR);

        float[] scaleAndOffset = state.getOverviewScaleAndOffset(mActivity);
        setter.setFloat(mRecentsView, RECENTS_SCALE_PROPERTY, scaleAndOffset[0],
                config.getInterpolator(ANIM_OVERVIEW_SCALE, LINEAR));
        setter.setFloat(mRecentsView, ADJACENT_PAGE_OFFSET, scaleAndOffset[1],
                config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_X, LINEAR));
        setter.setFloat(mRecentsView, TASK_SECONDARY_TRANSLATION, 0f,
                config.getInterpolator(ANIM_OVERVIEW_TRANSLATE_Y, LINEAR));

        setter.setFloat(mRecentsView, TASK_MODALNESS, state.getOverviewModalness(),
                config.getInterpolator(ANIM_OVERVIEW_MODAL, LINEAR));
        setter.setFloat(mRecentsView, FULLSCREEN_PROGRESS, state.isFullScreen() ? 1 : 0, LINEAR);
    }
}
