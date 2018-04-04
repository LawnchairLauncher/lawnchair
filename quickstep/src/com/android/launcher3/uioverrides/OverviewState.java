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
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.states.RotationHelper.REQUEST_ROTATE;

import android.graphics.Rect;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Workspace;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.views.RecentsView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_SHOW_SCRIM | FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI;

    public OverviewState(int id) {
        this(id, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    protected OverviewState(int id, int transitionDuration, int stateFlags) {
        super(id, ContainerType.TASKSWITCHER, transitionDuration, stateFlags);
    }

    @Override
    public float[] getWorkspaceScaleAndTranslation(Launcher launcher) {
        Rect pageRect = new Rect();
        RecentsView.getPageRect(launcher.getDeviceProfile(), launcher, pageRect);

        if (launcher.getWorkspace().getNormalChildWidth() <= 0 || pageRect.isEmpty()) {
            return super.getWorkspaceScaleAndTranslation(launcher);
        }

        return getScaleAndTranslationForPageRect(launcher, pageRect);
    }

    @Override
    public float[] getOverviewScaleAndTranslationYFactor(Launcher launcher) {
        return new float[] {1f, 0f};
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
    public void onStateTransitionEnd(Launcher launcher) {
        launcher.getRotationHelper().setCurrentStateRequest(REQUEST_ROTATE);
    }

    @Override
    public View getFinalFocus(Launcher launcher) {
        return launcher.getOverviewPanel();
    }

    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return new PageAlphaProvider(DEACCEL_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return 0;
            }
        };
    }

    public static float[] getScaleAndTranslationForPageRect(Launcher launcher, Rect pageRect) {
        Workspace ws = launcher.getWorkspace();
        float childWidth = ws.getNormalChildWidth();

        float scale = pageRect.width() / childWidth;
        Rect insets = launcher.getDragLayer().getInsets();

        float halfHeight = ws.getExpectedHeight() / 2;
        float childTop = halfHeight - scale * (halfHeight - ws.getPaddingTop() - insets.top);
        float translationY = pageRect.top - childTop;

        return new float[] {scale, 0, translationY};
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        if (launcher.getDeviceProfile().isVerticalBarLayout()) {
            return NONE;
        } else {
            return HOTSEAT_SEARCH_BOX | DRAG_HANDLE_INDICATOR |
                    (launcher.getAppsView().getFloatingHeaderView().hasVisibleContent()
                            ? ALL_APPS_HEADER_EXTRA : HOTSEAT_ICONS);
        }
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        if ((getVisibleElements(launcher) & ALL_APPS_HEADER_EXTRA) == 0) {
            // We have no all apps content, so we're still at the fully down progress.
            return super.getVerticalProgress(launcher);
        }
        return 1 - (getDefaultSwipeHeight(launcher)
                / launcher.getAllAppsController().getShiftRange());
    }

    public static float getDefaultSwipeHeight(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx;
    }
}
