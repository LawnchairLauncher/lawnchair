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

import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.quickstep.RecentsView;

/**
 * Definition for overview state
 */
public class OverviewState extends LauncherState {

    // The percent to shrink the workspace during overview mode
    public static final float SCALE_FACTOR = 0.7f;

    private static final int STATE_FLAGS = FLAG_SHOW_SCRIM | FLAG_MULTI_PAGE;

    public OverviewState(int id) {
        super(id, ContainerType.WORKSPACE, OVERVIEW_TRANSITION_MS, 1f, STATE_FLAGS);
    }

    @Override
    public float[] getWorkspaceScaleAndTranslation(Launcher launcher) {
        // TODO: Find a better transition
        return new float[] {0f, 0};
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
    public View getFinalFocus(Launcher launcher) {
        return launcher.getOverviewPanel();
    }
}
