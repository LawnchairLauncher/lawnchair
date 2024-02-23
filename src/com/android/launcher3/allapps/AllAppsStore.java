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

import static com.android.launcher3.config.FeatureFlags.ENABLE_ALL_APPS_RV_PREINFLATION;
import static com.android.launcher3.model.data.AppInfo.COMPONENT_KEY_COMPARATOR;
import static com.android.launcher3.model.data.AppInfo.EMPTY_ARRAY;
import static com.android.launcher3.model.data.ItemInfoWithIcon.FLAG_SHOW_DOWNLOAD_PROGRESS_MASK;

import android.content.Context;
import android.os.UserHandle;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.BubbleTextView;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.recyclerview.AllAppsRecyclerViewPool;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.views.ActivityContext;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A utility class to maintain the collection of all apps.
 *
 * @param <T> The type of the context.
 */
public class AllAppsStore<T extends Context & ActivityContext> {

    // Defer updates flag used to defer all apps updates to the next draw.
    public static final int DEFER_UPDATES_NEXT_DRAW = 1 << 0;
    // Defer updates flag used to defer all apps updates by a test's request.
    public static final int DEFER_UPDATES_TEST = 1 << 1;

    private PackageUserKey mTempKey = new PackageUserKey(null, null);
    private AppInfo mTempInfo = new AppInfo();

    private @NonNull AppInfo[] mApps = EMPTY_ARRAY;

    private final List<OnUpdateListener> mUpdateListeners = new CopyOnWriteArrayList<>();
    private final ArrayList<ViewGroup> mIconContainers = new ArrayList<>();
    private Map<PackageUserKey, Integer> mPackageUserKeytoUidMap = Collections.emptyMap();
    private int mModelFlags;
    private int mDeferUpdatesFlags = 0;
    private boolean mUpdatePending = false;
    private final AllAppsRecyclerViewPool mAllAppsRecyclerViewPool = new AllAppsRecyclerViewPool();

    private final T mContext;

    public AppInfo[] getApps() {
        return mApps;
    }

    public AllAppsStore(@NonNull T context) {
        mContext = context;
    }

    /**
     * Calling {@link #setApps(AppInfo[], int, Map, boolean)} with shouldPreinflate set to
     * {@code true}. This method should be called in launcher (not for taskbar).
     */
    public void setApps(@Nullable AppInfo[] apps, int flags, Map<PackageUserKey, Integer> map) {
        setApps(apps, flags, map, /* shouldPreinflate= */ true);
    }

    /**
     * Sets the current set of apps and sets mapping for {@link PackageUserKey} to Uid for
     * the current set of apps.
     *
     * <p> Note that shouldPreinflate param should be set to {@code false} for taskbar, because
     * this method is too late to preinflate all apps, as user will open all apps in the frame
     *
     * <p>Param: apps are required to be sorted using the comparator COMPONENT_KEY_COMPARATOR
     * in order to enable binary search on the mApps store
     */
    public void setApps(@Nullable AppInfo[] apps, int flags, Map<PackageUserKey, Integer> map,
            boolean shouldPreinflate) {
        mApps = apps == null ? EMPTY_ARRAY : apps;
        mModelFlags = flags;
        notifyUpdate();
        mPackageUserKeytoUidMap = map;
        // Preinflate all apps RV when apps has changed, which can happen after unlocking screen,
        // rotating screen, or downloading/upgrading apps.
        if (shouldPreinflate && ENABLE_ALL_APPS_RV_PREINFLATION.get()) {
            mAllAppsRecyclerViewPool.preInflateAllAppsViewHolders(mContext);
        }
    }

    AllAppsRecyclerViewPool getRecyclerViewPool() {
        return mAllAppsRecyclerViewPool;
    }

    /**
     * Look up for Uid using package name and user handle for the current set of apps.
     */
    public int lookUpForUid(String packageName, UserHandle user) {
        return mPackageUserKeytoUidMap.getOrDefault(new PackageUserKey(packageName, user), -1);
    }

    /**
     * @see com.android.launcher3.model.BgDataModel.Callbacks#FLAG_QUIET_MODE_ENABLED
     * @see com.android.launcher3.model.BgDataModel.Callbacks#FLAG_HAS_SHORTCUT_PERMISSION
     * @see com.android.launcher3.model.BgDataModel.Callbacks#FLAG_QUIET_MODE_CHANGE_PERMISSION
     * @see com.android.launcher3.model.BgDataModel.Callbacks#FLAG_WORK_PROFILE_QUIET_MODE_ENABLED
     * @see
     * com.android.launcher3.model.BgDataModel.Callbacks#FLAG_PRIVATE_PROFILE_QUIET_MODE_ENABLED
     */
    public boolean hasModelFlag(int mask) {
        return (mModelFlags & mask) != 0;
    }

