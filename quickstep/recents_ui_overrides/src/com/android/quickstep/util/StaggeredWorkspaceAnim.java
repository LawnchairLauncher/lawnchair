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
package com.android.quickstep.util;

import static com.android.launcher3.LauncherAnimUtils.VIEW_TRANSLATE_Y;
import static com.android.launcher3.LauncherState.BACKGROUND_APP;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.anim.Interpolators.ACCEL_DEACCEL;
import static com.android.launcher3.anim.Interpolators.LINEAR;
import static com.android.launcher3.states.StateAnimationConfig.ANIM_ALL_COMPONENTS;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_DEPTH_CONTROLLER;
import static com.android.launcher3.states.StateAnimationConfig.SKIP_OVERVIEW;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.anim.SpringAnimationBuilder;
import com.android.launcher3.graphics.OverviewScrim;
import com.android.launcher3.states.StateAnimationConfig;
import com.android.launcher3.util.DynamicResource;
import com.android.quickstep.views.RecentsView;
import com.android.systemui.plugins.ResourceProvider;

/**
 * Creates an animation where all the workspace items are moved into their final location,
 * staggered row by row from the bottom up.
 * This is used in conjunction with the swipe up to home animation.
 */
public class StaggeredWorkspaceAnim {

    private static final int APP_CLOSE_ROW_START_DELAY_MS = 10;
    private static final int ALPHA_DURATION_MS = 250;

    private static final float MAX_VELOCITY_PX_PER_S = 22f;

    private final float mVelocity;
    private final float mSpringTransY;

    private final AnimatorSet mAnimators = new AnimatorSet();

