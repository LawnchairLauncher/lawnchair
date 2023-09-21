/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.util.SparseArray;

import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;
import com.android.quickstep.RecentsModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Provides recent apps functionality specifically in a desktop environment.
 */
public class DesktopTaskbarRecentAppsController extends TaskbarRecentAppsController {

    private final TaskbarActivityContext mContext;
    private ArrayList<ItemInfo> mRunningApps = new ArrayList<>();
    private AppInfo[] mApps;

    public DesktopTaskbarRecentAppsController(TaskbarActivityContext context) {
        mContext = context;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mApps = null;
    }

    @Override
    protected void setApps(AppInfo[] apps) {
        mApps = apps;
    }

    @Override
    protected boolean isEnabled() {
        return true;
    }

    /**
     * Set mRunningApps to hold currently running applications using the list of currently running
     * tasks. Filtering is also done to ignore applications that are already on the taskbar in the
     * original hotseat.
     */
    @Override
    protected void updateRunningApps(SparseArray<ItemInfo> hotseatItems) {
        ArrayList<AppInfo> runningApps = getRunningAppsFromTasks();
        ArrayList<ItemInfo> filteredRunningApps = new ArrayList<>();
        for (AppInfo runningApp : runningApps) {
            boolean shouldAddOnTaskbar = true;
            for (int i = 0; i < hotseatItems.size(); i++) {
                if (hotseatItems.keyAt(i) >= mControllers.taskbarActivityContext.getDeviceProfile()
                        .numShownHotseatIcons) {
                    break;
                }
                if (hotseatItems.valueAt(i).getTargetPackage()
                        .equals(runningApp.getTargetPackage())) {
                    shouldAddOnTaskbar = false;
                    break;
                }
            }
            if (shouldAddOnTaskbar) {
                filteredRunningApps.add(new WorkspaceItemInfo(runningApp));
            }
        }
        mRunningApps = filteredRunningApps;
        mControllers.taskbarViewController.commitRunningAppsToUI();
    }

    /**
     * Returns a copy of hotseatItems with the addition of currently running applications.
     */
    @Override
    protected ItemInfo[] updateHotseatItemInfos(ItemInfo[] hotseatItemInfos) {
        // hotseatItemInfos.length would be 0 if deviceProfile.numShownHotseatIcons is 0, so we
        // don't want to show anything in the hotseat
        if (hotseatItemInfos.length == 0) return hotseatItemInfos;

        int runningAppsIndex = 0;
        ItemInfo[] newHotseatItemsInfo = Arrays.copyOf(
                hotseatItemInfos, hotseatItemInfos.length + mRunningApps.size());
        for (int i = hotseatItemInfos.length; i < newHotseatItemsInfo.length; i++) {
            newHotseatItemsInfo[i] = mRunningApps.get(runningAppsIndex);
            runningAppsIndex++;
        }
        return newHotseatItemsInfo;
    }


    /**
     * Returns a list of running applications from the list of currently running tasks.
     */
    private ArrayList<AppInfo> getRunningAppsFromTasks() {
        ArrayList<ActivityManager.RunningTaskInfo> tasks =
                RecentsModel.INSTANCE.get(mContext).getRunningTasks();
        ArrayList<AppInfo> runningApps = new ArrayList<>();
        // early return if apps is empty, since we would have no AppInfo to compare
        if (mApps == null)  {
            return runningApps;
        }

        Set<String> seenPackages = new HashSet<>();
        for (ActivityManager.RunningTaskInfo taskInfo : tasks) {
            if (taskInfo.realActivity == null) continue;

            // If a different task for the same package has already been handled, skip this one
            String taskPackage = taskInfo.realActivity.getPackageName();
            if (seenPackages.contains(taskPackage)) continue;

            // Otherwise, get the corresponding AppInfo and add it to the list
            seenPackages.add(taskPackage);
            AppInfo app = getAppInfo(taskInfo.realActivity);
            if (app == null) continue;
            runningApps.add(app);
        }
        return runningApps;
    }

    /**
     * Retrieves the corresponding AppInfo for the activity.
     */
    private AppInfo getAppInfo(ComponentName activity) {
        String packageName = activity.getPackageName();
        for (AppInfo app : mApps) {
            if (!packageName.equals(app.getTargetPackage())) {
                continue;
            }
            return app;
        }
        return null;
    }
}