    /**
     * Returns {@link AppInfo} if any apps matches with provided {@link ComponentKey}, otherwise
     * null.
     *
     * Uses {@link AppInfo#COMPONENT_KEY_COMPARATOR} as a default comparator.
     */
    @Nullable
    public AppInfo getApp(ComponentKey key) {
        return getApp(key, COMPONENT_KEY_COMPARATOR);
    }

    /**
     * Generic version of {@link #getApp(ComponentKey)} that allows comparator to be specified.
     */
    @Nullable
    public AppInfo getApp(ComponentKey key, Comparator<AppInfo> comparator) {
        mTempInfo.componentName = key.componentName;
        mTempInfo.user = key.user;
        int index = Arrays.binarySearch(mApps, mTempInfo, comparator);
        return index < 0 ? null : mApps[index];
    }

    public void enableDeferUpdates(int flag) {
        mDeferUpdatesFlags |= flag;
    }

    public void disableDeferUpdates(int flag) {
        mDeferUpdatesFlags &= ~flag;
        if (mDeferUpdatesFlags == 0 && mUpdatePending) {
            notifyUpdate();
            mUpdatePending = false;
        }
    }

    public void disableDeferUpdatesSilently(int flag) {
        mDeferUpdatesFlags &= ~flag;
    }

    public int getDeferUpdatesFlags() {
        return mDeferUpdatesFlags;
    }

    private void notifyUpdate() {
        if (mDeferUpdatesFlags != 0) {
            mUpdatePending = true;
            return;
        }
        for (OnUpdateListener listener : mUpdateListeners) {
            listener.onAppsUpdated();
        }
    }

    public void addUpdateListener(OnUpdateListener listener) {
        mUpdateListeners.add(listener);
    }

    public void removeUpdateListener(OnUpdateListener listener) {
        mUpdateListeners.remove(listener);
    }

    public void registerIconContainer(ViewGroup container) {
        if (container != null && !mIconContainers.contains(container)) {
            mIconContainers.add(container);
        }
    }

    public void unregisterIconContainer(ViewGroup container) {
        mIconContainers.remove(container);
    }

    public void updateNotificationDots(Predicate<PackageUserKey> updatedDots) {
        updateAllIcons((child) -> {
            if (child.getTag() instanceof ItemInfo) {
                ItemInfo info = (ItemInfo) child.getTag();
                if (mTempKey.updateFromItemInfo(info) && updatedDots.test(mTempKey)) {
                    child.applyDotState(info, true /* animate */);
                }
            }
        });
    }

    /**
     * Sets the AppInfo's associated icon's progress bar.
     *
     * If this app is installed and supports incremental downloads, the progress bar will be updated
     * the app's total download progress. Otherwise, the progress bar will be updated to the app's
     * installation progress.
     *
     * If this app is fully downloaded, the app icon will be reapplied.
     */
    public void updateProgressBar(AppInfo app) {
        updateAllIcons((child) -> {
            if (child.getTag() == app) {
                if ((app.runtimeStatusFlags & FLAG_SHOW_DOWNLOAD_PROGRESS_MASK) == 0) {
                    child.applyFromApplicationInfo(app);
                } else {
                    child.applyProgressLevel();
                }
            }
        });
    }

    private void updateAllIcons(Consumer<BubbleTextView> action) {
        for (int i = mIconContainers.size() - 1; i >= 0; i--) {
            ViewGroup parent = mIconContainers.get(i);
            int childCount = parent.getChildCount();

            for (int j = 0; j < childCount; j++) {
                View child = parent.getChildAt(j);
                if (child instanceof BubbleTextView) {
                    action.accept((BubbleTextView) child);
                }
            }
        }
    }

    public interface OnUpdateListener {
        void onAppsUpdated();
    }

    /** Generate a dumpsys for each app package name and position in the apps list */
    public void dump(String prefix, PrintWriter writer) {
        writer.println(prefix + "\tAllAppsStore Apps[] size: " + mApps.length);
        for (int i = 0; i < mApps.length; i++) {
            writer.println(String.format("%s\tPackage index and name: %d/%s", prefix, i,
                    mApps[i].componentName.getPackageName()));
        }
    }
}
