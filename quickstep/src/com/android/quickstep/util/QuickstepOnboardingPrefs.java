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

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL_APPS_EDU;
import static com.android.launcher3.AbstractFloatingView.getOpenView;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.util.NavigationMode.NO_BUTTON;

import android.content.SharedPreferences;

import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.appprediction.AppsDividerView;
import com.android.launcher3.hybridhotseat.HotseatPredictionController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.util.OnboardingPrefs;
import com.android.quickstep.views.AllAppsEduView;

/**
 * Extends {@link OnboardingPrefs} for quickstep-specific onboarding data.
 */
public class QuickstepOnboardingPrefs extends OnboardingPrefs<QuickstepLauncher> {

    public QuickstepOnboardingPrefs(QuickstepLauncher launcher, SharedPreferences sharedPrefs) {
        super(launcher, sharedPrefs);

        StateManager<LauncherState> stateManager = launcher.getStateManager();
        if (!getBoolean(HOME_BOUNCE_SEEN)) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    boolean swipeUpEnabled =
                            DisplayController.getNavigationMode(mLauncher).hasGestures;
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

        if (!Utilities.isRunningInTestHarness()
                && !hasReachedMaxCount(HOTSEAT_DISCOVERY_TIP_COUNT)) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                boolean mFromAllApps = false;

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    mFromAllApps = mLauncher.getStateManager().getCurrentStableState() == ALL_APPS;
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    HotseatPredictionController client = mLauncher.getHotseatPredictionController();
                    if (mFromAllApps && finalState == NORMAL && client.hasPredictions()) {
                        if (!mLauncher.getDeviceProfile().isTablet
                                && incrementEventCount(HOTSEAT_DISCOVERY_TIP_COUNT)) {
                            client.showEdu();
                            stateManager.removeStateListener(this);
                        }
                    }
                }
            });
        }

        if (DisplayController.getNavigationMode(launcher) == NO_BUTTON) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                private static final int MAX_NUM_SWIPES_TO_TRIGGER_EDU = 3;

                // Counts the number of consecutive swipes on nav bar without moving screens.
                private int mCount = 0;
                private boolean mShouldIncreaseCount;

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    if (toState == NORMAL) {
                        return;
                    }
                    mShouldIncreaseCount = toState == HINT_STATE
                            && launcher.getWorkspace().getNextPage() == Workspace.DEFAULT_PAGE;
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    if (finalState == NORMAL) {
                        if (mCount >= MAX_NUM_SWIPES_TO_TRIGGER_EDU) {
                            if (getOpenView(mLauncher, TYPE_ALL_APPS_EDU) == null) {
                                AllAppsEduView.show(launcher);
                            }
                            mCount = 0;
                        }
                        return;
                    }

                    if (mShouldIncreaseCount && finalState == HINT_STATE) {
                        mCount++;
                    } else {
                        mCount = 0;
                    }

                    if (finalState == ALL_APPS) {
                        AllAppsEduView view = getOpenView(mLauncher, TYPE_ALL_APPS_EDU);
                        if (view != null) {
                            view.close(false);
                        }
                    }
                }
            });
        }

        if (!hasReachedMaxCount(ALL_APPS_VISITED_COUNT)) {
            mLauncher.getStateManager().addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    if (finalState == ALL_APPS) {
                        incrementEventCount(ALL_APPS_VISITED_COUNT);
                        return;
                    }

                    boolean hasReachedMaxCount = hasReachedMaxCount(ALL_APPS_VISITED_COUNT);
                    mLauncher.getAppsView().getFloatingHeaderView().findFixedRowByType(
                            AppsDividerView.class).setShowAllAppsLabel(!hasReachedMaxCount);
                    if (hasReachedMaxCount) {
                        mLauncher.getStateManager().removeStateListener(this);
                    }
                }
            });
        }
    }
}
