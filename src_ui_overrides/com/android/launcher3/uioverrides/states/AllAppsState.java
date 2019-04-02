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

import static com.android.launcher3.LauncherAnimUtils.ALL_APPS_TRANSITION_MS;
import static com.android.launcher3.allapps.DiscoveryBounce.HOME_BOUNCE_SEEN;
import static com.android.launcher3.anim.Interpolators.DEACCEL_2;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;

/**
 * Definition for AllApps state
 */
public class AllAppsState extends LauncherState {

    private static final float PARALLAX_COEFFICIENT = .125f;

    private static final int STATE_FLAGS = FLAG_DISABLE_ACCESSIBILITY;

    private static final PageAlphaProvider PAGE_ALPHA_PROVIDER = new PageAlphaProvider(DEACCEL_2) {
        @Override
        public float getPageAlpha(int pageIndex) {
            return 0;
        }
    };

    public AllAppsState(int id) {
        super(id, ContainerType.ALLAPPS, ALL_APPS_TRANSITION_MS, STATE_FLAGS);
    }

    @Override
    public void onStateEnabled(Launcher launcher) {
        if (!launcher.getSharedPrefs().getBoolean(HOME_BOUNCE_SEEN, false)) {
            launcher.getSharedPrefs().edit().putBoolean(HOME_BOUNCE_SEEN, true).apply();
        }

        AbstractFloatingView.closeAllOpenViews(launcher);
        dispatchWindowStateChanged(launcher);
    }

    @Override
    public String getDescription(Launcher launcher) {
        return launcher.getString(R.string.all_apps_button_label);
    }

    @Override
    public int getVisibleElements(Launcher launcher) {
        return ALL_APPS_HEADER | ALL_APPS_CONTENT;
    }

    @Override
    public ScaleAndTranslation getWorkspaceScaleAndTranslation(Launcher launcher) {
        return new ScaleAndTranslation(1f, 0,
                -launcher.getAllAppsController().getShiftRange() * PARALLAX_COEFFICIENT);
    }

    @Override
    public PageAlphaProvider getWorkspacePageAlphaProvider(Launcher launcher) {
        return PAGE_ALPHA_PROVIDER;
    }

    @Override
    public float getVerticalProgress(Launcher launcher) {
        return 0f;
    }
}
