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
import com.android.launcher3.views.ActivityContext;
import com.android.launcher3.views.ScrimColors;
import com.android.quickstep.util.BaseDepthController;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.util.concurrent.TimeUnit;

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
        // Sora: the home screen holds still while the drawer comes over it.
        //
        // Shrinking it away was the old way of saying "this is behind now", and
        // it fights what the drawer's own glass already says far better: the
        // blur is pulled up over the workspace like a sheet, and a sheet does
        // not push what it covers backwards. Left scaling, the icons drift
        // inwards underneath the glass while the glass itself stays put, and the
        // two motions read as two unrelated things happening at once.
        return new ScaleAndTranslation(NO_SCALE, NO_OFFSET, NO_OFFSET);
    }

    @Override
    public ScaleAndTranslation getHotseatScaleAndTranslation(Launcher launcher) {
        if (launcher.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            return getWorkspaceScaleAndTranslation(launcher);
        } else {
            // The hotseat holds still with the rest of the home screen; only the
            // translation the drawer needs is kept.
            ScaleAndTranslation overviewScaleAndTranslation = LauncherState.OVERVIEW
                    .getWorkspaceScaleAndTranslation(launcher);
            return new ScaleAndTranslation(
                    NO_SCALE,
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
        // Sora: every branch goes through the drawer's own tint, which is none.
        //
        // Two of these used to hand back AllAppsScrimColor as it stands -- a 40%
        // grey -- and that was the flat layer still sitting over the drawer's
        // frosted wallpaper. DRAWER_TINT_ALPHA has said for a while that the
        // drawer is blurred wallpaper and nothing else; it was simply never
        // asked on this path, so the constant described a design the launcher
        // was not actually following. Routing all three through
        // getAllAppsBackgroundColor makes that one number true everywhere, and
        // leaves it as the single place to put a wash back if one is ever
        // wanted. Contrast for the labels comes from their shadow now -- see
        // overrideAllAppsTextColor -- rather than from a colour laid over the
        // whole screen to give them something to sit on.
        int defaultColor;
        if (!launcher.getDeviceProfile().shouldShowAllAppsOnSheet()) {
            defaultColor = ColorTokens.AllAppsScrimColor.resolveColor(launcher);
        } else if (!Flags.allAppsBlur()) {
            // If there's a sheet but no blur, use the old scrim color.
            defaultColor = ColorTokens.WidgetsPickerScrim.resolveColor(launcher);
        } else {
            defaultColor = ColorTokens.AllAppsScrimColor.resolveColor(launcher);
        }
        int backgroundColor = LawnchairUtilsKt.getAllAppsBackgroundColor(launcher, defaultColor);
        return new ScrimColors(backgroundColor, /* foregroundColor */ Color.TRANSPARENT);
    }
}
