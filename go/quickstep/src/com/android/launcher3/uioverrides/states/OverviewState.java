/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.view.View.VISIBLE;

import static com.android.launcher3.LauncherAnimUtils.OVERVIEW_TRANSITION_MS;
import static com.android.launcher3.LauncherAnimUtils.SCALE_PROPERTY;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_SCALE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_OVERVIEW_TRANSLATE_X;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_FADE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_SCALE;
import static com.android.launcher3.anim.AnimatorSetBuilder.ANIM_WORKSPACE_TRANSLATE;
import static com.android.launcher3.anim.Interpolators.ACCEL;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_2;
import static com.android.launcher3.anim.Interpolators.OVERSHOOT_1_7;
import static com.android.launcher3.states.RotationHelper.REQUEST_ROTATE;

import android.content.Context;
import android.view.View;

import com.android.launcher3.DeviceProfile;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.anim.AnimatorSetBuilder;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.views.IconRecentsView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    // Scale recents takes before animating in
    private static final float RECENTS_PREPARE_SCALE = 1.33f;

    private static final int STATE_FLAGS = FLAG_WORKSPACE_ICONS_CAN_BE_DRAGGED
            | FLAG_DISABLE_RESTORE | FLAG_OVERVIEW_UI | FLAG_DISABLE_ACCESSIBILITY;

    public OverviewState(int id) {
        this(id, OVERVIEW_TRANSITION_MS, STATE_FLAGS);
    }

    protected OverviewState(int id, int transitionDuration, int stateFlags) {
        super(id, LauncherLogProto.ContainerType.TASKSWITCHER, transitionDuration, stateFlags);
    }

    @Override
    public ScaleAndTranslation getOverviewScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(1f, 0f, 0f);
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        IconRecentsView recentsView = launcher.getOverviewPanel();
        recentsView.onBeginTransitionToOverview();
        recentsView.setShowStatusBarForegroundScrim(true);
        // Request orientation be set to unspecified, letting the system decide the best
        // orientation.
        launcher.getRotationHelper().setCurrentStateRequest(REQUEST_ROTATE);
    }

    @Override
    public void onStateDisabled(Launcher launcher) {
        IconRecentsView recentsView = launcher.getOverviewPanel();
        recentsView.setShowStatusBarForegroundScrim(false);
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return new PageAlphaProvider(DEACCEL_2) {
            @Override
            public float getPageAlpha(int pageIndex) {
                return 0;
            }
        };
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return NONE;
    }

    @Override
    public float getWorkspaceScrimAlpha(Launcher launcher) {
        return 0.5f;
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.accessibility_desc_recent_apps);
    }

    @Override
    public void onBackPressed(Launcher launcher) {
        // TODO: Add logic to go back to task if coming from a currently running task.
        super.onBackPressed(launcher);
    }

    public static float getDefaultSwipeHeight(Launcher launcher) {
        return getDefaultSwipeHeight(launcher, launcher.getDeviceProfile());
    }

    public static float getDefaultSwipeHeight(Context context, DeviceProfile dp) {
        return dp.allAppsCellHeightPx - dp.allAppsIconTextSizePx;
    }

    @Override
    public void prepareForAtomicAnimation(Launcher launcher, LauncherState fromState,
            AnimatorSetBuilder builder) {
        if (fromState == NORMAL && this == OVERVIEW) {
            if (SysUINavigationMode.getMode(launcher) == SysUINavigationMode.Mode.NO_BUTTON) {
                builder.setInterpolator(ANIM_WORKSPACE_SCALE, ACCEL);
                builder.setInterpolator(ANIM_WORKSPACE_TRANSLATE, ACCEL);
            } else {
                builder.setInterpolator(ANIM_WORKSPACE_SCALE, OVERSHOOT_1_2);
            }
            builder.setInterpolator(ANIM_WORKSPACE_FADE, OVERSHOOT_1_2);
            builder.setInterpolator(ANIM_OVERVIEW_SCALE, OVERSHOOT_1_2);
            builder.setInterpolator(ANIM_OVERVIEW_TRANSLATE_X, OVERSHOOT_1_7);
            builder.setInterpolator(ANIM_OVERVIEW_FADE, OVERSHOOT_1_2);

            View overview = launcher.getOverviewPanel();
            if (overview.getVisibility() != VISIBLE) {
                SCALE_PROPERTY.set(overview, RECENTS_PREPARE_SCALE);
            }
        }
    }

    public static OverviewState newBackgroundState(int id) {
        return new OverviewState(id);
    }

    public static OverviewState newPeekState(int id) {
        return new OverviewState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new OverviewState(id);
    }
}
