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

package com.android.launcher3.accessibility;

import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;

import android.app.PendingIntent;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.view.accessibility.AccessibilityManager;
import com.android.launcher3.Launcher;
import com.android.launcher3.LauncherState;
import com.android.launcher3.LauncherStateManager;
import com.android.launcher3.R;

/**
 * Manages the launcher system actions presented to accessibility services.
 */
public class SystemActions {

    /**
     * System Action ID to show all apps.  This ID should follow the ones in
     * com.android.systemui.accessibility.SystemActions.
     */
    private static final int SYSTEM_ACTION_ID_ALL_APPS = 100;

    private Launcher mLauncher;
    private AccessibilityManager mAccessibilityManager;
    private RemoteAction mAllAppsAction;
    private boolean mRegistered;

    public SystemActions(Launcher launcher) {
        mLauncher = launcher;
        mAccessibilityManager = (AccessibilityManager) launcher.getSystemService(
                Context.ACCESSIBILITY_SERVICE);
        mAllAppsAction = new RemoteAction(
                Icon.createWithResource(launcher, R.drawable.ic_apps),
                launcher.getString(R.string.all_apps_label),
                launcher.getString(R.string.all_apps_label),
                launcher.createPendingResult(SYSTEM_ACTION_ID_ALL_APPS, new Intent(),
                        0 /* flags */));
    }

    public void register() {
        if (mRegistered) {
            return;
        }
        mAccessibilityManager.registerSystemAction(mAllAppsAction, SYSTEM_ACTION_ID_ALL_APPS);
        mRegistered = true;
    }

    public void unregister() {
        if (!mRegistered) {
            return;
        }
        mAccessibilityManager.unregisterSystemAction(SYSTEM_ACTION_ID_ALL_APPS);
        mRegistered = false;
    }

    public void onActivityResult(int requestCode) {
        if (requestCode == SYSTEM_ACTION_ID_ALL_APPS) {
            showAllApps();
        }
    }

    private void showAllApps() {
        LauncherStateManager stateManager = mLauncher.getStateManager();
        stateManager.goToState(NORMAL);
        stateManager.goToState(ALL_APPS);
    }
}
