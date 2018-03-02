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
import static com.android.launcher3.anim.Interpolators.ACCEL_2;

import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Workspace;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.RecentsView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_SHOW_SCRIM | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_PAGE_BACKGROUNDS | FLAG_OVERVIEW_UI;

    public OverviewState(int id) {
        this(id, STATE_FLAGS);
    }

    protected OverviewState(int id, int stateFlags) {
        super(id, ContainerType.TASKSWITCHER, OVERVIEW_TRANSITION_MS, stateFlags);
    }

    @Override
    public float[] getWorkspaceScaleAndTranslation(Launcher launcher) {
        Rect pageRect = new Rect();
        RecentsView.getScaledDownPageRect(launcher.getDeviceProfile(), launcher, pageRect);
        RecentsView rv = launcher.getOverviewPanel();

        if (launcher.getWorkspace().getNormalChildWidth() <= 0 || pageRect.isEmpty()) {
            return super.getWorkspaceScaleAndTranslation(launcher);
        }

        float overlap = 0;
        if (rv.getCurrentPage() >= rv.getFirstTaskIndex()) {
            overlap = launcher.getResources().getDimension(R.dimen.workspace_overview_offset_x);
        }
        return getScaleAndTranslationForPageRect(launcher, overlap, pageRect);
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        RecentsView rv = launcher.getOverviewPanel();
        rv.setOverviewStateEnabled(true);
    }

    @Override
    public void onStateDisabled(Launcher launcher) {
        RecentsView rv = launcher.getOverviewPanel();
        rv.setOverviewStateEnabled(false);
    }

    @Override
    public View getFinalFocus(Launcher launcher) {
        return launcher.getOverviewPanel();
    }

    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        final int centerPage = launcher.getWorkspace().getNextPage();
        return new PageAlphaProvider(ACCEL_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return  pageIndex != centerPage ? 0 : 1f;
            }
        };
    }

    public static float[] getScaleAndTranslationForPageRect(Launcher launcher, float offsetX,
            Rect pageRect) {
        Workspace ws = launcher.getWorkspace();
        float childWidth = ws.getNormalChildWidth();
        float childHeight = ws.getNormalChildHeight();

        float scale = pageRect.height() / childHeight;
        Rect insets = launcher.getDragLayer().getInsets();

        float halfHeight = ws.getExpectedHeight() / 2;
        float childTop = halfHeight - scale * (halfHeight - ws.getPaddingTop() - insets.top);
        float translationY = pageRect.top - childTop;

        // Align the workspace horizontally centered with the task rect
        float halfWidth = ws.getExpectedWidth() / 2;
        float childCenter = halfWidth -
                scale * (halfWidth - ws.getPaddingLeft() - insets.left - childWidth / 2);
        float translationX = pageRect.centerX() - childCenter;

        if (launcher.<RecentsView>getOverviewPanel().isRtl()) {
            translationX -= offsetX / scale;
        } else {
            translationX += offsetX / scale;
        }

        return new float[] {scale, translationX, translationY};
    }
}
