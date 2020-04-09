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

import android.content.SharedPreferences;

import com.android.launcher3.BaseQuickstepLauncher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.LauncherStateManager.StateListener;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.quickstep.SysUINavigationMode;

/**
 * Extends {@link OnboardingPrefs} for quickstep-specific onboarding data.
 */
public class QuickstepOnboardingPrefs extends OnboardingPrefs<BaseQuickstepLauncher> {

    public QuickstepOnboardingPrefs(BaseQuickstepLauncher launcher, SharedPreferences sharedPrefs,
            LauncherStateManager stateManager) {
        super(launcher, sharedPrefs, stateManager);

        if (!getBoolean(HOME_BOUNCE_SEEN)) {
            mStateManager.addStateListener(new StateListener() {
                @Override
                public void onStateTransitionStart(LauncherState toState) { }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    boolean swipeUpEnabled = SysUINavigationMode.INSTANCE
                            .get(mLauncher).getMode().hasGestures;
                    LauncherState prevState = mStateManager.getLastState();

                    if (((swipeUpEnabled && finalState == OVERVIEW) || (!swipeUpEnabled
                            && finalState == ALL_APPS && prevState == NORMAL) ||
                            hasReachedMaxCount(HOME_BOUNCE_COUNT))) {
                        mSharedPrefs.edit().putBoolean(HOME_BOUNCE_SEEN, true).apply();
                        mStateManager.removeStateListener(this);
                    }
                }
            });
        }

        if (!getBoolean(SHELF_BOUNCE_SEEN)) {
            mStateManager.addStateListener(new StateListener() {
                @Override
                public void onStateTransitionStart(LauncherState toState) { }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    LauncherState prevState = mStateManager.getLastState();

                    if ((finalState == ALL_APPS && prevState == OVERVIEW) ||
                            hasReachedMaxCount(SHELF_BOUNCE_COUNT)) {
                        mSharedPrefs.edit().putBoolean(SHELF_BOUNCE_SEEN, true).apply();
                        mStateManager.removeStateListener(this);
                    }
                }
            });
        }
    }
}
