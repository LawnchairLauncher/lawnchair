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
import android.view.WindowManager;

import com.android.launcher3.CellLayout;
import com.android.launcher3.Launcher;
import com.android.launcher3.ShortcutAndWidgetContainer;
import com.android.launcher3.Workspace;
import com.android.systemui.unfold.updates.RotationChangeProvider;

/**
 * Animation that moves launcher icons and widgets from center to the sides (final position)
 */
public class UnfoldMoveFromCenterWorkspaceAnimator extends BaseUnfoldMoveFromCenterAnimator {

    private final Launcher mLauncher;

    public UnfoldMoveFromCenterWorkspaceAnimator(Launcher launcher, WindowManager windowManager,
            RotationChangeProvider rotationChangeProvider) {
        super(windowManager, rotationChangeProvider);
        mLauncher = launcher;
    }

    @Override
    protected void onPrepareViewsForAnimation() {
        Workspace<?> workspace = mLauncher.getWorkspace();

        // App icons and widgets
        workspace
                .forEachVisiblePage(page -> {
                    final CellLayout cellLayout = (CellLayout) page;
                    ShortcutAndWidgetContainer itemsContainer = cellLayout
                            .getShortcutsAndWidgets();
                    disableClipping(cellLayout);

                    for (int i = 0; i < itemsContainer.getChildCount(); i++) {
                        View child = itemsContainer.getChildAt(i);
                        registerViewForAnimation(child);
                    }
                });

        disableClipping(workspace);
    }

    @Override
    public void onTransitionFinished() {
        restoreClipping(mLauncher.getWorkspace());
        mLauncher.getWorkspace().forEachVisiblePage(page -> restoreClipping((CellLayout) page));
        super.onTransitionFinished();
    }
}
