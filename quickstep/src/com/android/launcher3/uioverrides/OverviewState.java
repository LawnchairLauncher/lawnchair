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

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.RecentsView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    public static final float WORKSPACE_SCALE_ON_SCROLL = 0.9f;

    private static final int STATE_FLAGS = FLAG_SHOW_SCRIM | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED;

    public OverviewState(int id) {
        super(id, ContainerType.WORKSPACE, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    @Override
    public float[] getWorkspaceScaleAndTranslation(Launcher launcher) {
        Rect pageRect = new Rect();
        RecentsView.getPageRect(launcher, pageRect);
        if (launcher.getWorkspace().getNormalChildWidth() <= 0 || pageRect.isEmpty()) {
            return super.getWorkspaceScaleAndTranslation(launcher);
        }

        RecentsView rv = launcher.getOverviewPanel();
        float overlap = 0;
        if (rv.getCurrentPage() >= rv.getFirstTaskIndex()) {
            Utilities.scaleRectAboutCenter(pageRect, WORKSPACE_SCALE_ON_SCROLL);
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
    public float getVerticalProgress(Launcher launcher) {
        DeviceProfile grid = launcher.getDeviceProfile();
        if (!grid.isVerticalBarLayout()) {
            return 1f;
        }

        float total = grid.heightPx;
        float searchHeight = total - grid.availableHeightPx +
                launcher.getResources().getDimension(R.dimen.all_apps_search_box_full_height);
        return 1 - (searchHeight / total);
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

        Rect insets = launcher.getDragLayer().getInsets();
        float scale = Math.min(pageRect.width() / childWidth, pageRect.height() / childHeight);

        float halfHeight = ws.getHeight() / 2;
        float childTop = halfHeight - scale * (halfHeight - ws.getPaddingTop() - insets.top);
        float translationY = pageRect.top - childTop;

        float halfWidth = ws.getWidth() / 2;
        float translationX;
        if (Utilities.isRtl(launcher.getResources())) {
            float childRight = halfWidth + scale * (halfWidth - ws.getPaddingRight() - insets.right);
            translationX = childRight - pageRect.right - offsetX / scale;
        } else {
            float childLeft = halfWidth - scale * (halfWidth - ws.getPaddingLeft() - insets.left);
            translationX = pageRect.left - childLeft + offsetX / scale;
        }

        return new float[] {scale, translationX, translationY};
    }
}
