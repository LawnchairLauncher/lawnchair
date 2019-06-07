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

package com.android.quickstep;

import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_CHANGED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;

import static com.android.launcher3.util.PackageManagerHelper.getPackageFilter;
import static com.android.systemui.shared.system.PackageManagerWrapper.ACTION_PREFERRED_ACTIVITY_CHANGED;
import static com.android.systemui.shared.system.QuickStepContract.SYSUI_STATE_HOME_DISABLED;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ResolveInfo;

import com.android.systemui.shared.system.PackageManagerWrapper;

import com.android.systemui.shared.system.QuickStepContract;
import java.util.ArrayList;

/**
 * Class to keep track of the current overview component based off user preferences and app updates
 * and provide callers the relevant classes.
 */
public final class OverviewComponentObserver {
    private final BroadcastReceiver mUserPreferenceChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateOverviewTargets();
        }
    };
    private final BroadcastReceiver mOtherHomeAppUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateOverviewTargets();
        }
    };
    private final Context mContext;
    private final ComponentName mMyHomeComponent;
    private String mUpdateRegisteredPackage;
    private ActivityControlHelper mActivityControlHelper;
    private Intent mOverviewIntent;
    private int mSystemUiStateFlags;

    public OverviewComponentObserver(Context context) {
        mContext = context;

        Intent myHomeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setPackage(mContext.getPackageName());
        ResolveInfo info = context.getPackageManager().resolveActivity(myHomeIntent, 0);
        mMyHomeComponent = new ComponentName(context.getPackageName(), info.activityInfo.name);

        mContext.registerReceiver(mUserPreferenceChangeReceiver,
                new IntentFilter(ACTION_PREFERRED_ACTIVITY_CHANGED));
        updateOverviewTargets();
    }

    public void onSystemUiStateChanged(int stateFlags) {
        boolean homeDisabledChanged = (mSystemUiStateFlags & SYSUI_STATE_HOME_DISABLED)
                != (stateFlags & SYSUI_STATE_HOME_DISABLED);
        mSystemUiStateFlags = stateFlags;
        if (homeDisabledChanged) {
            updateOverviewTargets();
        }
    }

    /**
     * Update overview intent and {@link ActivityControlHelper} based off the current launcher home
     * component.
     */
    private void updateOverviewTargets() {
        ComponentName defaultHome = PackageManagerWrapper.getInstance()
                .getHomeActivities(new ArrayList<>());

        final String overviewIntentCategory;
        ComponentName overviewComponent;
        if ((mSystemUiStateFlags & SYSUI_STATE_HOME_DISABLED) == 0 &&
                (defaultHome == null || mMyHomeComponent.equals(defaultHome))) {
            // User default home is same as out home app. Use Overview integrated in Launcher.
            overviewComponent = mMyHomeComponent;
            mActivityControlHelper = new LauncherActivityControllerHelper();
            overviewIntentCategory = Intent.CATEGORY_HOME;

            if (mUpdateRegisteredPackage != null) {
                // Remove any update listener as we don't care about other packages.
                mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
                mUpdateRegisteredPackage = null;
            }
        } else {
            // The default home app is a different launcher. Use the fallback Overview instead.
            overviewComponent = new ComponentName(mContext, RecentsActivity.class);
            mActivityControlHelper = new FallbackActivityControllerHelper();
            overviewIntentCategory = Intent.CATEGORY_DEFAULT;

            // User's default home app can change as a result of package updates of this app (such
            // as uninstalling the app or removing the "Launcher" feature in an update).
            // Listen for package updates of this app (and remove any previously attached
            // package listener).
            if (!defaultHome.getPackageName().equals(mUpdateRegisteredPackage)) {
                if (mUpdateRegisteredPackage != null) {
                    mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
                }

                mUpdateRegisteredPackage = defaultHome.getPackageName();
                mContext.registerReceiver(mOtherHomeAppUpdateReceiver, getPackageFilter(
                        mUpdateRegisteredPackage, ACTION_PACKAGE_ADDED, ACTION_PACKAGE_CHANGED,
                        ACTION_PACKAGE_REMOVED));
            }
        }

        mOverviewIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(overviewIntentCategory)
                .setComponent(overviewComponent)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    /**
     * Clean up any registered receivers.
     */
    public void onDestroy() {
        mContext.unregisterReceiver(mUserPreferenceChangeReceiver);

        if (mUpdateRegisteredPackage != null) {
            mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
            mUpdateRegisteredPackage = null;
        }
    }

    /**
     * Get the current intent for going to the overview activity.
     *
     * @return the overview intent
     */
    public Intent getOverviewIntent() {
        return mOverviewIntent;
    }

    /**
     * Get the current activity control helper for managing interactions to the overview activity.
     *
     * @return the current activity control helper
     */
    public ActivityControlHelper getActivityControlHelper() {
        return mActivityControlHelper;
    }
}
