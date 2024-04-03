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

import static com.android.app.animation.Interpolators.DECELERATE_2;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;

import android.content.Context;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;

import app.lawnchair.LawnchairLauncher;
import app.lawnchair.util.LawnchairUtilsKt;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final int STATE_FLAGS = FLAG_WORKSPACE_INACCESSIBLE | FLAG_CLOSE_POPUPS | FLAG_HOTSEAT_INACCESSIBLE;

    public AllAppsState(int id) {
        super(id, LAUNCHER_STATE_ALLAPPS, STATE_FLAGS);
    }

    @Override
    public <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext> int getTransitionDuration(
            DEVICE_PROFILE_CONTEXT context, boolean isToState) {
        return isToState
                ? context.getDeviceProfile().allAppsOpenDuration
                : context.getDeviceProfile().allAppsCloseDuration;
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
    protected <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext> float getDepthUnchecked(
            DEVICE_PROFILE_CONTEXT context) {
        if (context.getDeviceProfile().isTablet) {
            return context.getDeviceProfile().bottomSheetDepth;
        } else {
            // The scrim fades in at approximately 50% of the swipe gesture.
            // This means that the depth should be greater than 1, in order to fully zoom
            // out.
            return 2f;
        }
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        PageAlphaProvider superPageAlphaProvider = super.getWorkspacePageAlphaProvider(launcher);
        return new PageAlphaProvider(DECELERATE_2) {
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
        int elements = ALL_APPS_CONTENT | FLOATING_SEARCH_BAR;
        // Only add HOTSEAT_ICONS for tablets in ALL_APPS state.
        if (launcher.getDeviceProfile().isTablet) {
            elements |= HOTSEAT_ICONS;
        }
        return elements;
    }

    @Override
    public int getFloatingSearchBarRestingMarginBottom(Launcher launcher) {
        return 0;
    }

    @Override
    public int getFloatingSearchBarRestingMarginStart(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(launcher);
    }

    @Override
    public int getFloatingSearchBarRestingMarginEnd(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.allAppsLeftRightMargin + dp.getAllAppsIconStartMargin(launcher);
    }

    @Override
    public boolean shouldFloatingSearchBarUsePillWhenUnfocused(Launcher launcher) {
        DeviceProfile dp = launcher.getDeviceProfile();
        return dp.isPhone && !dp.isLandscape;
    }

    @Override
    public LauncherState getHistoryForState(LauncherState previousState) {
        return previousState == BACKGROUND_APP ? QUICK_SWITCH_FROM_HOME
                : previousState == OVERVIEW ? OVERVIEW : NORMAL;
    }

    @Override
    public float[] getOverviewScaleAndOffset(Launcher launcher) {
        if (!FeatureFlags.ENABLE_ALL_APPS_FROM_OVERVIEW.get()) {
            return super.getOverviewScaleAndOffset(launcher);
        }
        // This handles the case of returning to the previous app from Overview -> All Apps gesture.
        // This is the start scale/offset of overview that will be used for that transition.
        // TODO (b/283336332): Translate in Y direction (ideally with overview resistance).
        return new float[] {0.5f /* scale */, NO_OFFSET};
    }

    @Override
    public int getWorkspaceScrimColor(Launcher launcher) {
        return LawnchairUtilsKt.getAllAppsScrimColor(launcher);
    }
}
