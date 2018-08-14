/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.view.View;
import android.view.ViewGroup;

import com.android.launcher3.AppInfo;
import com.android.launcher3.BubbleTextView;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.PromiseAppInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

/**
 * A utility class to maintain the collection of all apps.
 */
public class AllAppsStore {

    private PackageUserKey mTempKey = new PackageUserKey(null, null);
    private final HashMap<ComponentKey, AppInfo> mComponentToAppMap = new HashMap<>();
    private final List<OnUpdateListener> mUpdateListeners = new ArrayList<>();
    private final ArrayList<ViewGroup> mIconContainers = new ArrayList<>();

    private boolean mDeferUpdates = false;
    private boolean mUpdatePending = false;

    public Collection<AppInfo> getApps() {
        return mComponentToAppMap.values();
    }

    /**
     * Sets the current set of apps.
     */
    public void setApps(List<AppInfo> apps) {
        mComponentToAppMap.clear();
        addOrUpdateApps(apps);
    }

    public AppInfo getApp(ComponentKey key) {
        return mComponentToAppMap.get(key);
    }

    public void setDeferUpdates(boolean deferUpdates) {
        if (mDeferUpdates != deferUpdates) {
            mDeferUpdates = deferUpdates;

            if (!mDeferUpdates && mUpdatePending) {
                notifyUpdate();
                mUpdatePending = false;
            }
        }
    }

    /**
     * Adds or updates existing apps in the list
     */
    public void addOrUpdateApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.put(app.toComponentKey(), app);
        }
        notifyUpdate();
    }

    /**
     * Removes some apps from the list.
     */
    public void removeApps(List<AppInfo> apps) {
        for (AppInfo app : apps) {
            mComponentToAppMap.remove(app.toComponentKey());
        }
        notifyUpdate();
    }


    private void notifyUpdate() {
        if (mDeferUpdates) {
            mUpdatePending = true;
            return;
        }
        int count = mUpdateListeners.size();
        for (int i = 0; i < count; i++) {
            mUpdateListeners.get(i).onAppsUpdated();
        }
    }

    public void addUpdateListener(OnUpdateListener listener) {
        mUpdateListeners.add(listener);
    }

    public void removeUpdateListener(OnUpdateListener listener) {
        mUpdateListeners.remove(listener);
    }

    public void registerIconContainer(ViewGroup container) {
        if (container != null) {
            mIconContainers.add(container);
        }
    }

    public void unregisterIconContainer(ViewGroup container) {
        mIconContainers.remove(container);
    }

    public void updateIconBadges(Set<PackageUserKey> updatedBadges) {
        updateAllIcons((child) -> {
            if (child.getTag() instanceof ItemInfo) {
                ItemInfo info = (ItemInfo) child.getTag();
                if (mTempKey.updateFromItemInfo(info) && updatedBadges.contains(mTempKey)) {
                    child.applyBadgeState(info, true /* animate */);
                }
            }
        });
    }

    public void updatePromiseAppProgress(PromiseAppInfo app) {
        updateAllIcons((child) -> {
            if (child.getTag() == app) {
                child.applyProgressLevel(app.level);
            }
        });
    }

    private void updateAllIcons(IconAction action) {
        for (int i = mIconContainers.size() - 1; i >= 0; i--) {
            ViewGroup parent = mIconContainers.get(i);
            int childCount = parent.getChildCount();

            for (int j = 0; j < childCount; j++) {
                View child = parent.getChildAt(j);
                if (child instanceof BubbleTextView) {
                    action.apply((BubbleTextView) child);
                }
            }
        }
    }

    public interface OnUpdateListener {
        void onAppsUpdated();
    }

    public interface IconAction {
        void apply(BubbleTextView icon);
    }
}
