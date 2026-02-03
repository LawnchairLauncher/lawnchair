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
import static com.android.launcher3.Flags.enableScalingRevealHomeAnimation;
import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_ALLAPPS;

import android.content.Context;
import android.graphics.Color;

import app.lawnchair.theme.color.tokens.ColorTokens;
import com.android.internal.jank.Cuj;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Flags;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;
import com.android.quickstep.util.BaseDepthController;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.util.concurrent.TimeUnit;

import app.lawnchair.LawnchairLauncher;
import app.lawnchair.util.LawnchairUtilsKt;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final int STATE_FLAGS =
            FLAG_WORKSPACE_INACCESSIBLE | FLAG_CLOSE_POPUPS | FLAG_HOTSEAT_INACCESSIBLE;
    private static final long BACK_CUJ_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);


    public AllAppsState(int id) {
        super(id, LAUNCHER_STATE_ALLAPPS, STATE_FLAGS);
    }

    @Override
    public int getTransitionDuration(ActivityContext context, boolean isToState) {
        return isToState
                ? context.getDeviceProfile().allAppsOpenDuration
                : context.getDeviceProfile().allAppsCloseDuration;
    }

    @Override
    public void onBackStarted(Launcher launcher) {
        // Because the back gesture can take longer time depending on when user release the finger,
        // we pass BACK_CUJ_TIMEOUT_MS as timeout to the jank monitor.
        InteractionJankMonitorWrapper.begin(launcher.getAppsView(),
                Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK, BACK_CUJ_TIMEOUT_MS);
        super.onBackStarted(launcher);
    }

    @Override
    public void onBackInvoked(Launcher launcher) {
        // In predictive back swipe, onBackInvoked() will be called after onBackStarted().
        // In 3 button mode, onBackStarted() is not called but onBackInvoked() will be called.
        // Thus In onBackInvoked(), we should only begin instrumenting if we didn't call
        // onBackStarted() to start instrumenting CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK.
        if (!InteractionJankMonitorWrapper.isInstrumenting(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK)) {
            InteractionJankMonitorWrapper.begin(
                    launcher.getAppsView(), Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
        }
        super.onBackInvoked(launcher);
    }

    /** Called when predictive back swipe is cancelled. */
    @Override
    public void onBackCancelled(Launcher launcher) {
        super.onBackCancelled(launcher);
        InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
    }

    @Override
    protected void onBackAnimationCompleted(boolean success) {
        if (success) {
            // Animation was successful.
            InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
        } else {
            // Animation was canceled.
            InteractionJankMonitorWrapper.cancel(Cuj.CUJ_LAUNCHER_CLOSE_ALL_APPS_BACK);
        }
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getAppsView().getDescription();
    }

    @Override
    public int getTitle() {
        return R.string.all_apps_list_label;
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
        if (launcher.getDeviceProfile().shouldShowAllAppsOnSheet()) {
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
    protected <DEVICE_PROFILE_CONTEXT extends Context & ActivityContext>
            float getDepthUnchecked(DEVICE_PROFILE_CONTEXT context) {
        if (context.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            return context.getDeviceProfile().getBottomSheetProfile().getBottomSheetDepth();
        } else {
            // The scrim fades in at approximately 50% of the swipe gesture.
            if (enableScalingRevealHomeAnimation()) {
                // This means that the depth should be twice of what we want, in order to fully zoom
                // out during the visible portion of the animation.
                return BaseDepthController.DEPTH_60_PERCENT;
            } else {
                // This means that the depth should be greater than 1, in order to fully zoom out.
                return 2f;
            }
        }
    }

    @Override
    public boolean shouldBlurWorkspace(LauncherState targetState) {
        return targetState == ALL_APPS || targetState == NORMAL;
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        PageAlphaProvider superPageAlphaProvider = super.getWorkspacePageAlphaProvider(launcher);
        return new PageAlphaProvider(DECELERATE_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return isWorkspaceVisible(launcher.getDeviceProfile())
                        ? superPageAlphaProvider.getPageAlpha(pageIndex)
                        : 0;
            }
        };
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        int elements = ALL_APPS_CONTENT | FLOATING_SEARCH_BAR;
        if (isWorkspaceVisible(launcher.getDeviceProfile())) {
            elements |= HOTSEAT_ICONS;
        }
        return elements;
    }

    private static boolean isWorkspaceVisible(DeviceProfile deviceProfile) {
        return deviceProfile.getDeviceProperties().isTablet() || (Flags.allAppsSheetForHandheld() && Flags.allAppsBlur());
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
        return dp.getDeviceProperties().isPhone() && !dp.getDeviceProperties().isLandscape();
    }

    @Override
    public ScrimColors getWorkspaceScrimColor(Launcher launcher) {
        int backgroundColor;
        if (!launcher.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            // Always use an opaque scrim if there's no sheet.
            // Lawnchair-TODO-Colour: Check R.color.materialColorSurfaceDim
            backgroundColor = launcher.getResources().getColor(R.color.materialColorSurfaceDim);
        } else if (!Flags.allAppsBlur()) {
            // If there's a sheet but no blur, use the old scrim color.
            backgroundColor = ColorTokens.WidgetsPickerScrim.resolveColor(launcher);
        } else {
            backgroundColor = LawnchairUtilsKt.getAllAppsScrimColor(launcher);
        }
        return new ScrimColors(backgroundColor, /* foregroundColor */ Color.TRANSPARENT);
    }
}
