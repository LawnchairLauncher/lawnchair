/*
 * Copyright (C) 2021 The Android Open Source Project
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

import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Hotseat;
import com.android.launcher3.Launcher;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Workspace;
import com.android.systemui.shared.animation.UnfoldMoveFromCenterAnimator;
import com.android.systemui.unfold.UnfoldTransitionProgressProvider;

import java.util.HashMap;
import java.util.Map;

/**
 * Animation that moves launcher icons and widgets from center to the sides (final position)
 */
public class UnfoldMoveFromCenterWorkspaceAnimator
        implements UnfoldTransitionProgressProvider.TransitionProgressListener {

    private final Launcher mLauncher;
    private final UnfoldMoveFromCenterAnimator mMoveFromCenterAnimation;

    private final Map<ViewGroup, Boolean> mOriginalClipToPadding = new HashMap<>();
    private final Map<ViewGroup, Boolean> mOriginalClipChildren = new HashMap<>();

    public UnfoldMoveFromCenterWorkspaceAnimator(Launcher launcher, WindowManager windowManager) {
        mLauncher = launcher;
        mMoveFromCenterAnimation = new UnfoldMoveFromCenterAnimator(windowManager,
                new LauncherViewsMoveFromCenterTranslationApplier());
    }

    @Override
    public void onTransitionStarted() {
        mMoveFromCenterAnimation.updateDisplayProperties();

        Workspace workspace = mLauncher.getWorkspace();
        Hotseat hotseat = mLauncher.getHotseat();

        // App icons and widgets
        workspace
                .forEachVisiblePage(page -> {
                    final CellLayout cellLayout = (CellLayout) page;
                    ShortcutAndWidgetContainer itemsContainer = cellLayout
                            .getShortcutsAndWidgets();
                    disableClipping(cellLayout);

                    for (int i = 0; i < itemsContainer.getChildCount(); i++) {
                        View child = itemsContainer.getChildAt(i);
                        mMoveFromCenterAnimation.registerViewForAnimation(child);
                    }
                });

        disableClipping(workspace);

        // Hotseat icons
        ViewGroup hotseatIcons = hotseat.getShortcutsAndWidgets();
        disableClipping(hotseat);

        for (int i = 0; i < hotseatIcons.getChildCount(); i++) {
            View child = hotseatIcons.getChildAt(i);
            mMoveFromCenterAnimation.registerViewForAnimation(child);
        }

        onTransitionProgress(0f);
    }

    @Override
    public void onTransitionProgress(float progress) {
        mMoveFromCenterAnimation.onTransitionProgress(progress);
    }

    @Override
    public void onTransitionFinished() {
        mMoveFromCenterAnimation.onTransitionFinished();
        mMoveFromCenterAnimation.clearRegisteredViews();

        restoreClipping(mLauncher.getWorkspace());
        mLauncher.getWorkspace().forEachVisiblePage(page -> restoreClipping((CellLayout) page));
        restoreClipping(mLauncher.getHotseat());

        mOriginalClipChildren.clear();
        mOriginalClipToPadding.clear();
    }

    private void disableClipping(ViewGroup view) {
        mOriginalClipToPadding.put(view, view.getClipToPadding());
        mOriginalClipChildren.put(view, view.getClipChildren());
        view.setClipToPadding(false);
        view.setClipChildren(false);
    }

    private void restoreClipping(ViewGroup view) {
        final Boolean originalClipToPadding = mOriginalClipToPadding.get(view);
        if (originalClipToPadding != null) {
            view.setClipToPadding(originalClipToPadding);
        }
        final Boolean originalClipChildren = mOriginalClipChildren.get(view);
        if (originalClipChildren != null) {
            view.setClipChildren(originalClipChildren);
        }
    }
}
