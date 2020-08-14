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
import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.quickstep.SysUINavigationMode.Mode.NO_BUTTON;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;

import android.content.Context;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.SysUINavigationMode;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_WORKSPACE_INACCESSIBLE | FLAG_CLOSE_POPUPS;

    private static final PageAlphaProvider PAGE_ALPHA_PROVIDER = new PageAlphaProvider(DEACCEL_2) {
        @Override
        public float getPageAlpha(int pageIndex) {
            return 0;
        }
    };

    public AllAppsState(int id) {
        super(id, ContainerType.ALLAPPS, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(Context context) {
        return 320;
    }

    @Override
    public String getDescription(Launcher launcher) {
        AllAppsContainerView appsView = launcher.getAppsView();
        return appsView.getDescription();
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        return 0f;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        ScaleAndTranslation scaleAndTranslation = LauncherState.OVERVIEW
                .getWorkspaceScaleAndTranslation(launcher);
        if (SysUINavigationMode.getMode(launcher) == NO_BUTTON && !ENABLE_OVERVIEW_ACTIONS.get()) {
            float normalScale = 1;
            // Scale down halfway to where we'd be in overview, to prepare for a potential pause.
            scaleAndTranslation.scale = (scaleAndTranslation.scale + normalScale) / 2;
        } else {
            scaleAndTranslation.scale = 1;
        }
        return scaleAndTranslation;
    }

    @Override
    protected float getDepthUnchecked(Context context) {
        return 1f;
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return PAGE_ALPHA_PROVIDER;
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return ALL_APPS_HEADER | ALL_APPS_HEADER_EXTRA | ALL_APPS_CONTENT;
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        float offset = ENABLE_OVERVIEW_ACTIONS.get() && removeShelfFromOverview(launcher) ? 1 : 0;
        return new float[] {0.9f, offset};
    }

    @Override
    public LauncherState getHistoryForState(LauncherState previousState) {
        return previousState == OVERVIEW ? OVERVIEW : NORMAL;
    }
}
