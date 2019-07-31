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
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_SCRIM_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_TRANSLATE_Y;
import static com.android.launcher3.anim.AnimatorSetBuilder.FLAG_DONT_ANIMATE_OVERVIEW;
import static com.android.launcher3.anim.Interpolators.AGGRESSIVE_EASE_IN_OUT;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.graphics.Scrim.SCRIM_PROGRESS;

import android.util.FloatProperty;
import android.view.View;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherState.ScaleAndTranslation;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.LauncherStateManager.StateHandler;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.graphics.OverviewScrim;

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
        ScaleAndTranslation scaleAndTranslation = state
                .getOverviewScaleAndTranslation(mLauncher);
        SCALE_PROPERTY.set(mRecentsView, scaleAndTranslation.scale);
        float translationX = scaleAndTranslation.translationX;
        if (mRecentsView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            translationX = -translationX;
        }
        mRecentsView.setTranslationX(translationX);
        mRecentsView.setTranslationY(scaleAndTranslation.translationY);
        getContentAlphaProperty().set(mRecentsView, state.overviewUi ? 1f : 0);
        OverviewScrim scrim = mLauncher.getDragLayer().getOverviewScrim();
        SCRIM_PROGRESS.set(scrim, state.getOverviewScrimAlpha(mLauncher));
    }

    @Override
    public final void setStateWithAnimation(@NonNull final LauncherState toState,
            @NonNull AnimatorSetBuilder builder, @NonNull AnimationConfig config) {
        boolean playAtomicOverviewComponent = config.playAtomicOverviewScaleComponent()
                || config.playAtomicOverviewPeekComponent();
        if (!playAtomicOverviewComponent) {
            // The entire recents animation is played atomically.
            return;
        }
        if (builder.hasFlag(FLAG_DONT_ANIMATE_OVERVIEW)) {
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
        ScaleAndTranslation scaleAndTranslation = toState.getOverviewScaleAndTranslation(mLauncher);
        Interpolator scaleInterpolator = builder.getInterpolator(ANIM_OVERVIEW_SCALE, LINEAR);
        setter.setFloat(mRecentsView, SCALE_PROPERTY, scaleAndTranslation.scale, scaleInterpolator);
        Interpolator translateXInterpolator = builder.getInterpolator(
                ANIM_OVERVIEW_TRANSLATE_X, LINEAR);
        Interpolator translateYInterpolator = builder.getInterpolator(
                ANIM_OVERVIEW_TRANSLATE_Y, LINEAR);
        float translationX = scaleAndTranslation.translationX;
        if (mRecentsView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            translationX = -translationX;
        }
        setter.setFloat(mRecentsView, View.TRANSLATION_X, translationX, translateXInterpolator);
        setter.setFloat(mRecentsView, View.TRANSLATION_Y, scaleAndTranslation.translationY,
                translateYInterpolator);
        setter.setFloat(mRecentsView, getContentAlphaProperty(), toState.overviewUi ? 1 : 0,
                builder.getInterpolator(ANIM_OVERVIEW_FADE, AGGRESSIVE_EASE_IN_OUT));
        OverviewScrim scrim = mLauncher.getDragLayer().getOverviewScrim();
        setter.setFloat(scrim, SCRIM_PROGRESS, toState.getOverviewScrimAlpha(mLauncher),
                builder.getInterpolator(ANIM_OVERVIEW_SCRIM_FADE, LINEAR));
    }

    /**
     * Get property for content alpha for the recents view.
     *
     * @return the float property for the view's content alpha
     */
    abstract FloatProperty getContentAlphaProperty();
}
