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
package com.android.launcher3.states;

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_HOME;

import android.content.Context;
import android.graphics.Rect;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;

/**
 * Definition for spring loaded state used during drag and drop.
 */
public class SpringLoadedState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_MULTI_PAGE
            | FLAG_WORKSPACE_INACCESSIBLE | FLAG_DISABLE_RESTORE
            | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED | FLAG_WORKSPACE_HAS_BACKGROUNDS
            | FLAG_HIDE_BACK_BUTTON;

    public SpringLoadedState(int id) {
        super(id, LAUNCHER_STATE_HOME, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(Context context) {
        return 150;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        DeviceProfile grid = launcher.getDeviceProfile();
        Workspace ws = launcher.getWorkspace();
        if (ws.getChildCount() == 0) {
            return super.getWorkspaceScaleAndTranslation(launcher);
        }

        if (grid.isVerticalBarLayout()) {
            float scale = grid.workspaceSpringLoadShrinkFactor;
            return new ScaleAndTranslation(scale, 0, 0);
        }

        float scale = grid.workspaceSpringLoadShrinkFactor;
        Rect insets = launcher.getDragLayer().getInsets();
        int insetsBottom = grid.isTaskbarPresent ? grid.taskbarSize : insets.bottom;

        float scaledHeight = scale * ws.getNormalChildHeight();
        float shrunkTop = insets.top + grid.dropTargetBarSizePx;
        float shrunkBottom = ws.getMeasuredHeight() - insetsBottom
                - grid.workspacePadding.bottom
                - grid.workspaceSpringLoadedBottomSpace;
        float totalShrunkSpace = shrunkBottom - shrunkTop;

        float desiredCellTop = shrunkTop + (totalShrunkSpace - scaledHeight) / 2;

        float halfHeight = ws.getHeight() / 2;
        float myCenter = ws.getTop() + halfHeight;
        float cellTopFromCenter = halfHeight - ws.getChildAt(0).getTop();
        float actualCellTop = myCenter - cellTopFromCenter * scale;
        return new ScaleAndTranslation(scale, 0, (desiredCellTop - actualCellTop) / scale);
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        return 0.5f;
    }

    @Override
    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(1, 0, 0);
    }

    @Override
    public float getWorkspaceBackgroundAlpha(Launcher launcher) {
        return 0.2f;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return (super.getVisibleElements(launcher) | HOTSEAT_ICONS) & ~TASKBAR;
    }
}
