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

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.compat.AccessibilityManagerCompat.isAccessibilityEnabled;

import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    // The percent to shrink the workspace during overview mode
    private static final float SCALE_FACTOR = 0.7f;

    private static final int STATE_FLAGS = FLAG_SHOW_SCRIM | FLAG_MULTI_PAGE |
            FLAG_DISABLE_PAGE_CLIPPING | FLAG_PAGE_BACKGROUNDS;

    public OverviewState(int id) {
        super(id, ContainerType.WORKSPACE, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    @Override
    public float[] getWorkspaceScaleAndTranslation(Launcher launcher) {
        DeviceProfile grid = launcher.getDeviceProfile();
        Workspace ws = launcher.getWorkspace();
        Rect insets = launcher.getDragLayer().getInsets();

        int overviewButtonBarHeight = OverviewPanel.getButtonBarHeight(launcher);
        int scaledHeight = (int) (SCALE_FACTOR * ws.getNormalChildHeight());
        int workspaceTop = insets.top + grid.workspacePadding.top;
        int workspaceBottom = ws.getHeight() - insets.bottom - grid.workspacePadding.bottom;
        int overviewTop = insets.top;
        int overviewBottom = ws.getHeight() - insets.bottom - overviewButtonBarHeight;
        int workspaceOffsetTopEdge =
                workspaceTop + ((workspaceBottom - workspaceTop) - scaledHeight) / 2;
        int overviewOffsetTopEdge = overviewTop + (overviewBottom - overviewTop - scaledHeight) / 2;
        return new float[] {SCALE_FACTOR, 0, -workspaceOffsetTopEdge + overviewOffsetTopEdge };
    }

    @Override
    public float getHoseatAlpha(Launcher launcher) {
        return 0;
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        launcher.getWorkspace().setPageRearrangeEnabled(true);

        if (isAccessibilityEnabled(launcher)) {
            launcher.getOverviewPanel().getChildAt(0).performAccessibilityAction(
                    AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
        }
    }

    @Override
    public void onStateDisabled(Launcher launcher) {
        launcher.getWorkspace().setPageRearrangeEnabled(false);
    }

    @Override
    public View getFinalFocus(Launcher launcher) {
        return launcher.getOverviewPanel();
    }
}
