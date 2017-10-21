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
package com.android.launcher3.states;

import static com.android.launcher3.LauncherAnimUtils.ALL_APPS_TRANSITION_MS;

import android.view.View;

import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    public static final String APPS_VIEW_SHOWN = "launcher.apps_view_shown";

    private static final int STATE_FLAGS = FLAG_DISABLE_ACCESSIBILITY | FLAG_HAS_SPRING;

    public AllAppsState(int id) {
        super(id, ContainerType.ALLAPPS, ALL_APPS_TRANSITION_MS, 0f, STATE_FLAGS);
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        if (!launcher.getSharedPrefs().getBoolean(APPS_VIEW_SHOWN, false)) {
            launcher.getSharedPrefs().edit().putBoolean(APPS_VIEW_SHOWN, true).apply();
        }
    }

    @Override
    public View getFinalFocus(Launcher launcher) {
        return launcher.getAppsView();
    }
}
