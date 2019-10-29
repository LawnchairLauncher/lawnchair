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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.util.SparseIntArray;

import com.android.systemui.shared.system.PackageManagerWrapper;

import java.util.ArrayList;
import java.util.Objects;

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
    private final Intent mCurrentHomeIntent;
    private final Intent mMyHomeIntent;
    private final Intent mFallbackIntent;
    private final SparseIntArray mConfigChangesMap = new SparseIntArray();
    private String mUpdateRegisteredPackage;
    private ActivityControlHelper mActivityControlHelper;
    private Intent mOverviewIntent;
    private int mSystemUiStateFlags;
    private boolean mIsHomeAndOverviewSame;
    private boolean mIsDefaultHome;

    public OverviewComponentObserver(Context context) {
        mContext = context;

        mCurrentHomeIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mMyHomeIntent = new Intent(mCurrentHomeIntent).setPackage(mContext.getPackageName());
        ResolveInfo info = context.getPackageManager().resolveActivity(mMyHomeIntent, 0);
        ComponentName myHomeComponent =
                new ComponentName(context.getPackageName(), info.activityInfo.name);
        mMyHomeIntent.setComponent(myHomeComponent);
        mConfigChangesMap.append(myHomeComponent.hashCode(), info.activityInfo.configChanges);

        ComponentName fallbackComponent = new ComponentName(mContext, RecentsActivity.class);
        mFallbackIntent = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_DEFAULT)
                .setComponent(fallbackComponent)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            ActivityInfo fallbackInfo = context.getPackageManager().getActivityInfo(
                    mFallbackIntent.getComponent(), 0 /* flags */);
            mConfigChangesMap.append(fallbackComponent.hashCode(), fallbackInfo.configChanges);
        } catch (PackageManager.NameNotFoundException ignored) { /* Impossible */ }

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

        mIsDefaultHome = Objects.equals(mMyHomeIntent.getComponent(), defaultHome);

        // Set assistant visibility to 0 from launcher's perspective, ensures any elements that
        // launcher made invisible become visible again before the new activity control helper
        // becomes active.
        if (mActivityControlHelper != null) {
            mActivityControlHelper.onAssistantVisibilityChanged(0.f);
        }

        if ((mSystemUiStateFlags & SYSUI_STATE_HOME_DISABLED) == 0
                && (defaultHome == null || mIsDefaultHome)) {
            // User default home is same as out home app. Use Overview integrated in Launcher.
            mActivityControlHelper = new LauncherActivityControllerHelper();
            mIsHomeAndOverviewSame = true;
            mOverviewIntent = mMyHomeIntent;
            mCurrentHomeIntent.setComponent(mMyHomeIntent.getComponent());

            if (mUpdateRegisteredPackage != null) {
                // Remove any update listener as we don't care about other packages.
                mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
                mUpdateRegisteredPackage = null;
            }
        } else {
            // The default home app is a different launcher. Use the fallback Overview instead.

            mActivityControlHelper = new FallbackActivityControllerHelper();
            mIsHomeAndOverviewSame = false;
            mOverviewIntent = mFallbackIntent;
            mCurrentHomeIntent.setComponent(defaultHome);

            // User's default home app can change as a result of package updates of this app (such
            // as uninstalling the app or removing the "Launcher" feature in an update).
            // Listen for package updates of this app (and remove any previously attached
            // package listener).
            if (defaultHome == null) {
                if (mUpdateRegisteredPackage != null) {
                    mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
                }
            } else if (!defaultHome.getPackageName().equals(mUpdateRegisteredPackage)) {
                if (mUpdateRegisteredPackage != null) {
                    mContext.unregisterReceiver(mOtherHomeAppUpdateReceiver);
                }

                mUpdateRegisteredPackage = defaultHome.getPackageName();
                mContext.registerReceiver(mOtherHomeAppUpdateReceiver, getPackageFilter(
                        mUpdateRegisteredPackage, ACTION_PACKAGE_ADDED, ACTION_PACKAGE_CHANGED,
                        ACTION_PACKAGE_REMOVED));
            }
        }
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
     * @return {@code true} if the overview component is able to handle the configuration changes.
     */
    boolean canHandleConfigChanges(ComponentName component, int changes) {
        final int orientationChange =
                ActivityInfo.CONFIG_ORIENTATION | ActivityInfo.CONFIG_SCREEN_SIZE;
        if ((changes & orientationChange) == orientationChange) {
            // This is just an approximate guess for simple orientation change because the changes
            // may contain non-public bits (e.g. window configuration).
            return true;
        }

        int configMask = mConfigChangesMap.get(component.hashCode());
        return configMask != 0 && (~configMask & changes) == 0;
    }

    /**
     * Get the intent for overview activity. It is used when lockscreen is shown and home was died
     * in background, we still want to restart the one that will be used after unlock.
     *
     * @return the overview intent
     */
    Intent getOverviewIntentIgnoreSysUiState() {
        return mIsDefaultHome ? mMyHomeIntent : mOverviewIntent;
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
     * Get the current intent for going to the home activity.
     */
    public Intent getHomeIntent() {
        return mCurrentHomeIntent;
    }

    /**
     * Returns true if home and overview are same activity.
     */
    public boolean isHomeAndOverviewSame() {
        return mIsHomeAndOverviewSame;
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
