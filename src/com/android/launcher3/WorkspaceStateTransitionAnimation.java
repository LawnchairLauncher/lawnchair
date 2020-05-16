/*
 * Copyright (C) 2015 The Android Open Source Project
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

import static com.android.launcher3.LauncherAnimUtils.DRAWABLE_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.LauncherAnimUtils.VIEW_ALPHA;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_X;
import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.LauncherState.FLAG_HAS_SYS_UI_SCRIM;
import static com.android.launcher3.LauncherState.FLAG_WORKSPACE_HAS_BACKGROUNDS;
import static com.android.launcher3.LauncherState.HOTSEAT_ICONS;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.anim.Interpolators.ZOOM_OUT;
import static com.android.launcher3.anim.PropertySetter.NO_ANIM_PROPERTY_SETTER;
import static com.android.launcher3.graphics.WorkspaceAndHotseatScrim.SCRIM_PROGRESS;
import static com.android.launcher3.graphics.WorkspaceAndHotseatScrim.SYSUI_PROGRESS;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_HOTSEAT_TRANSLATE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_WORKSPACE_TRANSLATE;

import android.view.View;
import android.view.animation.Interpolator;

import com.android.launcher3.LauncherState.PageAlphaProvider;
import com.android.launcher3.LauncherState.ScaleAndTranslation;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.graphics.WorkspaceAndHotseatScrim;
import com.android.launcher3.states.StateAnimationConfig;

/**
 * Manages the animations between each of the workspace states.
 */
public class WorkspaceStateTransitionAnimation {

    private final Launcher mLauncher;
    private final Workspace mWorkspace;

    private float mNewScale;

    public WorkspaceStateTransitionAnimation(Launcher launcher, Workspace workspace) {
        mLauncher = launcher;
        mWorkspace = workspace;
    }

    public void setState(LauncherState toState) {
        setWorkspaceProperty(toState, NO_ANIM_PROPERTY_SETTER, new StateAnimationConfig());
    }

    /**
     * @see com.android.launcher3.statemanager.StateManager.StateHandler#setStateWithAnimation
     */
    public void setStateWithAnimation(
            LauncherState toState, StateAnimationConfig config, PendingAnimation animation) {
        setWorkspaceProperty(toState, animation, config);
    }

    public float getFinalScale() {
        return mNewScale;
    }

    /**
     * Starts a transition animation for the workspace.
     */
    private void setWorkspaceProperty(LauncherState state, PropertySetter propertySetter,
            StateAnimationConfig config) {
        ScaleAndTranslation scaleAndTranslation = state.getWorkspaceScaleAndTranslation(mLauncher);
        ScaleAndTranslation hotseatScaleAndTranslation = state.getHotseatScaleAndTranslation(
                mLauncher);
        ScaleAndTranslation qsbScaleAndTranslation = state.getQsbScaleAndTranslation(mLauncher);
        mNewScale = scaleAndTranslation.scale;
        PageAlphaProvider pageAlphaProvider = state.getWorkspacePageAlphaProvider(mLauncher);
        final int childCount = mWorkspace.getChildCount();
        for (int i = 0; i < childCount; i++) {
            applyChildState(state, (CellLayout) mWorkspace.getChildAt(i), i, pageAlphaProvider,
                    propertySetter, config);
        }

        int elements = state.getVisibleElements(mLauncher);
        Interpolator fadeInterpolator = config.getInterpolator(ANIM_WORKSPACE_FADE,
                pageAlphaProvider.interpolator);
        boolean playAtomicComponent = config.playAtomicOverviewScaleComponent();
        Hotseat hotseat = mWorkspace.getHotseat();
        // Since we set the pivot relative to mWorkspace, we need to scale a sibling of Workspace.
        AllAppsContainerView qsbScaleView = mLauncher.getAppsView();
        View qsbView = qsbScaleView.getSearchView();
        if (playAtomicComponent) {
            Interpolator scaleInterpolator = config.getInterpolator(ANIM_WORKSPACE_SCALE, ZOOM_OUT);
            propertySetter.setFloat(mWorkspace, SCALE_PROPERTY, mNewScale, scaleInterpolator);

            setPivotToScaleWithWorkspace(hotseat);
            setPivotToScaleWithWorkspace(qsbScaleView);
            float hotseatScale = hotseatScaleAndTranslation.scale;
            Interpolator hotseatScaleInterpolator = config.getInterpolator(ANIM_HOTSEAT_SCALE,
                    scaleInterpolator);
            propertySetter.setFloat(hotseat, SCALE_PROPERTY, hotseatScale,
                    hotseatScaleInterpolator);
            propertySetter.setFloat(qsbScaleView, SCALE_PROPERTY, qsbScaleAndTranslation.scale,
                    hotseatScaleInterpolator);

            float hotseatIconsAlpha = (elements & HOTSEAT_ICONS) != 0 ? 1 : 0;
            propertySetter.setViewAlpha(hotseat, hotseatIconsAlpha, fadeInterpolator);
            propertySetter.setViewAlpha(mLauncher.getWorkspace().getPageIndicator(),
                    hotseatIconsAlpha, fadeInterpolator);
        }

        if (config.onlyPlayAtomicComponent()) {
            // Only the alpha and scale, handled above, are included in the atomic animation.
            return;
        }

        Interpolator translationInterpolator = !playAtomicComponent
                ? LINEAR
                : config.getInterpolator(ANIM_WORKSPACE_TRANSLATE, ZOOM_OUT);
        propertySetter.setFloat(mWorkspace, VIEW_TRANSLATE_X,
                scaleAndTranslation.translationX, translationInterpolator);
        propertySetter.setFloat(mWorkspace, VIEW_TRANSLATE_Y,
                scaleAndTranslation.translationY, translationInterpolator);

        Interpolator hotseatTranslationInterpolator = config.getInterpolator(
                ANIM_HOTSEAT_TRANSLATE, translationInterpolator);
        propertySetter.setFloat(hotseat, VIEW_TRANSLATE_Y,
                hotseatScaleAndTranslation.translationY, hotseatTranslationInterpolator);
        propertySetter.setFloat(mWorkspace.getPageIndicator(), VIEW_TRANSLATE_Y,
                hotseatScaleAndTranslation.translationY, hotseatTranslationInterpolator);
        propertySetter.setFloat(qsbView, VIEW_TRANSLATE_Y,
                qsbScaleAndTranslation.translationY, hotseatTranslationInterpolator);

        setScrim(propertySetter, state);
    }

