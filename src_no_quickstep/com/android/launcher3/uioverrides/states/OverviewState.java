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

import static com.android.launcher3.logging.StatsLogManager.LAUNCHER_STATE_OVERVIEW;

import android.content.Context;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.util.Themes;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    public OverviewState(int id) {
        super(id, LAUNCHER_STATE_OVERVIEW, FLAG_DISABLE_RESTORE);
    }

    @Override
    public int getTransitionDuration(Context context, boolean isToState) {
        return 250;
    }

    public static OverviewState newBackgroundState(int id) {
        return new OverviewState(id);
    }

    public static OverviewState newSwitchState(int id) {
        return new OverviewState(id);
    }

    /**
     *  New Overview substate that represents the overview in modal mode (one task shown on its own)
     */
    public static OverviewState newModalTaskState(int id) {
        return new OverviewState(id);
    }

    /**
     *  New Overview substate that represents the overview in modal mode (one task shown on its own)
     */
    public static OverviewState newSplitSelectState(int id) {
        return new OverviewState(id);
    }

    @Override
    public int getWorkspaceScrimColor(Launcher launcher) {
        return Themes.getAttrColor(launcher, R.attr.overviewScrimColor);
    }
}
