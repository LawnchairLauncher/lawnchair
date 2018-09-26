/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3.icons;

import static com.android.launcher3.icons.CachingLogic.COMPONENT_WITH_LABEL;
import static com.android.launcher3.icons.CachingLogic.LAUNCHER_ACTIVITY_INFO;

import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.util.Log;

import com.android.launcher3.AppInfo;
import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.ItemInfoWithIcon;
import com.android.launcher3.LauncherModel;
import com.android.launcher3.ShortcutInfo;
import com.android.launcher3.Utilities;
import com.android.launcher3.model.PackageItemInfo;
import com.android.launcher3.util.Preconditions;
import com.android.launcher3.util.Provider;

import androidx.annotation.NonNull;

/**
 * Cache of application icons.  Icons can be made from any thread.
 */
public class IconCache extends BaseIconCache {

    private static final String TAG = "Launcher.IconCache";

    private int mPendingIconRequestCount = 0;

    public IconCache(Context context, InvariantDeviceProfile inv) {
        super(context, inv.fillResIconDpi, inv.iconBitmapSize);
    }

    /**
     * Updates the entries related to the given package in memory and persistent DB.
     */
    public synchronized void updateIconsForPkg(String packageName, UserHandle user) {
        removeIconsForPkg(packageName, user);
        try {
            PackageInfo info = mPackageManager.getPackageInfo(packageName,
                    PackageManager.GET_UNINSTALLED_PACKAGES);
            long userSerial = mUserManager.getSerialNumberForUser(user);
            for (LauncherActivityInfo app : mLauncherApps.getActivityList(packageName, user)) {
                addIconToDBAndMemCache(app, LAUNCHER_ACTIVITY_INFO, info, userSerial,
                        false /*replace existing*/);
            }
        } catch (NameNotFoundException e) {
            Log.d(TAG, "Package not found", e);
        }
    }

    /**
     * Fetches high-res icon for the provided ItemInfo and updates the caller when done.
     * @return a request ID that can be used to cancel the request.
     */
    public IconLoadRequest updateIconInBackground(final ItemInfoUpdateReceiver caller,
            final ItemInfoWithIcon info) {
        Preconditions.assertUIThread();
        if (mPendingIconRequestCount <= 0) {
            LauncherModel.setWorkerPriority(Process.THREAD_PRIORITY_FOREGROUND);
        }
        mPendingIconRequestCount ++;

        IconLoadRequest request = new IconLoadRequest(mWorkerHandler, this::onIconRequestEnd) {
            @Override
            public void run() {
                if (info instanceof AppInfo || info instanceof ShortcutInfo) {
                    getTitleAndIcon(info, false);
                } else if (info instanceof PackageItemInfo) {
                    getTitleAndIconForApp((PackageItemInfo) info, false);
                }
                mMainThreadExecutor.execute(() -> {
                    caller.reapplyItemInfo(info);
                    onEnd();
                });
            }
        };
        Utilities.postAsyncCallback(mWorkerHandler, request);
        return request;
    }

    private void onIconRequestEnd() {
        mPendingIconRequestCount --;
        if (mPendingIconRequestCount <= 0) {
            LauncherModel.setWorkerPriority(Process.THREAD_PRIORITY_BACKGROUND);
        }
    }

    /**
     * Updates {@param application} only if a valid entry is found.
     */
    public synchronized void updateTitleAndIcon(AppInfo application) {
        CacheEntry entry = cacheLocked(application.componentName,
                application.user, Provider.of(null), LAUNCHER_ACTIVITY_INFO,
                false, application.usingLowResIcon());
        if (entry.icon != null && !isDefaultIcon(entry.icon, application.user)) {
            applyCacheEntry(entry, application);
        }
    }

    /**
     * Fill in {@param info} with the icon and label for {@param activityInfo}
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info,
            LauncherActivityInfo activityInfo, boolean useLowResIcon) {
        // If we already have activity info, no need to use package icon
        getTitleAndIcon(info, Provider.of(activityInfo), false, useLowResIcon);
    }

    /**
     * Fill in {@param info} with the icon and label. If the
     * corresponding activity is not found, it reverts to the package icon.
     */
    public synchronized void getTitleAndIcon(ItemInfoWithIcon info, boolean useLowResIcon) {
        // null info means not installed, but if we have a component from the intent then
        // we should still look in the cache for restored app icons.
        if (info.getTargetComponent() == null) {
            getDefaultIcon(info.user).applyTo(info);
            info.title = "";
            info.contentDescription = "";
        } else {
            Intent intent = info.getIntent();
            getTitleAndIcon(info, () -> mLauncherApps.resolveActivity(intent, info.user),
                    true, useLowResIcon);
        }
    }

    public synchronized String getTitleNoCache(ComponentWithLabel info) {
        CacheEntry entry = cacheLocked(info.getComponent(), info.getUser(), Provider.of(info),
                COMPONENT_WITH_LABEL, false /* usePackageIcon */, true /* useLowResIcon */,
                false /* addToMemCache */);
        return Utilities.trim(entry.title);
    }

    /**
     * Fill in {@param shortcutInfo} with the icon and label for {@param info}
     */
    private synchronized void getTitleAndIcon(
            @NonNull ItemInfoWithIcon infoInOut,
            @NonNull Provider<LauncherActivityInfo> activityInfoProvider,
            boolean usePkgIcon, boolean useLowResIcon) {
        CacheEntry entry = cacheLocked(infoInOut.getTargetComponent(), infoInOut.user,
                activityInfoProvider,
                LAUNCHER_ACTIVITY_INFO, usePkgIcon, useLowResIcon);
        applyCacheEntry(entry, infoInOut);
    }

    public static abstract class IconLoadRequest implements Runnable {
        private final Handler mHandler;
        private final Runnable mEndRunnable;

        private boolean mEnded = false;

        IconLoadRequest(Handler handler, Runnable endRunnable) {
            mHandler = handler;
            mEndRunnable = endRunnable;
        }

        public void cancel() {
            mHandler.removeCallbacks(this);
            onEnd();
        }

        public void onEnd() {
            if (!mEnded) {
                mEnded = true;
                mEndRunnable.run();
            }
        }
    }

    /**
     * Interface for receiving itemInfo with high-res icon.
     */
    public interface ItemInfoUpdateReceiver {

        void reapplyItemInfo(ItemInfoWithIcon info);
    }
}