    /**
     * Set the given view's pivot point to match the workspace's, so that it scales together. Since
     * both this view and workspace can move, transform the point manually instead of using
     * dragLayer.getDescendantCoordRelativeToSelf and related methods.
     */
    private void setPivotToScaleWithWorkspace(View sibling) {
        sibling.setPivotY(mWorkspace.getPivotY() + mWorkspace.getTop()
                - sibling.getTop() - sibling.getTranslationY());
        sibling.setPivotX(mWorkspace.getPivotX() + mWorkspace.getLeft()
                - sibling.getLeft() - sibling.getTranslationX());
    }

    public void setScrim(PropertySetter propertySetter, LauncherState state) {
        WorkspaceAndHotseatScrim scrim = mLauncher.getDragLayer().getScrim();
        propertySetter.setFloat(scrim, SCRIM_PROGRESS, state.getWorkspaceScrimAlpha(mLauncher),
                LINEAR);
        propertySetter.setFloat(scrim, SYSUI_PROGRESS,
                state.hasFlag(FLAG_HAS_SYS_UI_SCRIM) ? 1 : 0, LINEAR);
    }

    public void applyChildState(LauncherState state, CellLayout cl, int childIndex) {
        applyChildState(state, cl, childIndex, state.getWorkspacePageAlphaProvider(mLauncher),
                NO_ANIM_PROPERTY_SETTER, new StateAnimationConfig());
    }

    private void applyChildState(LauncherState state, CellLayout cl, int childIndex,
            PageAlphaProvider pageAlphaProvider, PropertySetter propertySetter,
            StateAnimationConfig config) {
        float pageAlpha = pageAlphaProvider.getPageAlpha(childIndex);
        int drawableAlpha = state.hasFlag(FLAG_WORKSPACE_HAS_BACKGROUNDS)
                ? Math.round(pageAlpha * 255) : 0;

        if (!config.onlyPlayAtomicComponent()) {
            // Don't update the scrim during the atomic animation.
            propertySetter.setInt(cl.getScrimBackground(),
                    DRAWABLE_ALPHA, drawableAlpha, ZOOM_OUT);
        }
        if (config.playAtomicOverviewScaleComponent()) {
            Interpolator fadeInterpolator = config.getInterpolator(ANIM_WORKSPACE_FADE,
                    pageAlphaProvider.interpolator);
            propertySetter.setFloat(cl.getShortcutsAndWidgets(), VIEW_ALPHA,
                    pageAlpha, fadeInterpolator);
        }
    }
}