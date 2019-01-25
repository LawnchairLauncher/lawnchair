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

import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE_IN_OUT;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.util.FloatProperty;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;

/**
 * State handler for recents view. Manages UI changes and animations for recents view based off the
 * current {@link LauncherState}.
 *
 * @param <T> the recents view
 */
public abstract class BaseRecentsViewStateController<T extends View>
        implements StateHandler {
    protected final T mRecentsView;
    protected final Launcher mLauncher;

    public BaseRecentsViewStateController(@NonNull Launcher launcher) {
        mLauncher = launcher;
        mRecentsView = launcher.getOverviewPanel();
    }

    @Override
    public void setState(@NonNull LauncherState state) {
        float[] scaleTranslationYFactor = state.getOverviewScaleAndTranslationYFactor(mLauncher);
        SCALE_PROPERTY.set(mRecentsView, scaleTranslationYFactor[0]);
        getTranslationYFactorProperty().set(mRecentsView, scaleTranslationYFactor[1]);
        getContentAlphaProperty().set(mRecentsView, state.overviewUi ? 1f : 0);
    }

    @Override
    public final void setStateWithAnimation(@NonNull final LauncherState toState,
            @NonNull AnimatorSetBuilder builder, @NonNull AnimationConfig config) {
        if (!config.playAtomicComponent()) {
            // The entire recents animation is played atomically.
            return;
        }
        setStateWithAnimationInternal(toState, builder, config);
    }

    /**
     * Core logic for animating the recents view UI.
     *
     * @param toState state to animate to
     * @param builder animator set builder
     * @param config current animation config
     */
    void setStateWithAnimationInternal(@NonNull final LauncherState toState,
            @NonNull AnimatorSetBuilder builder, @NonNull AnimationConfig config) {
        PropertySetter setter = config.getPropertySetter(builder);
        float[] scaleTranslationYFactor = toState.getOverviewScaleAndTranslationYFactor(mLauncher);
        Interpolator scaleAndTransYInterpolator = getScaleAndTransYInterpolator(toState, builder);
        setter.setFloat(mRecentsView, SCALE_PROPERTY, scaleTranslationYFactor[0],
                scaleAndTransYInterpolator);
        setter.setFloat(mRecentsView, getTranslationYFactorProperty(), scaleTranslationYFactor[1],
                scaleAndTransYInterpolator);
        setter.setFloat(mRecentsView, getContentAlphaProperty(), toState.overviewUi ? 1 : 0,
                builder.getInterpolator(ANIM_OVERVIEW_FADE, AGGRESSIVE_EASE_IN_OUT));
    }

    /**
     * Get the interpolator to use for the scale and translation Y animation for the view.
     *
     * @param toState state to animate to
     * @param builder animator set builder
     * @return interpolator for scale and trans Y recents view animation
     */
    Interpolator getScaleAndTransYInterpolator(@NonNull final LauncherState toState,
            @NonNull AnimatorSetBuilder builder) {
        return builder.getInterpolator(ANIM_OVERVIEW_SCALE, LINEAR);
    }

    /**
     * Get property for translation Y factor for the recents view.
     *
     * @return the float property for the recents view
     */
    abstract FloatProperty getTranslationYFactorProperty();

    /**
     * Get property for content alpha for the recents view.
     *
     * @return the float property for the view's content alpha
     */
    abstract FloatProperty getContentAlphaProperty();
}
