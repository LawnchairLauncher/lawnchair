/**
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

package com.android.launcher3.appprediction;

import static com.android.launcher3.AbstractFloatingView.TYPE_DISCOVERY_BOUNCE;
import static com.android.launcher3.AbstractFloatingView.TYPE_ON_BOARD_POPUP;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.quickstep.logging.UserEventDispatcherExtension.ALL_APPS_PREDICTION_TIPS;

import android.os.UserManager;

import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.allapps.FloatingHeaderView;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.views.ArrowTipView;
import com.android.systemui.shared.system.LauncherEventUtil;

/**
 * ArrowTip helper aligned just above prediction apps, shown to users that enter all apps for the
 * first time.
 */
public class AllAppsTipView {

    private static final String ALL_APPS_TIP_SEEN = "launcher.all_apps_tip_seen";

    private static boolean showAllAppsTipIfNecessary(Launcher launcher) {
        FloatingHeaderView floatingHeaderView = launcher.getAppsView().getFloatingHeaderView();
        if (!floatingHeaderView.hasVisibleContent()
                || AbstractFloatingView.getOpenView(launcher,
                TYPE_ON_BOARD_POPUP | TYPE_DISCOVERY_BOUNCE) != null
                || !launcher.isInState(ALL_APPS)
                || hasSeenAllAppsTip(launcher)
                || launcher.getSystemService(UserManager.class).isDemoUser()
                || Utilities.IS_RUNNING_IN_TEST_HARNESS) {
            return false;
        }

        int[] coords = new int[2];
        floatingHeaderView.findFixedRowByType(PredictionRowView.class).getLocationOnScreen(coords);
        ArrowTipView arrowTipView = new ArrowTipView(launcher).setOnClosedCallback(() -> {
            launcher.getSharedPrefs().edit().putBoolean(ALL_APPS_TIP_SEEN, true).apply();
            launcher.getUserEventDispatcher().logActionTip(LauncherEventUtil.DISMISS,
                    ALL_APPS_PREDICTION_TIPS);
        });
        arrowTipView.show(launcher.getString(R.string.all_apps_prediction_tip), coords[1]);

        return true;
    }

    private static boolean hasSeenAllAppsTip(Launcher launcher) {
        return launcher.getSharedPrefs().getBoolean(ALL_APPS_TIP_SEEN, false);
    }

    public static void scheduleShowIfNeeded(Launcher launcher) {
        if (!hasSeenAllAppsTip(launcher)) {
            launcher.getStateManager().addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    if (finalState == ALL_APPS) {
                        if (showAllAppsTipIfNecessary(launcher)) {
                            launcher.getStateManager().removeStateListener(this);
                        }
                    }
                }
            });
        }
    }
}