    public StaggeredWorkspaceAnim(Launcher launcher, float velocity, boolean animateOverviewScrim) {
        prepareToAnimate(launcher);

        mVelocity = velocity;

        // Scale the translationY based on the initial velocity to better sync the workspace items
        // with the floating view.
        float transFactor = 0.2f + 0.9f * Math.abs(velocity) / MAX_VELOCITY_PX_PER_S;
        mSpringTransY = transFactor * launcher.getResources()
                .getDimensionPixelSize(R.dimen.swipe_up_max_workspace_trans_y);

        DeviceProfile grid = launcher.getDeviceProfile();
        Workspace workspace = launcher.getWorkspace();
        CellLayout cellLayout = (CellLayout) workspace.getChildAt(workspace.getCurrentPage());
        ShortcutAndWidgetContainer currentPage = cellLayout.getShortcutsAndWidgets();
        ViewGroup hotseat = launcher.getHotseat();

        boolean workspaceClipChildren = workspace.getClipChildren();
        boolean workspaceClipToPadding = workspace.getClipToPadding();
        boolean cellLayoutClipChildren = cellLayout.getClipChildren();
        boolean cellLayoutClipToPadding = cellLayout.getClipToPadding();
        boolean hotseatClipChildren = hotseat.getClipChildren();
        boolean hotseatClipToPadding = hotseat.getClipToPadding();

        workspace.setClipChildren(false);
        workspace.setClipToPadding(false);
        cellLayout.setClipChildren(false);
        cellLayout.setClipToPadding(false);
        hotseat.setClipChildren(false);
        hotseat.setClipToPadding(false);

        // Hotseat and QSB takes up two additional rows.
        int totalRows = grid.inv.numRows + (grid.isVerticalBarLayout() ? 0 : 2);

        // Set up springs on workspace items.
        for (int i = currentPage.getChildCount() - 1; i >= 0; i--) {
            View child = currentPage.getChildAt(i);
            CellLayout.LayoutParams lp = ((CellLayout.LayoutParams) child.getLayoutParams());
            addStaggeredAnimationForView(child, lp.cellY + lp.cellVSpan, totalRows);
        }

        // Set up springs for the hotseat and qsb.
        ViewGroup hotseatChild = (ViewGroup) hotseat.getChildAt(0);
        if (grid.isVerticalBarLayout()) {
            for (int i = hotseatChild.getChildCount() - 1; i >= 0; i--) {
                View child = hotseatChild.getChildAt(i);
                CellLayout.LayoutParams lp = ((CellLayout.LayoutParams) child.getLayoutParams());
                addStaggeredAnimationForView(child, lp.cellY + 1, totalRows);
            }
        } else {
            for (int i = hotseatChild.getChildCount() - 1; i >= 0; i--) {
                View child = hotseatChild.getChildAt(i);
                addStaggeredAnimationForView(child, grid.inv.numRows + 1, totalRows);
            }

            View qsb = launcher.findViewById(R.id.search_container_all_apps);
            addStaggeredAnimationForView(qsb, grid.inv.numRows + 2, totalRows);
        }

        if (animateOverviewScrim) {
            addScrimAnimationForState(launcher, BACKGROUND_APP, 0);
            addScrimAnimationForState(launcher, NORMAL, ALPHA_DURATION_MS);
        }

        mAnimators.play(launcher.getDragLayer().getScrim().createSysuiMultiplierAnim(0f, 1f)
                .setDuration(ALPHA_DURATION_MS));
        mAnimators.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                workspace.setClipChildren(workspaceClipChildren);
                workspace.setClipToPadding(workspaceClipToPadding);
                cellLayout.setClipChildren(cellLayoutClipChildren);
                cellLayout.setClipToPadding(cellLayoutClipToPadding);
                hotseat.setClipChildren(hotseatClipChildren);
                hotseat.setClipToPadding(hotseatClipToPadding);
            }
        });
    }

    /**
     * Setup workspace with 0 duration to prepare for our staggered animation.
     */
    private void prepareToAnimate(Launcher launcher) {
        StateAnimationConfig config = new StateAnimationConfig();
        config.animFlags = ANIM_ALL_COMPONENTS | SKIP_OVERVIEW | SKIP_DEPTH_CONTROLLER;
        config.duration = 0;
        // setRecentsAttachedToAppWindow() will animate recents out.
        launcher.getStateManager().createAtomicAnimation(BACKGROUND_APP, NORMAL, config).start();

        // Stop scrolling so that it doesn't interfere with the translation offscreen.
        launcher.<RecentsView>getOverviewPanel().getScroller().forceFinished(true);
    }

    public AnimatorSet getAnimators() {
        return mAnimators;
    }

    /**
     * Starts the animation.
     */
    public void start() {
        mAnimators.start();
    }

    /**
     * Adds an alpha/trans animator for {@param v}, with a start delay based on the view's row.
     *
     * @param v A view on the workspace.
     * @param row The bottom-most row that contains the view.
     * @param totalRows Total number of rows.
     */
    private void addStaggeredAnimationForView(View v, int row, int totalRows) {
        // Invert the rows, because we stagger starting from the bottom of the screen.
        int invertedRow = totalRows - row;
        // Add 1 to the inverted row so that the bottom most row has a start delay.
        long startDelay = (long) ((invertedRow + 1) * APP_CLOSE_ROW_START_DELAY_MS);

        v.setTranslationY(mSpringTransY);

        ResourceProvider rp = DynamicResource.provider(v.getContext());
        float stiffness = rp.getFloat(R.dimen.staggered_stiffness);
        float damping = rp.getFloat(R.dimen.staggered_damping_ratio);
        ValueAnimator springTransY = new SpringAnimationBuilder(v.getContext())
                .setStiffness(stiffness)
                .setDampingRatio(damping)
                .setMinimumVisibleChange(1f)
                .setStartValue(mSpringTransY)
                .setEndValue(0)
                .setStartVelocity(mVelocity)
                .build(v, VIEW_TRANSLATE_Y);
        springTransY.setStartDelay(startDelay);
        mAnimators.play(springTransY);

        v.setAlpha(0);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f);
        alpha.setInterpolator(LINEAR);
        alpha.setDuration(ALPHA_DURATION_MS);
        alpha.setStartDelay(startDelay);
        alpha.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                v.setAlpha(1f);
            }
        });
        mAnimators.play(alpha);
    }

    private void addScrimAnimationForState(Launcher launcher, LauncherState state, long duration) {
        PendingAnimation builder = new PendingAnimation(duration, mAnimators);
        launcher.getWorkspace().getStateTransitionAnimation().setScrim(builder, state);
        builder.setFloat(
                launcher.getDragLayer().getOverviewScrim(),
                OverviewScrim.SCRIM_PROGRESS,
                state.getOverviewScrimAlpha(launcher),
                ACCEL_DEACCEL);
    }
}
