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

import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherState.OVERVIEW_BUTTONS;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE_IN_OUT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_OVERVIEW_FADE;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;

import android.annotation.TargetApi;
import android.os.Build;
import android.util.FloatProperty;
import android.view.Surface;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.anim.AnimationSuccessListener;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.states.StateAnimationConfig;
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
        setAlphas(PropertySetter.NO_ANIM_PROPERTY_SETTER, state, LINEAR);
        mRecentsView.setFullscreenProgress(state.getOverviewFullscreenProgress());
    }

    @Override
    void setStateWithAnimationInternal(@NonNull LauncherState toState,
            @NonNull StateAnimationConfig config, @NonNull PendingAnimation builder) {
        super.setStateWithAnimationInternal(toState, config, builder);

        if (toState.overviewUi) {
            // While animating into recents, update the visible task data as needed
            builder.addOnFrameCallback(mRecentsView::loadVisibleTaskData);
            mRecentsView.updateEmptyMessage();
        } else {
            builder.getAnim().addListener(
                    AnimationSuccessListener.forRunnable(mRecentsView::resetTaskVisuals));
        }

        setAlphas(builder, toState,
                config.getInterpolator(ANIM_OVERVIEW_FADE, AGGRESSIVE_EASE_IN_OUT));
        builder.setFloat(mRecentsView, FULLSCREEN_PROGRESS,
                toState.getOverviewFullscreenProgress(), LINEAR);
    }

    private void setAlphas(PropertySetter propertySetter, LauncherState state,
            Interpolator actionInterpolator) {
        float buttonAlpha = (state.getVisibleElements(mLauncher) & OVERVIEW_BUTTONS) != 0 ? 1 : 0;
        propertySetter.setFloat(mRecentsView.getClearAllButton(), ClearAllButton.VISIBILITY_ALPHA,
                buttonAlpha, LINEAR);

        View actionsView = mLauncher.getActionsView();
        int launcherRotation = mRecentsView.getPagedViewOrientedState().getLauncherRotation();
        if (actionsView != null && launcherRotation == Surface.ROTATION_0) {
            propertySetter.setViewAlpha(actionsView, buttonAlpha, actionInterpolator);
        }
    }

    @Override
    FloatProperty<RecentsView> getContentAlphaProperty() {
        return CONTENT_ALPHA;
    }
}
