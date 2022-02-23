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
package com.android.launcher3.uioverrides.states;

import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;

import android.content.Context;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_WORKSPACE_INACCESSIBLE | FLAG_CLOSE_POPUPS;

    public AllAppsState(int id) {
        super(id, LAUNCHER_STATE_ALLAPPS, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(Context context) {
        return 320;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getAppsView().getDescription();
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        return 0f;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        ScaleAndTranslation scaleAndTranslation =
                new ScaleAndTranslation(NO_SCALE, NO_OFFSET, NO_OFFSET);
        if (launcher.getDeviceProfile().isTablet) {
            scaleAndTranslation.scale = 0.97f;
        } else {
            ScaleAndTranslation overviewScaleAndTranslation = LauncherState.OVERVIEW
                    .getWorkspaceScaleAndTranslation(launcher);
            scaleAndTranslation.translationX = overviewScaleAndTranslation.translationX;
            scaleAndTranslation.translationY = overviewScaleAndTranslation.translationY;
        }
        return scaleAndTranslation;
    }

    @Override
    public boolean isTaskbarStashed(Launcher launcher) {
        return true;
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        // The scrim fades in at approximately 50% of the swipe gesture.
        // This means that the depth should be greater than 1, in order to fully zoom out.
        return 2f;
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        PageAlphaProvider superPageAlphaProvider = super.getWorkspacePageAlphaProvider(launcher);
        return new PageAlphaProvider(DEACCEL_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return launcher.getDeviceProfile().isTablet
                        ? superPageAlphaProvider.getPageAlpha(pageIndex)
                        : 0;
            }
        };
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return ALL_APPS_CONTENT;
    }

    @Override
    public LauncherState getHistoryForState(LauncherState previousState) {
        return previousState == OVERVIEW ? OVERVIEW : NORMAL;
    }

    @Override
    public int getWorkspaceScrimColor(Launcher launcher) {
        return launcher.getDeviceProfile().isTablet
                ? launcher.getResources().getColor(R.color.widgets_picker_scrim)
                : Themes.getAttrColor(launcher, R.attr.allAppsScrimColor);
    }
}
