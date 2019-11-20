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
import static com.android.launcher3.LauncherStateManager.ANIM_ALL;
import static com.android.launcher3.anim.Interpolators.LINEAR;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.CellLayout;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.LauncherStateManager.AnimationConfig;
import com.android.launcher3.R;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Workspace;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.anim.PropertySetter;
import com.android.launcher3.anim.SpringObjectAnimator;
import com.android.launcher3.graphics.OverviewScrim;
import com.android.quickstep.views.RecentsView;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates an animation where all the workspace items are moved into their final location,
 * staggered row by row from the bottom up.
 * This is used in conjunction with the swipe up to home animation.
 */
public class StaggeredWorkspaceAnim {

    private static final int APP_CLOSE_ROW_START_DELAY_MS = 10;
    private static final int ALPHA_DURATION_MS = 250;

    private static final float MAX_VELOCITY_PX_PER_S = 22f;

    private static final float DAMPING_RATIO = 0.7f;
    private static final float STIFFNESS = 150f;

    private final float mVelocity;
    private final float mSpringTransY;

    private final List<Animator> mAnimators = new ArrayList<>();

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

        AnimatorListener resetClipListener = new AnimatorListenerAdapter() {
            int numAnimations = mAnimators.size();

            @Override
            public void onAnimationEnd(Animator animation) {
                numAnimations--;
                if (numAnimations > 0) {
                    return;
                }

                workspace.setClipChildren(workspaceClipChildren);
                workspace.setClipToPadding(workspaceClipToPadding);
                cellLayout.setClipChildren(cellLayoutClipChildren);
                cellLayout.setClipToPadding(cellLayoutClipToPadding);
                hotseat.setClipChildren(hotseatClipChildren);
                hotseat.setClipToPadding(hotseatClipToPadding);
            }
        };

        for (Animator a : mAnimators) {
            a.addListener(resetClipListener);
        }
    }

    /**
     * Setup workspace with 0 duration to prepare for our staggered animation.
     */
    private void prepareToAnimate(Launcher launcher) {
        LauncherStateManager stateManager = launcher.getStateManager();
        AnimatorSetBuilder builder = new AnimatorSetBuilder();
        // setRecentsAttachedToAppWindow() will animate recents out.
        builder.addFlag(AnimatorSetBuilder.FLAG_DONT_ANIMATE_OVERVIEW);
        stateManager.createAtomicAnimation(BACKGROUND_APP, NORMAL, builder, ANIM_ALL, 0);
        builder.build().start();

        // Stop scrolling so that it doesn't interfere with the translation offscreen.
        launcher.<RecentsView>getOverviewPanel().getScroller().forceFinished(true);
    }

    /**
     * Starts the animation.
     */
    public void start() {
        for (Animator a : mAnimators) {
            if (a instanceof SpringObjectAnimator) {
                ((SpringObjectAnimator) a).startSpring(1f, mVelocity, null);
            } else {
                a.start();
            }
        }
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
        SpringObjectAnimator springTransY = new SpringObjectAnimator<>(v, VIEW_TRANSLATE_Y,
                1f, DAMPING_RATIO, STIFFNESS, mSpringTransY, 0);
        springTransY.setStartDelay(startDelay);
        mAnimators.add(springTransY);

        v.setAlpha(0);
        ObjectAnimator alpha = ObjectAnimator.ofFloat(v, View.ALPHA, 0f, 1f);
        alpha.setInterpolator(LINEAR);
        alpha.setDuration(ALPHA_DURATION_MS);
        alpha.setStartDelay(startDelay);
        mAnimators.add(alpha);
    }

    private void addScrimAnimationForState(Launcher launcher, LauncherState state, long duration) {
        AnimatorSetBuilder scrimAnimBuilder = new AnimatorSetBuilder();
        AnimationConfig scrimAnimConfig = new AnimationConfig();
        scrimAnimConfig.duration = duration;
        PropertySetter scrimPropertySetter = scrimAnimConfig.getPropertySetter(scrimAnimBuilder);
        launcher.getWorkspace().getStateTransitionAnimation().setScrim(scrimPropertySetter, state);
        mAnimators.add(scrimAnimBuilder.build());
        Animator fadeOverviewScrim = ObjectAnimator.ofFloat(
                launcher.getDragLayer().getOverviewScrim(), OverviewScrim.SCRIM_PROGRESS,
                state.getOverviewScrimAlpha(launcher));
        fadeOverviewScrim.setDuration(duration);
        mAnimators.add(fadeOverviewScrim);
    }
}
