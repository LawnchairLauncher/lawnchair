/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.launcher3.compat;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.UserHandle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LauncherAppsCompatV16 extends LauncherAppsCompat {

    private PackageManager mPm;
    private Context mContext;
    private List<OnAppsChangedListenerCompat> mListeners
            = new ArrayList<OnAppsChangedListenerCompat>();
    private PackageMonitor mPackageMonitor;

    LauncherAppsCompatV16(Context context) {
        mPm = context.getPackageManager();
        mContext = context;
        mPackageMonitor = new PackageMonitor();
   }

    public List<LauncherActivityInfoCompat> getActivityList(String packageName,
            UserHandleCompat user) {
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(packageName);
        List<ResolveInfo> infos = mPm.queryIntentActivities(mainIntent, 0);
        List<LauncherActivityInfoCompat> list =
                new ArrayList<LauncherActivityInfoCompat>(infos.size());
        for (ResolveInfo info : infos) {
            list.add(new LauncherActivityInfoCompatV16(mContext, info));
        }
        return list;
    }

    public LauncherActivityInfoCompat resolveActivity(Intent intent, UserHandleCompat user) {
        ResolveInfo info = mPm.resolveActivity(intent, 0);
        if (info != null) {
            return new LauncherActivityInfoCompatV16(mContext, info);
        }
        return null;
    }

    public void startActivityForProfile(ComponentName component, Rect sourceBounds,
            Bundle opts, UserHandleCompat user) {
        Intent launchIntent = new Intent(Intent.ACTION_MAIN);
        launchIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        launchIntent.setComponent(component);
        launchIntent.setSourceBounds(sourceBounds);
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(launchIntent, opts);
    }

    public synchronized void addOnAppsChangedListener(OnAppsChangedListenerCompat listener) {
        if (listener != null && !mListeners.contains(listener)) {
            mListeners.add(listener);
            if (mListeners.size() == 1) {
                registerForPackageIntents();
            }
        }
    }

    public synchronized void removeOnAppsChangedListener(OnAppsChangedListenerCompat listener) {
        mListeners.remove(listener);
        if (mListeners.size() == 0) {
            unregisterForPackageIntents();
        }
    }

    public boolean isPackageEnabledForProfile(String packageName, UserHandleCompat user) {
        try {
            PackageInfo info = mPm.getPackageInfo(packageName, 0);
            return info != null && info.applicationInfo.enabled;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    public boolean isActivityEnabledForProfile(ComponentName component, UserHandleCompat user) {
        try {
            ActivityInfo info = mPm.getActivityInfo(component, 0);
            return info != null && info.isEnabled();
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    private void unregisterForPackageIntents() {
        mContext.unregisterReceiver(mPackageMonitor);
    }

    private void registerForPackageIntents() {
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        mContext.registerReceiver(mPackageMonitor, filter);
        filter = new IntentFilter();
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE);
        filter.addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE);
        mContext.registerReceiver(mPackageMonitor, filter);
    }

    private synchronized List<OnAppsChangedListenerCompat> getListeners() {
        return new ArrayList<OnAppsChangedListenerCompat>(mListeners);
    }

    private class PackageMonitor extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            final UserHandleCompat user = UserHandleCompat.myUserHandle();

            if (Intent.ACTION_PACKAGE_CHANGED.equals(action)
                    || Intent.ACTION_PACKAGE_REMOVED.equals(action)
                    || Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                final String packageName = intent.getData().getSchemeSpecificPart();
                final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);

                if (packageName == null || packageName.length() == 0) {
                    // they sent us a bad intent
                    return;
                }
                if (Intent.ACTION_PACKAGE_CHANGED.equals(action)) {
                    for (OnAppsChangedListenerCompat listener : getListeners()) {
                        listener.onPackageChanged(user, packageName);
                    }
                } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)) {
                    if (!replacing) {
                        for (OnAppsChangedListenerCompat listener : getListeners()) {
                            listener.onPackageRemoved(user, packageName);
                        }
                    }
                    // else, we are replacing the package, so a PACKAGE_ADDED will be sent
                    // later, we will update the package at this time
                } else if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
                    if (!replacing) {
                        for (OnAppsChangedListenerCompat listener : getListeners()) {
                            listener.onPackageAdded(user, packageName);
                        }
                    } else {
                        for (OnAppsChangedListenerCompat listener : getListeners()) {
                            listener.onPackageChanged(user, packageName);
                        }
                    }
                }
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE.equals(action)) {
                final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                for (OnAppsChangedListenerCompat listener : getListeners()) {
                    listener.onPackagesAvailable(user, packages, replacing);
                }
            } else if (Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE.equals(action)) {
                final boolean replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false);
                String[] packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST);
                for (OnAppsChangedListenerCompat listener : getListeners()) {
                    listener.onPackagesUnavailable(user, packages, replacing);
                }
            }
        }
    }
}
