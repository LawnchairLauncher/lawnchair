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

import static com.android.launcher3.LauncherState.RECENTS_CLEAR_ALL_BUTTON;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.quickstep.views.RecentsView.CONTENT_ALPHA;
import static com.android.quickstep.views.RecentsView.FULLSCREEN_PROGRESS;

import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.os.Build;
import android.util.FloatProperty;

import androidx.annotation.NonNull;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
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

    public RecentsViewStateController(Launcher launcher) {
        super(launcher);
    }

    @Override
    public void setState(@NonNull LauncherState state) {
        super.setState(state);
        if (state.overviewUi) {
            mRecentsView.updateEmptyMessage();
            mRecentsView.resetTaskVisuals();
        }
        setAlphas(PropertySetter.NO_ANIM_PROPERTY_SETTER, state.getVisibleElements(mLauncher));
        mRecentsView.setFullscreenProgress(state.getOverviewFullscreenProgress());
    }

    @Override
    void setStateWithAnimationInternal(@NonNull final LauncherState toState,
            @NonNull AnimatorSetBuilder builder, @NonNull AnimationConfig config) {
        super.setStateWithAnimationInternal(toState, builder, config);

        if (!toState.overviewUi) {
            builder.addOnFinishRunnable(mRecentsView::resetTaskVisuals);
        }

        if (toState.overviewUi) {
            ValueAnimator updateAnim = ValueAnimator.ofFloat(0, 1);
            updateAnim.addUpdateListener(valueAnimator -> {
                // While animating into recents, update the visible task data as needed
                mRecentsView.loadVisibleTaskData();
            });
            updateAnim.setDuration(config.duration);
            builder.play(updateAnim);
            mRecentsView.updateEmptyMessage();
        }

        PropertySetter propertySetter = config.getPropertySetter(builder);
        setAlphas(propertySetter, toState.getVisibleElements(mLauncher));
        float fullscreenProgress = toState.getOverviewFullscreenProgress();
        propertySetter.setFloat(mRecentsView, FULLSCREEN_PROGRESS, fullscreenProgress, LINEAR);
    }

    private void setAlphas(PropertySetter propertySetter, int visibleElements) {
        boolean hasClearAllButton = (visibleElements & RECENTS_CLEAR_ALL_BUTTON) != 0;
        propertySetter.setFloat(mRecentsView.getClearAllButton(), ClearAllButton.VISIBILITY_ALPHA,
                hasClearAllButton ? 1f : 0f, LINEAR);
    }

    @Override
    FloatProperty<RecentsView> getContentAlphaProperty() {
        return CONTENT_ALPHA;
    }
}
