/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.quickstep.util;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.config.FeatureFlags.ENABLE_OVERVIEW_ACTIONS;
import static com.android.quickstep.SysUINavigationMode.removeShelfFromOverview;

import android.content.SharedPreferences;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.quickstep.SysUINavigationMode;

/**
 * Extends {@link OnboardingPrefs} for quickstep-specific onboarding data.
 */
public class QuickstepOnboardingPrefs extends OnboardingPrefs<BaseQuickstepLauncher> {

    public QuickstepOnboardingPrefs(BaseQuickstepLauncher launcher, SharedPreferences sharedPrefs) {
        super(launcher, sharedPrefs);

        StateManager<LauncherState> stateManager = launcher.getStateManager();
        if (!getBoolean(HOME_BOUNCE_SEEN)) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    boolean swipeUpEnabled = SysUINavigationMode.INSTANCE
                            .get(mLauncher).getMode().hasGestures;
                    LauncherState prevState = stateManager.getLastState();

                    if (((swipeUpEnabled && finalState == OVERVIEW) || (!swipeUpEnabled
                            && finalState == ALL_APPS && prevState == NORMAL) ||
                            hasReachedMaxCount(HOME_BOUNCE_COUNT))) {
                        mSharedPrefs.edit().putBoolean(HOME_BOUNCE_SEEN, true).apply();
                        stateManager.removeStateListener(this);
                    }
                }
            });
        }

        boolean shelfBounceSeen = getBoolean(SHELF_BOUNCE_SEEN);
        if (!shelfBounceSeen && ENABLE_OVERVIEW_ACTIONS.get()
                && removeShelfFromOverview(launcher)) {
            // There's no shelf in overview, so don't bounce it (can't get to all apps anyway).
            shelfBounceSeen = true;
            mSharedPrefs.edit().putBoolean(SHELF_BOUNCE_SEEN, shelfBounceSeen).apply();
        }
        if (!shelfBounceSeen) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    LauncherState prevState = stateManager.getLastState();

                    if ((finalState == ALL_APPS && prevState == OVERVIEW) ||
                            hasReachedMaxCount(SHELF_BOUNCE_COUNT)) {
                        mSharedPrefs.edit().putBoolean(SHELF_BOUNCE_SEEN, true).apply();
                        stateManager.removeStateListener(this);
                    }
                }
            });
        }

        if (!hasReachedMaxCount(ALL_APPS_COUNT)) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    if (finalState == ALL_APPS) {
                        if (incrementEventCount(ALL_APPS_COUNT)) {
                            stateManager.removeStateListener(this);
                            mLauncher.getScrimView().updateDragHandleVisibility();
                        }
                    }
                }
            });
        }
    }
}
