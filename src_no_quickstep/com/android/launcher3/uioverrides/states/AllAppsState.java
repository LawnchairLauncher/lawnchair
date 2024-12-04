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

import static com.android.app.animation.Interpolators.DECELERATE;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;

import android.content.Context;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final float PARALLAX_COEFFICIENT = .125f;

    private static final int STATE_FLAGS = FLAG_WORKSPACE_INACCESSIBLE;

    public AllAppsState(int id) {
        super(id, LAUNCHER_STATE_ALLAPPS, STATE_FLAGS);
    }

    @Override
    public <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext>
    int getTransitionDuration(DEVICE_PROFILE_CONTEXT context, boolean isToState) {
        return isToState
                ? context.getDeviceProfile().allAppsOpenDuration
                : context.getDeviceProfile().allAppsCloseDuration;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.all_apps_button_label);
    }

    @Override
    public int getTitle() {
        return R.string.all_apps_label;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return ALL_APPS_CONTENT;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(launcher.getDeviceProfile().workspaceContentScale, NO_OFFSET,
                NO_OFFSET);
    }

    @Override
    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        if (launcher.getDeviceProfile().isTablet) {
            return getWorkspaceScaleAndTranslation(launcher);
        } else {
            ScaleAndTranslation overviewScaleAndTranslation = LauncherState.OVERVIEW
                    .getWorkspaceScaleAndTranslation(launcher);
            return new ScaleAndTranslation(
                    launcher.getDeviceProfile().workspaceContentScale,
                    overviewScaleAndTranslation.translationX,
                    overviewScaleAndTranslation.translationY);
        }
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        PageAlphaProvider superPageAlphaProvider = super.getWorkspacePageAlphaProvider(launcher);
        return new PageAlphaProvider(DECELERATE) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return launcher.getDeviceProfile().isTablet
                        ? superPageAlphaProvider.getPageAlpha(pageIndex)
                        : 0;
            }
        };
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        return 0f;
    }

    @Override
    public int getWorkspaceScrimColor(Launcher launcher) {
        return launcher.getDeviceProfile().isTablet
                ? launcher.getResources().getColor(R.color.widgets_picker_scrim)
                : Themes.getAttrColor(launcher, R.attr.allAppsScrimColor);
    }
}
